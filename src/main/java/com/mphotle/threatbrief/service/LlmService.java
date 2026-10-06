package com.mphotle.threatbrief.service;

import com.mphotle.threatbrief.exception.DownstreamServiceException;
import com.mphotle.threatbrief.service.LlmProvider;
import com.mphotle.threatbrief.model.DailyVulnerabilities;
import com.mphotle.threatbrief.model.VulnerabilityItem;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class LlmService {

    private final LlmProvider provider;

    public LlmService(LlmProvider provider) {
        this.provider = provider;
    }

    public Mono<String> generateBrief(DailyVulnerabilities vulnerabilities) {
        String prompt = buildPrompt(vulnerabilities);
        return provider.generate(prompt)
                .onErrorMap(this::wrapIfNecessary);
    }

    private Throwable wrapIfNecessary(Throwable t) {
        if (t instanceof DownstreamServiceException) {
            return t;
        }
        return new DownstreamServiceException(
                "Failed to generate threat briefing via LLM provider: " + t.getMessage(), t);
    }

    private String buildPrompt(DailyVulnerabilities vulnerabilities) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert cybersecurity threat intelligence analyst. ");
        sb.append("Analyze the following vulnerabilities reported on ")
          .append(vulnerabilities.date())
          .append(" (Total published: ")
          .append(vulnerabilities.totalCount())
          .append(") and produce a professional, executive-level Security Threat Intelligence Briefing formatted in clean Markdown.\n\n");
        sb.append("Structure the briefing with the following sections:\n");
        sb.append("Do NOT include 'prepared by' and a date at the end of the output");
        sb.append("# Executive Threat Briefing - ").append(vulnerabilities.date()).append("\n");
        sb.append("## 1. Executive Summary\n");
        sb.append("## 2. High & Critical Vulnerability Highlights\n");
        sb.append("## 3. Key Risk Trends & Affected Technologies\n");
        sb.append("## 4. Recommended Mitigation Priorities\n\n");
        sb.append("Vulnerability List:\n");
        if (vulnerabilities.items().isEmpty()) {
            sb.append("No vulnerabilities recorded for this date as of yet.\n");
        } else {
            // All trancations below are to remain within the free tier's limits
            List<VulnerabilityItem> topItems = vulnerabilities.items().stream()
                    .sorted((a, b) -> Double.compare(b.score(), a.score()))
                    .limit(20)
                    .toList();
            for (VulnerabilityItem item : topItems) {
                String desc = item.description();
                if (desc.length() > 200) {
                    desc = desc.substring(0, 197) + "...";
                }
                sb.append("- [")
                  .append(item.severity()).append(" | Score: ").append(item.score())
                  .append("] ").append(item.id()).append(": ").append(desc).append("\n");
            }
        }
        return sb.toString();
    }
}
