package com.fareflow.xrpl;

import com.google.common.primitives.UnsignedInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.client.faucet.FaucetClient;
import org.xrpl.xrpl4j.client.faucet.FundAccountRequest;
import org.xrpl.xrpl4j.crypto.keys.Base58EncodedSecret;
import org.xrpl.xrpl4j.crypto.keys.Entropy;
import org.xrpl.xrpl4j.crypto.keys.KeyPair;
import org.xrpl.xrpl4j.crypto.keys.PrivateKey;
import org.xrpl.xrpl4j.crypto.keys.Seed;
import org.xrpl.xrpl4j.crypto.signing.SignatureService;
import org.xrpl.xrpl4j.crypto.signing.SingleSignedTransaction;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoRequestParams;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult;
import org.xrpl.xrpl4j.model.client.transactions.TransactionRequestParams;
import org.xrpl.xrpl4j.model.client.transactions.TransactionResult;
import org.xrpl.xrpl4j.model.client.transactions.SubmitResult;
import org.xrpl.xrpl4j.model.transactions.Address;
import org.xrpl.xrpl4j.model.transactions.IssuedCurrencyAmount;
import org.xrpl.xrpl4j.model.transactions.Payment;
import org.xrpl.xrpl4j.model.transactions.Transaction;
import org.xrpl.xrpl4j.model.transactions.TrustSet;
import org.xrpl.xrpl4j.model.transactions.XrpCurrencyAmount;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Provisions and unlocks the custodial XRPL account behind a rider's RLUSD rail.
 *
 * <p>Accounts are created lazily, on the first attempt to pay this way. A rider who
 * never picks the rail never has a key held on their behalf, which is the only
 * defensible default when the key is a liability rather than a feature.
 *
 * <p>Provisioning is two network steps that must both succeed before the rail can
 * carry a fare: the faucet has to bring the account into existence, and a trustline
 * has to authorise it to hold the issuer's RLUSD. They are recorded separately so a
 * half-provisioned wallet is visible rather than mysterious.
 */
public class XrplWalletService {

    private static final Logger log = LoggerFactory.getLogger(XrplWalletService.class);

    /**
     * The ceiling on RLUSD this account may hold. Not a fare limit — it is the
     * trustline's own required maximum, and it exists so a compromised issuer
     * cannot mint an unbounded balance into a rider's account.
     */
    private static final String TRUST_LIMIT = "100000";

    private final XrplWalletRepository repository;
    private final XrplClient xrplClient;
    private final FaucetClient faucetClient;
    private final SignatureService<PrivateKey> signatureService;
    private final SeedCipher seedCipher;
    private final XrplProperties properties;
    private final Clock clock;

    public XrplWalletService(XrplWalletRepository repository,
                             XrplClient xrplClient,
                             FaucetClient faucetClient,
                             SignatureService<PrivateKey> signatureService,
                             SeedCipher seedCipher,
                             XrplProperties properties,
                             Clock clock) {
        this.repository = repository;
        this.xrplClient = xrplClient;
        this.faucetClient = faucetClient;
        this.signatureService = signatureService;
        this.seedCipher = seedCipher;
        this.properties = properties;
        this.clock = clock;
    }

    public Optional<XrplWallet> find(Long userId) {
        return repository.findByUserId(userId);
    }

