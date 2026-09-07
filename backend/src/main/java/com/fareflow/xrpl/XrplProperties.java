package com.fareflow.xrpl;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * XRP Ledger settings for the RLUSD rail.
 *
 * <p>The rail is off unless an issuer is configured. That is the same posture the
 * map and the assistant take: an unconfigured integration reports itself
 * unavailable rather than failing at the moment a rider tries to pay.
 *
 * @param network       which test ledger to use; mainnet is intentionally not an
 *                      option, because custodial settlement of a real stablecoin
 *                      is a regulated activity and this product is a simulation
 * @param issuerAddress the RLUSD issuer on that network. No default is shipped:
 *                      a guessed issuer would silently produce a trustline to
 *                      nothing, and every payment would fail far from the cause.
 * @param custodyKey      base64 32-byte AES key encrypting seed entropy at rest
 * @param treasuryAddress where fares are sent. FareFlow's own funded testnet
 *                        account, which must already trust the same issuer or the
 *                        payment is rejected by the ledger rather than by us.
 */
@ConfigurationProperties(prefix = "fareflow.xrpl")
public record XrplProperties(
        String network,
        String issuerAddress,
        String treasuryAddress,
        String custodyKey,
        String currencyCode
) {

    /** RLUSD is five characters, so it is carried as a 160-bit hex currency code. */
    public static final String RLUSD_HEX = "524C555344000000000000000000000000000000";

    public XrplProperties {
        network = network == null || network.isBlank()
                ? "TESTNET" : network.trim().toUpperCase(java.util.Locale.ROOT);
        if (!network.equals("TESTNET") && !network.equals("DEVNET")) {
            throw new IllegalArgumentException(
                    "fareflow.xrpl.network must be TESTNET or DEVNET, not " + network);
        }
        currencyCode = currencyCode == null || currencyCode.isBlank()
                ? RLUSD_HEX : currencyCode.trim();
    }

    /** The rail is usable only with both an issuer to trust and a key to encrypt. */
    public boolean isEnabled() {
        return issuerAddress != null && !issuerAddress.isBlank()
                && treasuryAddress != null && !treasuryAddress.isBlank()
                && custodyKey != null && !custodyKey.isBlank();
    }

    public String rpcUrl() {
        return network.equals("DEVNET")
                ? "https://s.devnet.rippletest.net:51234/"
                : "https://s.altnet.rippletest.net:51234/";
    }

    public String faucetUrl() {
        return network.equals("DEVNET")
                ? "https://faucet.devnet.rippletest.net"
                : "https://faucet.altnet.rippletest.net";
    }

    /** Public explorer, so a rider can check the charge without trusting FareFlow. */
    public String explorerTransactionUrl(String hash) {
        return explorerTransactionUrl(network, hash);
    }

    /**
     * Static so a stored payment can be linked from its own recorded network,
     * rather than from whatever the server happens to be configured for now.
     */
    public static String explorerTransactionUrl(String network, String hash) {
        if (network == null || hash == null) return null;
        String subdomain = "DEVNET".equals(network) ? "devnet" : "testnet";
        return "https://%s.xrpl.org/transactions/%s".formatted(subdomain, hash);
    }
}
