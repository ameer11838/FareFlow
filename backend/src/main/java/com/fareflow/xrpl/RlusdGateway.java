package com.fareflow.xrpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.crypto.keys.KeyPair;
import org.xrpl.xrpl4j.crypto.keys.PrivateKey;
import org.xrpl.xrpl4j.crypto.signing.SignatureService;
import org.xrpl.xrpl4j.crypto.signing.SingleSignedTransaction;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoRequestParams;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult;
import org.xrpl.xrpl4j.model.client.transactions.SubmitResult;
import org.xrpl.xrpl4j.model.transactions.Address;
import org.xrpl.xrpl4j.model.transactions.IssuedCurrencyAmount;
import org.xrpl.xrpl4j.model.transactions.Memo;
import org.xrpl.xrpl4j.model.transactions.MemoWrapper;
import org.xrpl.xrpl4j.model.transactions.Payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Settles a fare in RLUSD on the XRP Ledger.
 *
 * <p>Shaped like {@code SimulatedCardGateway} on purpose — authorize, get a
 * reference back, hand it to the payment domain — so the ledger rail is a peer of
 * the other rails rather than a special case threaded through the payment service.
 *
 * <p>The difference is that the reference this returns is real. A card token is
 * something only FareFlow can resolve; an XRPL transaction hash can be checked by
 * the rider on a public explorer, without trusting anything FareFlow says about it.
 *
 * <p>Every bean it needs is conditional on configuration, so it is injected lazily
 * and reports the rail unavailable when the integration is switched off.
 */
@Component
public class RlusdGateway {

    private static final Logger log = LoggerFactory.getLogger(RlusdGateway.class);

    private final ObjectProvider<XrplClient> xrplClient;
    private final ObjectProvider<XrplWalletService> walletService;
    private final ObjectProvider<SignatureService<PrivateKey>> signatureService;
    private final XrplProperties properties;

    public RlusdGateway(ObjectProvider<XrplClient> xrplClient,
                        ObjectProvider<XrplWalletService> walletService,
                        ObjectProvider<SignatureService<PrivateKey>> signatureService,
                        XrplProperties properties) {
        this.xrplClient = xrplClient;
        this.walletService = walletService;
        this.signatureService = signatureService;
        this.properties = properties;
    }

    public boolean isAvailable() {
        return properties.isEnabled() && xrplClient.getIfAvailable() != null;
    }

    public String network() {
        return properties.network();
    }

    public String explorerUrl(String hash) {
        return properties.explorerTransactionUrl(hash);
    }

    /**
     * Sends {@code amountCents} of RLUSD from the rider's custodial account to
     * FareFlow's treasury, and returns the on-ledger transaction hash.
     *
     * @param reference the payment intent id, written into the transaction memo so
     *                  an on-ledger payment can always be traced back to the fare
     *                  that caused it — the only handle reconciliation has if the
     *                  database transaction around this call later rolls back
     */
    public Settlement settle(Long userId, long amountCents, String reference) {
        if (!isAvailable()) {
            throw new XrplRailException(
                    "The RLUSD rail is not configured on this deployment.");
        }
        XrplWalletService wallets = walletService.getObject();
        XrplClient client = xrplClient.getObject();
        SignatureService<PrivateKey> signer = signatureService.getObject();

        XrplWallet wallet = wallets.provision(userId);
        if (!wallet.canSettleRlusd(properties.issuerAddress())) {
            throw new XrplRailException(
                    "This rider's XRPL account is not yet authorised to hold RLUSD.");
        }

        try {
            Address from = Address.of(wallet.getClassicAddress());
            KeyPair keyPair = wallets.keyPairFor(wallet);
            AccountInfoResult accountInfo =
                    client.accountInfo(AccountInfoRequestParams.of(from));

            Payment payment = Payment.builder()
                    .account(from)
                    .destination(Address.of(properties.treasuryAddress()))
                    .amount(IssuedCurrencyAmount.builder()
                            .currency(properties.currencyCode())
                            .issuer(Address.of(properties.issuerAddress()))
                            .value(toRlusd(amountCents))
                            .build())
                    .fee(wallets.openLedgerFee())
                    .sequence(accountInfo.accountData().sequence())
                    .lastLedgerSequence(wallets.lastLedgerSequence(accountInfo))
                    .addMemos(memo(reference))
                    .signingPublicKey(keyPair.publicKey())
                    .build();

            SingleSignedTransaction<Payment> signed = signer.sign(keyPair.privateKey(), payment);
            SubmitResult<Payment> result = client.submit(signed);
            XrplWalletService.requireAccepted(result.engineResult(), "payment");

            String hash = signed.hash().value();
            log.info("Settled fare {} as RLUSD on XRPL {} in {}",
                    reference, properties.network(), hash);
            return new Settlement(hash, properties.network(), from.value());
        } catch (XrplRailException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new XrplRailException(
                    "The RLUSD payment could not be submitted to the XRP Ledger.", exception);
        }
    }

    /**
     * Cents to a decimal RLUSD amount.
     *
     * <p>RLUSD is dollar-denominated, so this is a unit change and not a conversion:
     * no rate is fetched and none is implied. Fares are integer cents end to end
     * precisely so this is the only place a decimal ever appears.
     */
    static String toRlusd(long amountCents) {
        return BigDecimal.valueOf(amountCents, 2)
                .setScale(2, RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    /** XRPL memos are hex-encoded bytes, not text. */
    private static MemoWrapper memo(String reference) {
        return MemoWrapper.builder()
                .memo(Memo.builder()
                        .memoData(HexFormat.of().withUpperCase()
                                .formatHex(("fareflow:" + reference)
                                        .getBytes(StandardCharsets.UTF_8)))
                        .build())
                .build();
    }

    /** @param hash independently resolvable on a public explorer */
    public record Settlement(String hash, String network, String fromAddress) {}
}
