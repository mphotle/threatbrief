package com.mphotle.threatbrief.service;

import com.mphotle.threatbrief.model.DailyVulnerabilities;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class Tools {

    private final NvdService nvdService;

    public Tools(NvdService nvdService) {
        this.nvdService = nvdService;
    }

    @Tool("Get the number of vulnerabilities published on a specific date. Date must be in ISO format (YYYY-MM-DD).")
    public int getVulnerabilityCount(String date) {
        LocalDate localDate = LocalDate.parse(date);
        DailyVulnerabilities result = nvdService.fetchVulnerabilitiesForDate(localDate).block();
        return result != null ? result.totalCount() : 0;
    }

    @Tool("Get the top vulnerabilities for a specific date. Date must be in ISO format (YYYY-MM-DD).")
    public String getTopVulnerabilities(String date, int limit) {
        LocalDate localDate = LocalDate.parse(date);
        DailyVulnerabilities result = nvdService.fetchVulnerabilitiesForDate(localDate).block();
        if (result == null || result.items().isEmpty()) {
            return "No vulnerabilities found for " + date;
        }
        StringBuilder sb = new StringBuilder();
        result.items().stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(limit)
                .forEach(item -> sb.append("- [").append(item.severity())
                        .append(" | Score: ").append(item.score())
                        .append("] ").append(item.id()).append(": ")
                        .append(item.description()).append("\n"));
        return sb.toString();
    }

    @Tool("Get the most recent dates that have threat briefs available, up to the specified limit.")
    public List<String> getRecentDates(int limit) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return java.util.stream.IntStream.range(0, Math.min(limit, 14))
                .mapToObj(i -> today.minusDays(i).toString())
                .toList();
    }
}
