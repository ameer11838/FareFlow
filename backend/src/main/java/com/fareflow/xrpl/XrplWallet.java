package com.fareflow.xrpl;

import jakarta.persistence.*;

import java.time.Instant;

/** A custodial XRPL Testnet account held on one rider's behalf. */
@Entity
@Table(name = "xrpl_wallets")
public class XrplWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "classic_address", nullable = false, updatable = false)
    private String classicAddress;
    @Column(name = "public_key_hex", nullable = false, updatable = false)
    private String publicKeyHex;

    @Column(name = "encrypted_entropy", nullable = false, updatable = false)
    private byte[] encryptedEntropy;
    @Column(name = "entropy_nonce", nullable = false, updatable = false)
    private byte[] entropyNonce;
    @Column(name = "key_algorithm", nullable = false, updatable = false)
    private String keyAlgorithm;
    @Column(nullable = false, updatable = false)
    private String network;

    @Column(name = "funded_at")
    private Instant fundedAt;
    @Column(name = "trustline_set_at")
    private Instant trustlineSetAt;
    @Column(name = "rlusd_issuer_address")
    private String rlusdIssuerAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected XrplWallet() {}

    public static XrplWallet create(Long userId, String classicAddress, String publicKeyHex,
                                    SeedCipher.Sealed sealed, String network, Instant now) {
        XrplWallet wallet = new XrplWallet();
        wallet.userId = userId;
        wallet.classicAddress = classicAddress;
        wallet.publicKeyHex = publicKeyHex;
        wallet.encryptedEntropy = sealed.ciphertext();
        wallet.entropyNonce = sealed.nonce();
        wallet.keyAlgorithm = "ED25519";
        wallet.network = network;
        wallet.createdAt = now;
        wallet.updatedAt = now;
        return wallet;
    }

    public void markFunded(Instant now) {
        fundedAt = now;
        updatedAt = now;
    }

    public void markTrustlineSet(String issuerAddress, Instant now) {
        trustlineSetAt = now;
        rlusdIssuerAddress = issuerAddress;
        updatedAt = now;
    }

    /** Ready to receive and send RLUSD: the account exists and trusts the issuer. */
    public boolean canSettleRlusd(String issuerAddress) {
        return fundedAt != null && trustlineSetAt != null
                && issuerAddress.equals(rlusdIssuerAddress);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getClassicAddress() { return classicAddress; }
    public String getPublicKeyHex() { return publicKeyHex; }
    public byte[] getEncryptedEntropy() { return encryptedEntropy; }
    public byte[] getEntropyNonce() { return entropyNonce; }
    public String getKeyAlgorithm() { return keyAlgorithm; }
    public String getNetwork() { return network; }
    public Instant getFundedAt() { return fundedAt; }
    public Instant getTrustlineSetAt() { return trustlineSetAt; }
    public String getRlusdIssuerAddress() { return rlusdIssuerAddress; }
}
