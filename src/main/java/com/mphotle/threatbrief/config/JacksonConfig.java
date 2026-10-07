package com.mphotle.threatbrief.config;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration class responsible for initializing and customizing the central {@link JsonMapper} bean.
 * <p>
 * Registers the {@link JavaTimeModule} for JSR-310 date/time serialization (e.g., {@link java.time.LocalDate})
 * and disables failure on unknown properties to ensure resilient parsing of evolving external API payloads.
 * </p>
 */

@Configuration 
public class JacksonConfig {
    
    @Bean
    public JsonMapper jsonMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}
