package com.mphotle.threatbrief.service;

import com.mphotle.threatbrief.model.DailyVulnerabilities;
import com.mphotle.threatbrief.model.Message;
import com.mphotle.threatbrief.model.VulnerabilityItem;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AgentServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private Tools tools;

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        agentService = new AgentService(chatModel, tools);
    }

    @Test
    void generateBrief_when_vulnerabilities_present_returns_generated_content() {
        DailyVulnerabilities vulns = new DailyVulnerabilities(
                LocalDate.of(2025, 1, 15),
                2,
                List.of(
                        new VulnerabilityItem("CVE-2025-0001", "HIGH", 8.5, "Test description 1"),
                        new VulnerabilityItem("CVE-2025-0002", "MEDIUM", 5.5, "Test description 2")
                )
        );

        when(chatModel.chat(anyString())).thenReturn("Generated brief content");

        Mono<String> result = agentService.generateBrief(vulns);

        StepVerifier.create(result)
                .expectNext("Generated brief content")
                .verifyComplete();
    }

    @Test
    void generateBrief_when_no_vulnerabilities_returns_zero_brief() {
        DailyVulnerabilities vulns = new DailyVulnerabilities(
                LocalDate.of(2025, 1, 15),
                0,
                Collections.emptyList()
        );

        Mono<String> result = agentService.generateBrief(vulns);

        StepVerifier.create(result)
                .expectNextMatches(content -> content.contains("no vulnerabilities"))
                .verifyComplete();
    }

    @Test
    void generateBrief_when_provider_fails_wraps_exception() {
        DailyVulnerabilities vulns = new DailyVulnerabilities(
                LocalDate.of(2025, 1, 15),
                1,
                List.of(new VulnerabilityItem("CVE-2025-0001", "HIGH", 8.5, "Desc"))
        );

        when(chatModel.chat(anyString())).thenThrow(new RuntimeException("Boom"));

        Mono<String> result = agentService.generateBrief(vulns);

        StepVerifier.create(result)
                .expectErrorMatches(t -> t.getMessage().contains("Failed to generate threat briefing"))
                .verify();
    }

    @Test
    void chat_when_valid_messages_returns_response() {
        List<Message> messages = List.of(
                new Message("user", "Hello")
        );

        Mono<String> result = agentService.chat(messages);

        assertThat(result).isNotNull();
    }

    @Test
    void sanitizeBrief_strips_thinking_tags_and_preamble_before_title() {
        String rawOutput = """
                <think>
                Need to analyze vulnerabilities... Let's plan structure.
                </think>
                Random reasoning notes before title.
                # Executive Threat Briefing - 2026-10-19
                
                ## 1. Executive Summary
                Content here.
                """;

        String sanitized = AgentService.sanitizeBrief(rawOutput);

        assertThat(sanitized)
                .startsWith("# Executive Threat Briefing - 2026-10-19")
                .doesNotContain("<think>")
                .doesNotContain("Random reasoning notes");
    }

    @Test
    void sanitizeBrief_handles_clean_input_and_null() {
        assertThat(AgentService.sanitizeBrief(null)).isEmpty();
        assertThat(AgentService.sanitizeBrief("# Executive Threat Briefing - 2026-10-19\nContent"))
                .isEqualTo("# Executive Threat Briefing - 2026-10-19\nContent");
    }
}
