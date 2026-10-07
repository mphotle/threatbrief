package com.mphotle.threatbrief.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mphotle.threatbrief.exception.LlmClientException;
import com.mphotle.threatbrief.exception.LlmParseException;
import com.mphotle.threatbrief.exception.LlmRateLimitExceededException;
import com.mphotle.threatbrief.exception.LlmServerException;
import com.mphotle.threatbrief.exception.LlmServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.List; 

/**
 * {@link LlmProvider} implementation that communicates asynchronously with the Hugging Face Router API.
 * <p>
 * Handles OpenAI-compatible chat completion requests, maps HTTP error statuses to domain-specific exceptions,
 * and extracts synthesized Markdown content from API responses.
 * </p>
 */
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "huggingface")
public class HuggingFaceProvider implements LlmProvider {

    private final WebClient client;
    private final JsonMapper jsonMapper;
    private final String token;
    private final String modelId;

    public HuggingFaceProvider(@Qualifier("hfWebClient") WebClient client,
                               JsonMapper jsonMapper,
                               @Value("${huggingface.api.token}") String token,
                               @Value("${huggingface.model.id}") String modelId) {
        this.client = client;
        this.jsonMapper = jsonMapper;
        this.token = token;
        this.modelId = modelId;
    }

    @Override
    public Mono<String> generate(String prompt) {
        return client.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "model", modelId,
                        "messages", List.of(
                                Map.of("role", "user", "content", prompt)
                        )
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(mapToLlmException(response.statusCode(), body)))
                )
                .bodyToMono(String.class)
                .flatMap(this::extractText);
    }

    private Throwable mapToLlmException(HttpStatusCode statusCode, String body) {
        String message = "HuggingFace API error (status " + statusCode + "): " + body;
        if (statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new LlmRateLimitExceededException(message);
        } else if (statusCode.is4xxClientError()) {
            return new LlmClientException(message);
        } else if (statusCode.is5xxServerError()) {
            return new LlmServerException(message);
        }
        return new LlmServiceException(message);
    }

    private Mono<String> extractText(String raw) {
        try {
            JsonNode node = jsonMapper.readTree(raw);
            JsonNode choices = node.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String content = choices.get(0).path("message").path("content").asText();
                if (content != null && !content.isBlank()) {
                    return Mono.just(content);
                }
            }
            if (node.has("error")) {
                return Mono.error(new LlmClientException("HuggingFace API error: " + node.path("error").asText()));
            }
            return Mono.just(node.asText());
        } catch (Exception e) {
            return Mono.error(new LlmParseException("Failed to parse HuggingFace response", e));
        }
    }
}
