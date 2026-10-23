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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Service responsible for orchestrating prompt construction and invoking the configured {@link ChatModel}.
 * <p>
 * Filters, ranks, and truncates daily vulnerability data to optimize LLM token context windows before generating
 * structured Markdown threat briefings. Also provides a conversational agent with tool access.
 * </p>
 * <p>
 * Prompt templates are loaded from {@code src/main/resources/prompts/} to keep business logic
 * and natural-language instructions cleanly separated.
 * </p>
 */
@Service
public class AgentService {

    private final ChatModel chatModel;
    private final Tools tools;
    private final String briefingTemplate;
    private final String chatSystemPrompt;
    private static final int MAX_DESCRIPTION_LENGTH = 800;

    public AgentService(ChatModel chatModel, Tools tools) {
        this.chatModel = chatModel;
        this.tools = tools;
        this.briefingTemplate = loadPrompt("prompts/briefing-system.md");
        this.chatSystemPrompt = loadPrompt("prompts/chat-system.md");
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

            chatMemory.add(SystemMessage.from(chatSystemPrompt));

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
        StringBuilder vulnList = new StringBuilder();
        if (vulnerabilities.items().isEmpty()) {
            vulnList.append("No vulnerabilities recorded for this date as of yet.\n");
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
                vulnList.append("- [")
                        .append(item.severity())
                        .append(" | Score: ").append(item.score())
                        .append("] ").append(item.id())
                        .append(": ").append(desc).append("\n");
            }
        }

        return briefingTemplate
                .replace("{{date}}", vulnerabilities.date().toString())
                .replace("{{totalCount}}", String.valueOf(vulnerabilities.totalCount()))
                .replace("{{vulnerabilityList}}", vulnList.toString());
    }

    private static String loadPrompt(String resourcePath) {
        try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load prompt template from " + resourcePath, e);
        }
    }
}
