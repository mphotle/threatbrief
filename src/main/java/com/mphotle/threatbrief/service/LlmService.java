package com.mphotle.threatbrief.service;

import com.mphotle.threatbrief.exception.DownstreamServiceException;
import com.mphotle.threatbrief.service.LlmProvider;
import com.mphotle.threatbrief.model.DailyVulnerabilities;
import com.mphotle.threatbrief.model.VulnerabilityItem;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service responsible for orchestrating prompt construction and invoking the configured {@link LlmProvider}.
 * <p>
 * Filters, ranks, and truncates daily vulnerability data to optimize LLM token context windows before generating 
 * structured Markdown threat briefings.
 * </p>
 */
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

    private static final int MAX_DESCRIPTION_LENGTH = 800;

    private String buildPrompt(DailyVulnerabilities vulnerabilities) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert cybersecurity threat intelligence analyst. ");
        sb.append("Analyze the following vulnerabilities reported on ")
          .append(vulnerabilities.date())
          .append(" (Total published: ")
          .append(vulnerabilities.totalCount())
          .append(") and produce a comprehensive, executive-ready Security Threat Intelligence Briefing formatted in clean Markdown.\n\n");
        sb.append("Provide a thorough, in-depth analysis. Avoid overly brief bullet points or superficial summaries.\n");
        sb.append("Structure the briefing with the following sections:\n");
        sb.append("Do NOT include 'prepared by' and a date at the end of the output\n\n");
        sb.append("# Executive Threat Briefing - ").append(vulnerabilities.date()).append("\n\n");
        sb.append("## 1. Executive Summary\n");
        sb.append("Provide a detailed 2-3 paragraph strategic overview of the threat landscape for this day. Synthesize the overall risk posture, severity distribution, predominant exploit mechanisms, and potential operational impact on organizations.\n\n");
        sb.append("## 2. High & Critical Vulnerability Highlights\n");
        sb.append("For the most critical vulnerabilities, provide an in-depth breakdown covering:\n");
        sb.append("- CVE ID, severity rating, and CVSS score\n");
        sb.append("- Technical root cause and mechanism of failure\n");
        sb.append("- Exploitation vectors, prerequisites, and weaponization likelihood\n");
        sb.append("- Affected software ecosystems, components, and blast radius\n\n");
        sb.append("## 3. Key Risk Trends & Affected Technologies\n");
        sb.append("Deeply analyze emerging threat patterns grouped by technology domain (e.g., Enterprise Software, Cloud & DevOps, Network & IoT Hardware, Identity & Access Controls). Highlight systemic weaknesses observed across today's disclosures.\n\n");
        sb.append("## 4. Recommended Mitigation Priorities\n");
        sb.append("Provide actionable, phased guidance for security and operations teams:\n");
        sb.append("- Immediate Actions (0-48 hours): Emergency patching, temporary workarounds, and exposure containment\n");
        sb.append("- Short-Term Remediation (1-2 weeks): Scheduled updates, configuration hardening, and dependency audits\n");
        sb.append("- Strategic Defenses & Monitoring: Detection rule updates, threat hunting pivots, and architectural safeguards\n\n");
        sb.append("Vulnerability List:\n");
        if (vulnerabilities.items().isEmpty()) {
            sb.append("No vulnerabilities recorded for this date as of yet.\n");
        } else {
            List<VulnerabilityItem> topItems = vulnerabilities.items().stream()
                    .sorted((a, b) -> Double.compare(b.score(), a.score()))
                    .limit(20)
                    .toList();
            for (VulnerabilityItem item : topItems) {
                String desc = item.description();
                if (desc.length() > MAX_DESCRIPTION_LENGTH) {
                    desc = desc.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
                }
                sb.append("- [")
                  .append(item.severity()).append(" | Score: ").append(item.score())
                  .append("] ").append(item.id()).append(": ").append(desc).append("\n");
            }
        }
        return sb.toString();
    }
}
