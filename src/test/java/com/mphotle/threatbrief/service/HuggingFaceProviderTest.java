package com.mphotle.threatbrief.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mphotle.threatbrief.exception.LlmClientException;
import com.mphotle.threatbrief.exception.LlmParseException;
import com.mphotle.threatbrief.exception.LlmRateLimitExceededException;
import com.mphotle.threatbrief.exception.LlmServerException;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HuggingFaceProviderTest {

    private static final String DUMMY_TOKEN = "dummy-token";
    private static final String DUMMY_MODEL = "dummy-model";
    private HuggingFaceProvider provider;

    private WebClient buildWebClient(ExchangeFunction exchangeFunction) {
        return WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .baseUrl("https://router.huggingface.co")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Test
    void generate_when_successful_response_returns_content() {
        String responseBody = "{\"choices\":[{\"message\":{\"content\":\"Generated brief content\"}}]}";
        ExchangeFunction successExchange = request ->
                Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(responseBody)
                        .build());

        WebClient client = buildWebClient(successExchange);
        provider = new HuggingFaceProvider(client, new JsonMapper(), DUMMY_TOKEN, DUMMY_MODEL);

        Mono<String> result = provider.generate("dummy prompt");
        StepVerifier.create(result)
                .expectNext("Generated brief content")
                .verifyComplete();
    }

    @Test
    void generate_when_client_error_400_throws_LlmClientException() {
        String errorBody = "{\"error\":\"invalid request\"}";
        ExchangeFunction errorExchange = request ->
                Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(errorBody)
                        .build());

        WebClient client = buildWebClient(errorExchange);
        provider = new HuggingFaceProvider(client, new JsonMapper(), DUMMY_TOKEN, DUMMY_MODEL);

        Mono<String> result = provider.generate("bad prompt");
        StepVerifier.create(result)
                .expectError(LlmClientException.class)
                .verify();
    }

    @Test
    void generate_when_rate_limit_exceeded_429_throws_LlmRateLimitExceededException() {
        String errorBody = "{\"error\":\"rate limit exceeded\"}";
        ExchangeFunction errorExchange = request ->
                Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(errorBody)
                        .build());

        WebClient client = buildWebClient(errorExchange);
        provider = new HuggingFaceProvider(client, new com.fasterxml.jackson.databind.json.JsonMapper(), DUMMY_TOKEN, DUMMY_MODEL);

        Mono<String> result = provider.generate("prompt");
        StepVerifier.create(result)
                .expectError(LlmRateLimitExceededException.class)
                .verify();
    }

    @Test
    void generate_when_server_error_500_throws_LlmServerException() {
        String errorBody = "{\"error\":\"internal server error\"}";
        ExchangeFunction errorExchange = request ->
                Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(errorBody)
                        .build());

        WebClient client = buildWebClient(errorExchange);
        provider = new HuggingFaceProvider(client, new JsonMapper(), DUMMY_TOKEN, DUMMY_MODEL);

        Mono<String> result = provider.generate("prompt");
        StepVerifier.create(result)
                .expectError(LlmServerException.class)
                .verify();
    }

    @Test
    void generate_when_malformed_json_response_throws_LlmParseException() {
        String malformedJson = "{ invalid json }";
        ExchangeFunction exchange = request ->
                Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(malformedJson)
                        .build());

        WebClient client = buildWebClient(exchange);
        provider = new HuggingFaceProvider(client, new JsonMapper(), DUMMY_TOKEN, DUMMY_MODEL);

        Mono<String> result = provider.generate("prompt");
        StepVerifier.create(result)
                .expectError(LlmParseException.class)
                .verify();
    }
}
