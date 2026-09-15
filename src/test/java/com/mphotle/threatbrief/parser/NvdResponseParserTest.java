package com.mphotle.threatbrief.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mphotle.threatbrief.exception.NvdParseException;
import com.mphotle.threatbrief.model.DailyVulnerabilities;
import com.mphotle.threatbrief.model.VulnerabilityItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NvdResponseParserTest {

    private NvdResponseParser parser;
    private final LocalDate testDate = LocalDate.of(2026, 9, 15);

    @BeforeEach
    void setUp() {
        parser = new NvdResponseParser(new ObjectMapper());
    }

    @Test
    void parse_when_json_has_cvss_v31_metrics_returns_daily_vulnerabilities() {
        String rawJson = """
                {
                  "totalResults": 1,
                  "vulnerabilities": [
                    {
                      "cve": {
                        "id": "CVE-2026-1122",
                        "descriptions": [
                          {"lang": "es", "value": "Descripción en español"},
                          {"lang": "en", "value": "Remote code execution in core module."}
                        ],
                        "metrics": {
                          "cvssMetricV31": [
                            {
                              "type": "Primary",
                              "cvssData": {
                                "baseScore": 9.8,
                                "baseSeverity": "CRITICAL"
                              }
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
                """;

        DailyVulnerabilities result = parser.parse(rawJson, testDate);

        assertThat(result).isNotNull();
        assertThat(result.date()).isEqualTo(testDate);
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);

        VulnerabilityItem item = result.items().get(0);
        assertThat(item.id()).isEqualTo("CVE-2026-1122");
        assertThat(item.severity()).isEqualTo("CRITICAL");
        assertThat(item.score()).isEqualTo(9.8);
        assertThat(item.description()).isEqualTo("Remote code execution in core module.");
    }

    @Test
    void parse_when_multiple_metrics_exist_prioritizes_primary_type() {
        String rawJson = """
                {
                  "totalResults": 1,
                  "vulnerabilities": [
                    {
                      "cve": {
                        "id": "CVE-2026-3344",
                        "descriptions": [{"lang": "en", "value": "Metric type test."}],
                        "metrics": {
                          "cvssMetricV31": [
                            {
                              "type": "Secondary",
                              "cvssData": {"baseScore": 5.0, "baseSeverity": "MEDIUM"}
                            },
                            {
                              "type": "Primary",
                              "cvssData": {"baseScore": 8.1, "baseSeverity": "HIGH"}
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
                """;

        DailyVulnerabilities result = parser.parse(rawJson, testDate);

        VulnerabilityItem item = result.items().get(0);
        assertThat(item.score()).isEqualTo(8.1);
        assertThat(item.severity()).isEqualTo("HIGH");
    }

    @Test
    void parse_when_v3_absent_and_v2_present_parses_cvss_v2_metrics() {
        String rawJson = """
                {
                  "totalResults": 1,
                  "vulnerabilities": [
                    {
                      "cve": {
                        "id": "CVE-2010-0001",
                        "descriptions": [{"lang": "en", "value": "Legacy vulnerability."}],
                        "metrics": {
                          "cvssMetricV2": [
                            {
                              "type": "Primary",
                              "baseSeverity": "HIGH",
                              "cvssData": {"baseScore": 7.5}
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
                """;

        DailyVulnerabilities result = parser.parse(rawJson, testDate);

        VulnerabilityItem item = result.items().get(0);
        assertThat(item.id()).isEqualTo("CVE-2010-0001");
        assertThat(item.severity()).isEqualTo("HIGH");
        assertThat(item.score()).isEqualTo(7.5);
    }

    @Test
    void parse_when_metrics_empty_sets_awaiting_analysis() {
        String rawJson = """
                {
                  "totalResults": 1,
                  "vulnerabilities": [
                    {
                      "cve": {
                        "id": "CVE-2026-9999",
                        "descriptions": [{"lang": "en", "value": "Newly published CVE."}],
                        "metrics": {}
                      }
                    }
                  ]
                }
                """;

        DailyVulnerabilities result = parser.parse(rawJson, testDate);

        VulnerabilityItem item = result.items().get(0);
        assertThat(item.severity()).isEqualTo("AWAITING_ANALYSIS");
        assertThat(item.score()).isEqualTo(0.0);
    }

    @Test
    void parse_when_english_description_missing_uses_fallback_description() {
        String rawJson = """
                {
                  "totalResults": 1,
                  "vulnerabilities": [
                    {
                      "cve": {
                        "id": "CVE-2026-7788",
                        "descriptions": [{"lang": "fr", "value": "Description en français"}],
                        "metrics": {}
                      }
                    }
                  ]
                }
                """;

        DailyVulnerabilities result = parser.parse(rawJson, testDate);

        VulnerabilityItem item = result.items().get(0);
        assertThat(item.description()).isEqualTo("No description available.");
    }

    @Test
    void parse_when_json_is_malformed_throws_NvdParseException() {
        String invalidJson = "{ malformed_json: ";

        assertThatThrownBy(() -> parser.parse(invalidJson, testDate))
                .isInstanceOf(NvdParseException.class)
                .hasMessageContaining("Failed to parse NVD response")
                .hasCauseInstanceOf(Exception.class);
    }
}
