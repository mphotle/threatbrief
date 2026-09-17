package com.mphotle.threatbrief.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient nvdWebClient() {
        // 16 MB buffer to handle large NVD JSON payloads
        int maxMemorySize = 16 * 1024 * 1024;  
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxMemorySize))
                .build();

        return WebClient.builder()
                .baseUrl("https://services.nvd.nist.gov/rest/json/cves/2.0")
                .exchangeStrategies(strategies)
                .defaultHeader("User-Agent", "ThreatBrief-App")
                .build();
    }
}