    /**
     * Returns a wallet ready to settle RLUSD, creating and provisioning one if
     * this rider has never used the rail.
     *
     * <p>Runs in its own transaction. Provisioning talks to a faucet and a public
     * ledger, and a rider's account surviving is worth more than the fare payment
     * that triggered it: if the payment afterwards rolls back, the account it
     * created should still be there rather than be orphaned on-ledger with no row.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public XrplWallet provision(Long userId) {
        XrplWallet wallet = repository.findByUserId(userId)
                .orElseGet(() -> repository.save(createWallet(userId)));

        if (wallet.getFundedAt() == null) {
            fund(wallet);
        }
        if (wallet.getTrustlineSetAt() == null
                || !properties.issuerAddress().equals(wallet.getRlusdIssuerAddress())) {
            setTrustline(wallet);
            // A trustline only grants permission to hold the asset, not any of it.
            // Without this the rider's very first fare would fail on an empty
            // balance, which reads as a broken rail rather than an empty wallet.
            grantStartingBalance(wallet);
        }
        return repository.save(wallet);
    }

    /**
     * Mints the rider's opening RLUSD balance from FareFlow's own issuer.
     *
     * <p>Only defensible because the issuer is FareFlow's and the ledger is a test
     * network: this is the simulation's equivalent of topping up a demo card. On a
     * real asset the rider would fund their own account and this step would not
     * exist — which is why {@code XRPL_NETWORK} refuses mainnet outright.
     */
    private void grantStartingBalance(XrplWallet wallet) {
        try {
            KeyPair issuer = issuerKeyPair();
            Address issuerAddress = Address.of(properties.issuerAddress());
            AccountInfoResult accountInfo =
                    xrplClient.accountInfo(AccountInfoRequestParams.of(issuerAddress));

            Payment grant = Payment.builder()
                    .account(issuerAddress)
                    .destination(Address.of(wallet.getClassicAddress()))
                    .amount(IssuedCurrencyAmount.builder()
                            .currency(properties.currencyCode())
                            .issuer(issuerAddress)
                            .value(properties.riderGrantRlusd())
                            .build())
                    .fee(openLedgerFee())
                    .sequence(accountInfo.accountData().sequence())
                    .lastLedgerSequence(lastLedgerSequence(accountInfo))
                    .signingPublicKey(issuer.publicKey())
                    .build();

            SingleSignedTransaction<Payment> signed =
                    signatureService.sign(issuer.privateKey(), grant);
            SubmitResult<Payment> result = xrplClient.submit(signed);
            requireAccepted(result.engineResult(), "opening balance");
            awaitValidated(signed, Payment.class, "opening balance");
            log.info("Granted {} RLUSD to {}",
                    properties.riderGrantRlusd(), wallet.getClassicAddress());
        } catch (XrplRailException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new XrplRailException(
                    "Could not fund this rider's RLUSD balance.", exception);
        }
    }

    /** The issuer's signing key, derived from the configured seed. */
    KeyPair issuerKeyPair() {
        return Seed.fromBase58EncodedSecret(
                Base58EncodedSecret.of(properties.issuerSeed().trim())).deriveKeyPair();
    }

    /** Re-derives the signing key for a stored wallet. */
    public KeyPair keyPairFor(XrplWallet wallet) {
        byte[] entropy = seedCipher.open(wallet.getEncryptedEntropy(), wallet.getEntropyNonce());
        return Seed.ed25519SeedFromEntropy(Entropy.of(entropy)).deriveKeyPair();
    }

    private XrplWallet createWallet(Long userId) {
        Entropy entropy = Entropy.newInstance();
        KeyPair keyPair = Seed.ed25519SeedFromEntropy(entropy).deriveKeyPair();
        SeedCipher.Sealed sealed = seedCipher.seal(entropy.value().toByteArray());
        return XrplWallet.create(userId,
                keyPair.publicKey().deriveAddress().value(),
                keyPair.publicKey().base16Value(),
                sealed, properties.network(), clock.instant());
    }

    /**
     * Brings the account into existence with faucet XRP.
     *
     * <p>An XRPL account does not exist until it is funded above the base reserve,
     * and an unfunded address cannot hold a trustline or receive an issued currency.
     * This is why the rail is testnet-only: on mainnet this step costs real money
     * and would make FareFlow the funder of every rider who tried the button.
     */
    private void fund(XrplWallet wallet) {
        try {
            faucetClient.fundAccount(
                    FundAccountRequest.of(Address.of(wallet.getClassicAddress())));
            awaitAccount(wallet);
            wallet.markFunded(clock.instant());
        } catch (Exception exception) {
            throw new XrplRailException(
                    "Could not fund the test account for this rider on the XRPL "
                            + properties.network().toLowerCase(java.util.Locale.ROOT)
                            + ". The faucet may be rate limiting; try again shortly.",
                    exception);
        }
    }

