package com.mphotle.threatbrief.service;

import com.mphotle.threatbrief.exception.DownstreamServiceException;
import com.mphotle.threatbrief.model.DailyVulnerabilities;
import com.mphotle.threatbrief.model.Message;
import com.mphotle.threatbrief.model.VulnerabilityItem;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.List;

/**
 * Service responsible for orchestrating prompt construction and invoking the configured {@link ChatModel}.
 * <p>
 * Filters, ranks, and truncates daily vulnerability data to optimize LLM token context windows before generating 
 * structured Markdown threat briefings. Also provides a conversational agent with tool access.
 * </p>
 */
@Service
public class AgentService {

    private final ChatModel chatModel;
    private final Tools tools;
    private static final int MAX_DESCRIPTION_LENGTH = 800;

    public AgentService(ChatModel chatModel, Tools tools) {
        this.chatModel = chatModel;
        this.tools = tools;
    }

    public Mono<String> generateBrief(DailyVulnerabilities vulnerabilities) {
        if (vulnerabilities.items().isEmpty()) {
            return Mono.just(buildZeroVulnerabilitiesBrief(vulnerabilities.date()));
        }

        String prompt = buildPrompt(vulnerabilities);
        return Mono.fromCallable(() -> chatModel.chat(prompt))
                .subscribeOn(Schedulers.boundedElastic())
                .map(AgentService::sanitizeBrief)
                .onErrorMap(this::wrapIfNecessary);
    }

    public Mono<String> chat(List<Message> messages) {
        return Mono.fromCallable(() -> {
            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

            chatMemory.add(SystemMessage.from(
                "You are a helpful ThreatBrief Assistant. You have access to tools for looking up " +
                "vulnerability data. Describe what you can do in natural, user-friendly terms without" + 
                "mentioning internal function names."
            ));

            if (messages != null) {
                for (int i = 0; i < messages.size() - 1; i++) {
                    Message msg = messages.get(i);
                    if ("user".equalsIgnoreCase(msg.role())) {
                        chatMemory.add(UserMessage.from(msg.content()));
                    } else if ("assistant".equalsIgnoreCase(msg.role())) {
                        chatMemory.add(AiMessage.from(msg.content()));
                    }
                }
            }

            ThreatBriefAgent agent = AiServices.builder(ThreatBriefAgent.class)
                    .chatModel(chatModel)
                    .tools(tools)
                    .chatMemory(chatMemory)
                    .build();

            String userMessage = (messages != null && !messages.isEmpty())
                    ? messages.get(messages.size() - 1).content()
                    : "";
            return agent.chat(userMessage);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public interface ThreatBriefAgent {
        String chat(String userMessage);
    }

    private Throwable wrapIfNecessary(Throwable t) {
        if (t instanceof DownstreamServiceException) {
            return t;
        }
        return new DownstreamServiceException(
                "Failed to generate threat briefing via LLM provider: " + t.getMessage(), t);
    }

    private String buildZeroVulnerabilitiesBrief(LocalDate date) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Executive Threat Briefing - ").append(date).append("\n\n");
        sb.append("There are no vulnerabilities published on the NVD as of yet.\n");
        return sb.toString();
    }

    static String sanitizeBrief(String content) {
        if (content == null) {
            return "";
        }
        // Remove <think>...</think> tags if present
        String cleaned = content.replaceAll("(?s)<think>.*?</think>", "").trim();

        // Discard any thinking scratchpad or pretext output before the main title
        int headerIndex = cleaned.indexOf("# Executive Threat Briefing");
        if (headerIndex > 0) {
            cleaned = cleaned.substring(headerIndex).trim();
        }

        return cleaned;
    }

    private String buildPrompt(DailyVulnerabilities vulnerabilities) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert cybersecurity threat intelligence analyst. ");
        sb.append("Analyze the following vulnerabilities reported on ")
          .append(vulnerabilities.date())
          .append(" (Total published: ")
          .append(vulnerabilities.totalCount())
          .append(") and produce a comprehensive, executive-ready Security Threat Intelligence Briefing formatted in clean Markdown.\n\n");
        sb.append("Provide a thorough, in-depth analysis. Avoid overly brief bullet points or superficial summaries.\n");
        sb.append("Output ONLY the final markdown report. Do NOT include any introductory remarks, chain-of-thought monologue,");
        sb.append("reasoning notes, or conversational filler. Start your response directly with the top-level header '# Executive Threat Briefing - ")
        sb.append("YOU MUST NOT include 'prepared by' and a date at the end of the output\n\n");
        sb.append("YOU MUST NOT use any tables at all in the threat brief.");
        sb.append(vulnerabilities.date()).append("'.\n\n");
        sb.append("Structure the briefing with the following sections:\n");
        sb.append("Generally prefer paragraph form but use point form where needed.")
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
        sb.append("## 4. Recommended Mitigation Steps\n");
        sb.append("Provide actionable, phased guidance for security and operations teams:\n");
        sb.append("Provide advice for both immediate and long term strategic actions.");
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
