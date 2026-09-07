package com.fareflow.xrpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The parts of the RLUSD rail that hold money or key material. */
class XrplRailTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("a-32-byte-key-for-fareflow-tests".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("cents become a plain two-decimal RLUSD amount, never scientific notation")
    void centsBecomeDecimalAmount() {
        assertThat(RlusdGateway.toRlusd(0)).isEqualTo("0.00");
        assertThat(RlusdGateway.toRlusd(5)).isEqualTo("0.05");
        assertThat(RlusdGateway.toRlusd(135)).isEqualTo("1.35");
        assertThat(RlusdGateway.toRlusd(1_200)).isEqualTo("12.00");
        // A large fare must not serialise as 1.0E+5; the ledger would reject it.
        assertThat(RlusdGateway.toRlusd(10_000_000)).isEqualTo("100000.00");
    }

    @Test
    @DisplayName("sealed entropy round-trips, and each sealing uses a fresh nonce")
    void entropyRoundTrips() {
        SeedCipher cipher = new SeedCipher(KEY);
        byte[] entropy = "sixteen-byte-ent".getBytes(StandardCharsets.UTF_8);

        SeedCipher.Sealed first = cipher.seal(entropy);
        SeedCipher.Sealed second = cipher.seal(entropy);

        assertThat(cipher.open(first.ciphertext(), first.nonce())).isEqualTo(entropy);
        assertThat(cipher.open(second.ciphertext(), second.nonce())).isEqualTo(entropy);
        // Identical plaintext under one key must not produce identical ciphertext.
        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    @DisplayName("a tampered ciphertext fails to open rather than yielding a wrong seed")
    void tamperingIsDetected() {
        SeedCipher cipher = new SeedCipher(KEY);
        SeedCipher.Sealed sealed = cipher.seal("sixteen-byte-ent".getBytes(StandardCharsets.UTF_8));
        byte[] tampered = sealed.ciphertext().clone();
        tampered[0] ^= 0x01;

        assertThatThrownBy(() -> cipher.open(tampered, sealed.nonce()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(KEY);
    }

    @Test
    @DisplayName("a custody key of the wrong size is rejected at construction")
    void shortKeyIsRejected() {
        String tooShort = Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new SeedCipher(tooShort))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("the rail stays disabled unless issuer, treasury, and custody key are all set")
    void railRequiresCompleteConfiguration() {
        assertThat(properties("rIssuer", "rTreasury", KEY).isEnabled()).isTrue();
        assertThat(properties("rIssuer", "rTreasury", "").isEnabled()).isFalse();
        assertThat(properties("rIssuer", "", KEY).isEnabled()).isFalse();
        assertThat(properties("", "rTreasury", KEY).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("mainnet is not a configurable network")
    void mainnetIsRefused() {
        assertThatThrownBy(() -> properties("rIssuer", "rTreasury", KEY, "MAINNET"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TESTNET");
    }

    @Test
    @DisplayName("an explorer link is built from the network the payment was made on")
    void explorerLinkFollowsStoredNetwork() {
        String hash = "A".repeat(64);

        assertThat(XrplProperties.explorerTransactionUrl("TESTNET", hash))
                .isEqualTo("https://testnet.xrpl.org/transactions/" + hash);
        assertThat(XrplProperties.explorerTransactionUrl("DEVNET", hash))
                .isEqualTo("https://devnet.xrpl.org/transactions/" + hash);
        assertThat(XrplProperties.explorerTransactionUrl(null, null)).isNull();
    }

    private static XrplProperties properties(String issuer, String treasury, String key) {
        return properties(issuer, treasury, key, "TESTNET");
    }

    private static XrplProperties properties(String issuer, String treasury,
                                             String key, String network) {
        return new XrplProperties(network, issuer, treasury, key, null);
    }
}
