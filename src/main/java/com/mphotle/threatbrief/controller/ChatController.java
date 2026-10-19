package com.mphotle.threatbrief.controller;

import com.mphotle.threatbrief.model.ChatRequest;
import com.mphotle.threatbrief.model.ChatResponse;
import com.mphotle.threatbrief.model.Message;
import com.mphotle.threatbrief.service.AgentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class ChatController {

    private final AgentService agentService;

    public ChatController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public Mono<ChatResponse> chat(@RequestBody ChatRequest request) {
        return agentService.chat(request.messages())
                .map(content -> new ChatResponse(new Message("assistant", content)));
    }
}
