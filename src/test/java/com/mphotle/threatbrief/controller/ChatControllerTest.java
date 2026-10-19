package com.mphotle.threatbrief.controller;

import com.mphotle.threatbrief.model.ChatRequest;
import com.mphotle.threatbrief.model.ChatResponse;
import com.mphotle.threatbrief.model.Message;
import com.mphotle.threatbrief.service.AgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChatControllerTest {

    private WebTestClient webTestClient;

    @Mock
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        ChatController controller = new ChatController(agentService);
        this.webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void chat_when_valid_request_returns_200_and_assistant_message() {
        ChatRequest request = new ChatRequest(List.of(
                new Message("user", "Hello")
        ));

        when(agentService.chat(anyList())).thenReturn(Mono.just("Hello! How can I help?"));

        webTestClient.post()
                .uri("/chat")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatResponse.class)
                .value(response -> {
                    assert response.message().role().equals("assistant");
                    assert response.message().content().equals("Hello! How can I help?");
                });
    }
}
