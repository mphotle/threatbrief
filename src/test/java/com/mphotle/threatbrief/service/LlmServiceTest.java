package com.mphotle.threatbrief.service;

import com.mphotle.threatbrief.exception.DownstreamServiceException;
import com.mphotle.threatbrief.exception.LlmClientException;
import com.mphotle.threatbrief.model.DailyVulnerabilities;
import com.mphotle.threatbrief.model.VulnerabilityItem;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LlmServiceTest {

    @Mock
    private LlmProvider provider;

    @InjectMocks
    private LlmService llmService;

    private final LocalDate testDate = LocalDate.of(2026, 9, 15);

    @Test
    void generateBrief_when_vulnerabilities_exist_builds_prompt_and_returns_brief() {
        VulnerabilityItem item = new VulnerabilityItem("CVE-2026-0001", "CRITICAL", 9.8, "Critical vulnerability.");
        DailyVulnerabilities vulnerabilities = new DailyVulnerabilities(testDate, 1, List.of(item));
        String generatedMarkdown = "# Executive Threat Briefing - 2026-09-15";

        when(provider.generate(anyString())).thenReturn(Mono.just(generatedMarkdown));

        Mono<String> result = llmService.generateBrief(vulnerabilities);

        StepVerifier.create(result)
                .expectNext(generatedMarkdown)
                .verifyComplete();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(provider).generate(promptCaptor.capture());

        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt).contains("2026-09-15");
        assertThat(capturedPrompt).contains("Total published: 1");
        assertThat(capturedPrompt).contains("CVE-2026-0001");
        assertThat(capturedPrompt).contains("Score: 9.8");
        assertThat(capturedPrompt).contains("Do NOT include 'prepared by' and a date at the end of the output");
    }

    @Test
    void generateBrief_when_no_vulnerabilities_exist_builds_prompt_with_empty_message() {
        DailyVulnerabilities vulnerabilities = new DailyVulnerabilities(testDate, 0, Collections.emptyList());

        when(provider.generate(anyString())).thenReturn(Mono.just("Brief with no vulnerabilities"));

        Mono<String> result = llmService.generateBrief(vulnerabilities);

        StepVerifier.create(result)
                .expectNext("Brief with no vulnerabilities")
                .verifyComplete();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(provider).generate(promptCaptor.capture());

        assertThat(promptCaptor.getValue()).contains("No vulnerabilities recorded for this date as of yet.");
    }

    @Test
    void generateBrief_when_vulnerabilities_exceed_20_limits_prompt_to_top_20_highest_scores() {
        List<VulnerabilityItem> items = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            items.add(new VulnerabilityItem(String.format("CVE-2026-%04d", i), "HIGH", (double) i, "Vulnerability #" + i));
        }
        DailyVulnerabilities vulnerabilities = new DailyVulnerabilities(testDate, 25, items);

        when(provider.generate(anyString())).thenReturn(Mono.just("Brief for top 20"));

        llmService.generateBrief(vulnerabilities).block();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(provider).generate(promptCaptor.capture());

        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("CVE-2026-0025"); // Highest score (25.0) included
        assertThat(prompt).contains("CVE-2026-0006"); // 20th item (6.0) included
        assertThat(prompt).doesNotContain("CVE-2026-0005"); // 21st item (5.0) excluded
    }

    @Test
    void generateBrief_when_description_longer_than_200_chars_truncates_description() {
        String longDescription = "A".repeat(250);
        VulnerabilityItem item = new VulnerabilityItem("CVE-2026-9999", "MEDIUM", 5.0, longDescription);
        DailyVulnerabilities vulnerabilities = new DailyVulnerabilities(testDate, 1, List.of(item));

        when(provider.generate(anyString())).thenReturn(Mono.just("Brief"));

        llmService.generateBrief(vulnerabilities).block();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(provider).generate(promptCaptor.capture());

        String expectedTruncatedDesc = "A".repeat(197) + "...";
        assertThat(promptCaptor.getValue()).contains(expectedTruncatedDesc);
    }

    @Test
    void generateBrief_when_provider_throws_DownstreamServiceException_propagates_unchanged() {
        DailyVulnerabilities vulnerabilities = new DailyVulnerabilities(testDate, 0, Collections.emptyList());
        LlmClientException clientException = new LlmClientException("Invalid API token");

        when(provider.generate(anyString())).thenReturn(Mono.error(clientException));

        Mono<String> result = llmService.generateBrief(vulnerabilities);

        StepVerifier.create(result)
                .expectError(LlmClientException.class)
                .verify();
    }

    @Test
    void generateBrief_when_provider_throws_generic_exception_wraps_in_DownstreamServiceException() {
        DailyVulnerabilities vulnerabilities = new DailyVulnerabilities(testDate, 0, Collections.emptyList());
        RuntimeException runtimeException = new RuntimeException("Unexpected IO error");

        when(provider.generate(anyString())).thenReturn(Mono.error(runtimeException));

        Mono<String> result = llmService.generateBrief(vulnerabilities);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof DownstreamServiceException
                        && throwable.getMessage().contains("Failed to generate threat briefing via LLM provider: Unexpected IO error"))
                .verify();
    }
}
