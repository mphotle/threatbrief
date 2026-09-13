package com.mphotle.threatbrief.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient nvdWebClient() {
        return WebClient.builder()
                .baseUrl("https://services.nvd.nist.gov/rest/json/cves/2.0")
                .defaultHeader("User-Agent", "ThreatBrief-App")
                .build();
    }
}
