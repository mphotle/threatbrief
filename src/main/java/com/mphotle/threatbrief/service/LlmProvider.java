package com.mphotle.threatbrief.service;

import reactor.core.publisher.Mono;

/**
 * Strategy interface abstraction for Large Language Model (LLM) text generation providers.
 * <p>
 * Decouples prompt synthesis logic from specific AI backend providers, enabling reactive text generation.
 * </p>
 */
public interface LlmProvider {
    Mono<String> generate(String prompt);
}
