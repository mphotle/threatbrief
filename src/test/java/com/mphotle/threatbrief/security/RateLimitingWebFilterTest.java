package com.mphotle.threatbrief.security;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mphotle.threatbrief.config.JacksonConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RateLimitingWebFilterTest {

    private RateLimiterService rateLimiterService;
    private RateLimitingWebFilter webFilter;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        this.jsonMapper = new JacksonConfig().jsonMapper();
        this.rateLimiterService = new RateLimiterService(
                true,
                2, 2, 60,
                2, 2, 60
        );
        this.webFilter = new RateLimitingWebFilter(rateLimiterService, jsonMapper);
    }

    @Test
    void filter_when_chat_within_limit_passes_to_chain() {
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.post("/chat")
                .header("X-Forwarded-For", "203.0.113.1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(webFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void filter_when_chat_exceeds_limit_returns_429_with_retry_after() {
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        String ip = "203.0.113.2";
        // Consume the 2 allowed tokens
        for (int i = 0; i < 2; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.post("/chat")
                    .header("X-Forwarded-For", ip)
                    .build();
            webFilter.filter(MockServerWebExchange.from(req), chain).block();
        }

        // 3rd attempt should be rejected
        MockServerHttpRequest thirdReq = MockServerHttpRequest.post("/chat")
                .header("X-Forwarded-For", ip)
                .build();
        MockServerWebExchange thirdExchange = MockServerWebExchange.from(thirdReq);

        StepVerifier.create(webFilter.filter(thirdExchange, chain))
                .verifyComplete();

        assertThat(thirdExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(thirdExchange.getResponse().getHeaders().getFirst("Retry-After")).isNotNull();
    }

    @Test
    void filter_when_brief_exceeds_limit_returns_429() {
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        String ip = "203.0.113.3";
        // Consume 2 allowed tokens for /brief
        for (int i = 0; i < 2; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.get("/brief")
                    .header("X-Forwarded-For", ip)
                    .build();
            webFilter.filter(MockServerWebExchange.from(req), chain).block();
        }

        // 3rd attempt rejected
        MockServerHttpRequest thirdReq = MockServerHttpRequest.get("/brief")
                .header("X-Forwarded-For", ip)
                .build();
        MockServerWebExchange thirdExchange = MockServerWebExchange.from(thirdReq);

        StepVerifier.create(webFilter.filter(thirdExchange, chain))
                .verifyComplete();

        assertThat(thirdExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(thirdExchange.getResponse().getHeaders().getFirst("Retry-After")).isNotNull();
    }

    @Test
    void filter_when_static_resource_or_other_path_bypasses_rate_limiting() {
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/index.html")
                .header("X-Forwarded-For", "203.0.113.4")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(webFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void resolveClientIp_extracts_first_ip_from_comma_separated_x_forwarded_for() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/chat")
                .header("X-Forwarded-For", "198.51.100.5, 10.0.0.1, 127.0.0.1")
                .build();

        String ip = webFilter.resolveClientIp(request);
        assertThat(ip).isEqualTo("198.51.100.5");
    }

    @Test
    void resolveClientIp_falls_back_to_x_real_ip_when_x_forwarded_for_is_missing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/chat")
                .header("X-Real-IP", "198.51.100.6")
                .build();

        String ip = webFilter.resolveClientIp(request);
        assertThat(ip).isEqualTo("198.51.100.6");
    }

    @Test
    void resolveClientIp_falls_back_to_remote_address_when_headers_missing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/chat")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();

        String ip = webFilter.resolveClientIp(request);
        assertThat(ip).isEqualTo("127.0.0.1");
    }
}
