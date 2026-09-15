package com.mphotle.threatbrief.controller;

import com.mphotle.threatbrief.model.DailyVulnerabilities;
import com.mphotle.threatbrief.service.NvdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ThreatBriefControllerTest {

    private WebTestClient webTestClient;

    @Mock
    private NvdService nvdService;

    @BeforeEach
    void setUp() {
        ThreatBriefController controller = new ThreatBriefController(nvdService);
        this.webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void get_when_date_provided_returns_200_and_vulnerabilities() {
        LocalDate testDate = LocalDate.of(2026, 9, 15);
        DailyVulnerabilities mockResult = new DailyVulnerabilities(testDate, 0, List.of());

        when(nvdService.fetchVulnerabilitiesForDate(testDate)).thenReturn(Mono.just(mockResult));

        webTestClient.get()
                .uri("/vulnerabilities?date=2026-09-15")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.date").isEqualTo("2026-09-15")
                .jsonPath("$.totalCount").isEqualTo(0)
                .jsonPath("$.items").isEmpty();

        verify(nvdService).fetchVulnerabilitiesForDate(testDate);
    }

    @Test
    void get_when_date_omitted_uses_current_date() {
        DailyVulnerabilities mockResult = new DailyVulnerabilities(LocalDate.now(), 0, List.of());
        when(nvdService.fetchVulnerabilitiesForDate(any(LocalDate.class))).thenReturn(Mono.just(mockResult));

        webTestClient.get()
                .uri("/vulnerabilities")
                .exchange()
                .expectStatus().isOk();

        verify(nvdService).fetchVulnerabilitiesForDate(any(LocalDate.class));
    }

    @Test
    void get_when_invalid_date_format_returns_400_bad_request() {
        webTestClient.get()
                .uri("/vulnerabilities?date=15-09-2026")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
