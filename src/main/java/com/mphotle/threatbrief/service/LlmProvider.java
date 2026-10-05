package com.mphotle.threatbrief.service;

import reactor.core.publisher.Mono;

public interface LlmProvider {
    Mono<String> generate(String prompt);
}
