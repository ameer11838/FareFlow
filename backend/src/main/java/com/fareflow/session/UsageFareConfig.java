package com.fareflow.session;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({UsageFareProperties.class, StopVerificationProperties.class})
public class UsageFareConfig {
}
