package com.fareflow.config;

import com.fareflow.passes.TransitPass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Published pass products.
 *
 * <p>Real prices, in integer cents. Declared as configuration so a tariff change is
 * a data change rather than a code change.
 */
@Configuration
public class PassConfig {

    @Bean
    public List<TransitPass> availablePasses() {
        return List.of(
                new TransitPass("PATH_7DAY", "PATH 7-Day SmartLink pass",
                        "PATH", TransitPass.Period.WEEKLY, 7, 4_600),
                new TransitPass("PATH_30DAY", "PATH 30-Day SmartLink pass",
                        "PATH", TransitPass.Period.MONTHLY, 30, 14_200),
                new TransitPass("MTA_7DAY", "MTA 7-Day Unlimited",
                        "MTA", TransitPass.Period.WEEKLY, 7, 3_400),
                new TransitPass("MTA_30DAY", "MTA 30-Day Unlimited",
                        "MTA", TransitPass.Period.MONTHLY, 30, 13_200));
    }
}
