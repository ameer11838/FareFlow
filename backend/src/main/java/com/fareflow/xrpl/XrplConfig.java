package com.fareflow.xrpl;

import okhttp3.HttpUrl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.client.faucet.FaucetClient;
import org.xrpl.xrpl4j.crypto.keys.PrivateKey;
import org.xrpl.xrpl4j.crypto.signing.SignatureService;
import org.xrpl.xrpl4j.crypto.signing.bc.BcSignatureService;

/**
 * Wires the XRP Ledger rail.
 *
 * <p>Every bean here is conditional on an issuer being configured. With none, the
 * beans are simply absent and {@link RlusdGateway} reports the rail unavailable —
 * the same shape as the map and the assistant, so an unconfigured integration is
 * a missing capability rather than a runtime failure at the till.
 */
@Configuration
@EnableConfigurationProperties(XrplProperties.class)
public class XrplConfig {

    @Bean
    @ConditionalOnXrplRail
    public XrplClient xrplClient(XrplProperties properties) {
        return new XrplClient(HttpUrl.get(properties.rpcUrl()));
    }

    @Bean
    @ConditionalOnXrplRail
    public FaucetClient xrplFaucetClient(XrplProperties properties) {
        return FaucetClient.construct(HttpUrl.get(properties.faucetUrl()));
    }

    /**
     * Bouncy Castle signing, in process.
     *
     * <p>Deliberately not a remote signer: the point of holding a testnet key at
     * all is that it never leaves this JVM. A production custodian would replace
     * this bean with an HSM-backed {@link SignatureService} and change nothing else.
     */
    @Bean
    @ConditionalOnXrplRail
    public SignatureService<PrivateKey> xrplSignatureService() {
        return new BcSignatureService();
    }

    @Bean
    @ConditionalOnXrplRail
    public SeedCipher seedCipher(XrplProperties properties) {
        return new SeedCipher(properties.custodyKey());
    }

    /**
     * Declared here rather than annotated {@code @Service} so it shares the rail's
     * condition: it depends on beans that only exist when the rail is configured,
     * and a component scan would otherwise fail every unconfigured startup.
     */
    @Bean
    @ConditionalOnXrplRail
    public XrplWalletService xrplWalletService(XrplWalletRepository repository,
                                               XrplClient xrplClient,
                                               FaucetClient faucetClient,
                                               SignatureService<PrivateKey> signatureService,
                                               SeedCipher seedCipher,
                                               XrplProperties properties,
                                               java.time.Clock clock) {
        return new XrplWalletService(repository, xrplClient, faucetClient,
                signatureService, seedCipher, properties, clock);
    }
}
