package com.mphotle.threatbrief.security;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mphotle.threatbrief.exception.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;

/**
 * Reactive WebFilter responsible for enforcing client IP-based rate limiting on sensitive API endpoints.
 * <p>
 * Evaluates incoming requests against {@link RateLimiterService} token buckets, returning HTTP 429 Too Many Requests
 * with appropriate {@code Retry-After} headers when limits are reached.
 * </p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitingWebFilter implements WebFilter {

    private final RateLimiterService rateLimiterService;
    private final JsonMapper jsonMapper;

    public RateLimitingWebFilter(RateLimiterService rateLimiterService, JsonMapper jsonMapper) {
        this.rateLimiterService = rateLimiterService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!rateLimiterService.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        if (method == HttpMethod.POST && "/chat".equals(path)) {
            String clientIp = resolveClientIp(request);
            RateLimiterService.ConsumptionResult result = rateLimiterService.tryConsumeChat(clientIp);
            if (!result.allowed()) {
                log.warn("Rate limit exceeded for POST /chat from IP {}", clientIp);
                return handleRateLimitExceeded(exchange, result.retryAfterSeconds(),
                        "Chat rate limit exceeded. You can send up to 10 messages per minute. Please try again in "
                                + result.retryAfterSeconds() + " seconds.");
            }
        } else if (method == HttpMethod.GET && "/brief".equals(path)) {
            String clientIp = resolveClientIp(request);
            RateLimiterService.ConsumptionResult result = rateLimiterService.tryConsumeBrief(clientIp);
            if (!result.allowed()) {
                log.warn("Rate limit exceeded for GET /brief from IP {}", clientIp);
                return handleRateLimitExceeded(exchange, result.retryAfterSeconds(),
                        "Briefing request rate limit exceeded. Please wait a moment before trying again.");
            }
        }

        return chain.filter(exchange);
    }

    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange, long retryAfterSeconds, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(retryAfterSeconds));

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                message
        );

        byte[] bytes;
        try {
            bytes = jsonMapper.writeValueAsBytes(errorResponse);
        } catch (Exception e) {
            bytes = ("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"" + message + "\"}")
                    .getBytes();
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    public String resolveClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }
}
