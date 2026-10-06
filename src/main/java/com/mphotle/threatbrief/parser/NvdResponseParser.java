package com.mphotle.threatbrief.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mphotle.threatbrief.exception.NvdParseException;
import com.mphotle.threatbrief.model.DailyVulnerabilities;
import com.mphotle.threatbrief.model.VulnerabilityItem;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class NvdResponseParser {

     private final JsonMapper objectMapper;

    public NvdResponseParser(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DailyVulnerabilities parse(String rawJson, LocalDate date) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            int totalCount = root.path("totalResults").asInt(0);
            JsonNode vulnerabilities = root.path("vulnerabilities");

            List<VulnerabilityItem> items = new ArrayList<>();
            for (JsonNode entry : vulnerabilities) {
                JsonNode cve = entry.path("cve");
                String id = cve.path("id").asText("UNKNOWN");
                String description = extractDescription(cve.path("descriptions"));
                VulnerabilityItem item = extractCvss(cve.path("metrics"), id, description);
                items.add(item);
            }

            return new DailyVulnerabilities(date, totalCount, items);
        } catch (Exception e) {
            throw new NvdParseException("Failed to parse NVD response: " + e.getMessage(), e);
        }
    }

    private String extractDescription(JsonNode descriptionsNode) {
        for (JsonNode descNode : descriptionsNode) {
            if ("en".equals(descNode.path("lang").asText())) {
                return descNode.path("value").asText("No description available.");
            }
        }
        return "No description available.";
    }

    private VulnerabilityItem extractCvss(JsonNode metricsNode, String id, String description) {
        if (metricsNode.isEmpty()) {
            return new VulnerabilityItem(id, "AWAITING_ANALYSIS", 0.0, description);
        }

        if (metricsNode.has("cvssMetricV31")) {
            return buildFromV3(metricsNode.path("cvssMetricV31"), id, description);
        }
        if (metricsNode.has("cvssMetricV40")) {
            return buildFromV3(metricsNode.path("cvssMetricV40"), id, description);
        }
        if (metricsNode.has("cvssMetricV30")) {
            return buildFromV3(metricsNode.path("cvssMetricV30"), id, description);
        }
        if (metricsNode.has("cvssMetricV2")) {
            return buildFromV2(metricsNode.path("cvssMetricV2"), id, description);
        }

        return new VulnerabilityItem(id, "UNKNOWN", 0.0, description);
    }

    private VulnerabilityItem buildFromV3(JsonNode metricArray, String id, String description) {
        JsonNode entry = primaryOrFirst(metricArray);
        JsonNode cvssData = entry.path("cvssData");
        double score = cvssData.path("baseScore").asDouble(0.0);
        String severity = cvssData.path("baseSeverity").asText("UNKNOWN");
        return new VulnerabilityItem(id, severity, score, description);
    }

    private VulnerabilityItem buildFromV2(JsonNode metricArray, String id, String description) {
        JsonNode entry = primaryOrFirst(metricArray);
        double score = entry.path("cvssData").path("baseScore").asDouble(0.0);
        String severity = entry.path("baseSeverity").asText("UNKNOWN");
        return new VulnerabilityItem(id, severity, score, description);
    }

    private JsonNode primaryOrFirst(JsonNode metricArray) {
        for (JsonNode entry : metricArray) {
            if ("Primary".equals(entry.path("type").asText())) {
                return entry;
            }
        }
        return metricArray.path(0);
    }
}