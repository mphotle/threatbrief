package com.mphotle.threatbrief.controller;

import com.mphotle.threatbrief.model.ThreatBrief;
import com.mphotle.threatbrief.service.ThreatBriefService;
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
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ThreatBriefControllerTest {

    private WebTestClient webTestClient;

    @Mock
    private ThreatBriefService threatBriefService;

    @BeforeEach
    void setUp() {
        ThreatBriefController controller = new ThreatBriefController(threatBriefService);
        this.webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void getBrief_when_date_provided_returns_200_and_threat_brief() {
        LocalDate testDate = LocalDate.of(2026, 9, 15);
        ThreatBrief mockResult = new ThreatBrief(testDate, "# Brief Content", 5, LocalDateTime.now());

        when(threatBriefService.getBriefForDate(testDate)).thenReturn(Mono.just(mockResult));

        webTestClient.get()
                .uri("/brief?date=2026-09-15")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.date").isEqualTo("2026-09-15")
                .jsonPath("$.content").isEqualTo("# Brief Content")
                .jsonPath("$.totalCount").isEqualTo(5);

        verify(threatBriefService).getBriefForDate(testDate);
    }

    @Test
    void getBrief_when_date_omitted_uses_current_date() {
        ThreatBrief mockResult = new ThreatBrief(LocalDate.now(), "# Brief Content", 0, LocalDateTime.now());
        when(threatBriefService.getBriefForDate(any(LocalDate.class))).thenReturn(Mono.just(mockResult));

        webTestClient.get()
                .uri("/brief")
                .exchange()
                .expectStatus().isOk();

        verify(threatBriefService).getBriefForDate(any(LocalDate.class));
    }

    @Test
    void getBrief_when_invalid_date_format_returns_400_bad_request() {
        webTestClient.get()
                .uri("/brief?date=15-09-2026")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
