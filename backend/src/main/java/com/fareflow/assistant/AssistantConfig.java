package com.fareflow.assistant;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Gemini client, but only when there is a key to build it with.
 *
 * <p>Returning null from a {@code @Bean} method is legal in Spring and registers no
 * bean, so every collaborator injects {@code ObjectProvider<Client>} and
 * asks whether one exists. That is what makes an unkeyed deployment boot cleanly
 * instead of failing at startup on a feature the rider may never open.
 */
@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfig {

    @Bean(destroyMethod = "close")
    public Client geminiClient(AssistantProperties properties) {
        if (!properties.isUsable()) {
            return null;
        }
        return Client.builder()
                .apiKey(properties.apiKey())
                // A rider is watching a typing indicator. Two minutes is generous for
                // a multi-tool answer and still short enough that a hung upstream
                // surfaces as an error rather than an indefinite spinner.
                .httpOptions(HttpOptions.builder().timeout(120_000).build())
                .build();
    }
}
