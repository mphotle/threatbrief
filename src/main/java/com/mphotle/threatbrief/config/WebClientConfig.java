package com.mphotle.threatbrief.config;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient nvdWebClient(@Value("${nvd.api.key:}") String apiKey) {
        int maxMemorySize = 16 * 1024 * 1024; // 16 MB buffer
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxMemorySize))
                .build();
                
        HttpClient httpClient = HttpClient.create()
                .resolver(DefaultAddressResolverGroup.INSTANCE);
        
        WebClient.Builder builder = WebClient.builder()
                .baseUrl("https://services.nvd.nist.gov/rest/json/cves/2.0")
                .exchangeStrategies(strategies)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("User-Agent", "ThreatBrief-App");

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("apiKey", apiKey);
        }

        return builder.build();
    }

    @Bean
    public WebClient hfWebClient() {
        HttpClient httpClient = HttpClient.create()
                .resolver(DefaultAddressResolverGroup.INSTANCE);
        return WebClient.builder()
                .baseUrl("https://router.huggingface.co")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