    /**
     * The faucet returns before the account is visible to the ledger it funded,
     * so the next transaction would fail with actNotFound. Poll briefly rather
     * than sleeping a fixed guess.
     */
    private void awaitAccount(XrplWallet wallet) throws Exception {
        Address address = Address.of(wallet.getClassicAddress());
        Exception last = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                xrplClient.accountInfo(AccountInfoRequestParams.of(address));
                return;
            } catch (Exception exception) {
                last = exception;
                Thread.sleep(500);
            }
        }
        throw last == null ? new IllegalStateException("Account never appeared") : last;
    }

    /** Authorises the account to hold the issuer's RLUSD. */
    private void setTrustline(XrplWallet wallet) {
        try {
            KeyPair keyPair = keyPairFor(wallet);
            Address address = Address.of(wallet.getClassicAddress());
            AccountInfoResult accountInfo =
                    xrplClient.accountInfo(AccountInfoRequestParams.of(address));

            TrustSet trustSet = TrustSet.builder()
                    .account(address)
                    .fee(openLedgerFee())
                    .sequence(accountInfo.accountData().sequence())
                    .lastLedgerSequence(lastLedgerSequence(accountInfo))
                    .limitAmount(IssuedCurrencyAmount.builder()
                            .currency(properties.currencyCode())
                            .issuer(Address.of(properties.issuerAddress()))
                            .value(TRUST_LIMIT)
                            .build())
                    .signingPublicKey(keyPair.publicKey())
                    .build();

            SingleSignedTransaction<TrustSet> signed =
                    signatureService.sign(keyPair.privateKey(), trustSet);
            SubmitResult<TrustSet> result = xrplClient.submit(signed);
            requireAccepted(result.engineResult(), "trustline");
            awaitValidated(signed, TrustSet.class, "trustline");
            wallet.markTrustlineSet(properties.issuerAddress(), clock.instant());
        } catch (XrplRailException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new XrplRailException(
                    "Could not authorise this rider's account to hold RLUSD.", exception);
        }
    }

    XrpCurrencyAmount openLedgerFee() throws Exception {
        return xrplClient.fee().drops().openLedgerFee();
    }

    /**
     * Bounds how long a signed transaction stays submittable.
     *
     * <p>Without it, a transaction that fails to appear can be applied much later,
     * long after FareFlow reported the payment failed — charging a rider twice for
     * one trip. Four ledgers is roughly fifteen seconds.
     */
    UnsignedInteger lastLedgerSequence(AccountInfoResult accountInfo) {
        return UnsignedInteger.valueOf(
                accountInfo.ledgerCurrentIndexSafe().unsignedIntegerValue().longValue() + 4);
    }

    static void requireAccepted(String engineResult, String what) {
        // tesSUCCESS is the only unambiguous acceptance. tec* codes are applied to
        // the ledger but failed, and everything else was not applied at all.
        if (!"tesSUCCESS".equals(engineResult)) {
            log.warn("XRPL {} rejected with engine result {}", what, engineResult);
            throw new XrplRailException(
                    "The XRP Ledger rejected this %s (%s).".formatted(what, engineResult));
        }
    }

    /**
     * Waits until consensus includes a submitted transaction in a validated ledger.
     *
     * <p>A successful {@code submit} result only means a server accepted the blob
     * for relay. It does not mean the transaction made a validated ledger. This
     * distinction matters during first-use provisioning: the trustline must be
     * final before the issuer can fund it, and both must be final before the rider
     * can spend the balance. It also keeps FareFlow from issuing an optimistic
     * receipt for a transaction that later expires.
     */
    <T extends Transaction> TransactionResult<T> awaitValidated(
            SingleSignedTransaction<T> signed, Class<T> transactionType, String what)
            throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                TransactionResult<T> result = xrplClient.transaction(
                        TransactionRequestParams.of(signed.hash()), transactionType);
                if (result.validated()) {
                    String transactionResult = result.metadata()
                            .map(metadata -> metadata.transactionResult())
                            .orElseThrow(() -> new XrplRailException(
                                    "The validated XRPL %s has no transaction result."
                                            .formatted(what)));
                    requireAccepted(transactionResult, what);
                    return result;
                }
            } catch (XrplRailException exception) {
                throw exception;
            } catch (Exception exception) {
                // txnNotFound is normal for the first few ledgers after submit.
                last = exception;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
        }
        throw new XrplRailException(
                "The XRP Ledger did not validate this %s before it expired."
                        .formatted(what), last);
    }
}
