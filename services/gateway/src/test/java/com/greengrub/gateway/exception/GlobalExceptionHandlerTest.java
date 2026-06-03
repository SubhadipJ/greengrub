package com.greengrub.gateway.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleCircuitOpen_returns503() {
        CircuitBreaker cb = CircuitBreakerRegistry.ofDefaults().circuitBreaker("userServiceBreaker");
        cb.transitionToOpenState();
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(cb);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/users");

        ResponseEntity<ErrorResponse> response = handler.handleCircuitOpen(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(503);
        assertThat(response.getBody().getMessage()).contains("unavailable");
    }

    @Test
    void handleCircuitOpen_includesPath() {
        CircuitBreaker cb = CircuitBreakerRegistry.ofDefaults().circuitBreaker("donationServiceBreaker");
        cb.transitionToOpenState();
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(cb);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/donations");

        ResponseEntity<ErrorResponse> response = handler.handleCircuitOpen(ex, req);

        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/donations");
    }

    @Test
    void handleNotFound_returns404() throws Exception {
        NoResourceFoundException ex = new NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "/unknown", "Not found");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/unknown");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
    }

    @Test
    void handleNotFound_includesPathInMessage() throws Exception {
        NoResourceFoundException ex = new NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "/unknown/path", "Not found");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/unknown/path");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex, req);

        assertThat(response.getBody().getMessage()).contains("/unknown/path");
    }

    @Test
    void handleUnknown_returns500() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/donations");

        ResponseEntity<ErrorResponse> response = handler.handleUnknown(
                new RuntimeException("unexpected"), req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).contains("Unexpected");
    }

    @Test
    void errorResponse_hasTimestamp() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/users");

        ResponseEntity<ErrorResponse> response = handler.handleUnknown(
                new RuntimeException("oops"), req);

        assertThat(response.getBody().getTimestamp()).isNotNull();
    }
}
