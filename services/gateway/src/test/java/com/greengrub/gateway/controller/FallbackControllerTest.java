package com.greengrub.gateway.controller;

import com.greengrub.gateway.exception.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackControllerTest {

    private FallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new FallbackController();
    }

    @Test
    void fallback_returnsServiceUnavailable() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/donations");
        ResponseEntity<ErrorResponse> response = controller.fallback("donation-service", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void fallback_includesRouteIdInMessage() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/donations");
        ResponseEntity<ErrorResponse> response = controller.fallback("donation-service", req);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("donation-service");
    }

    @Test
    void fallback_includesCorrectStatusCode() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/food-requests");
        ResponseEntity<ErrorResponse> response = controller.fallback("food-service", req);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(503);
    }

    @Test
    void fallback_includesRequestPath() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/users/1");
        ResponseEntity<ErrorResponse> response = controller.fallback("user-service", req);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/users/1");
    }

    @Test
    void generic_returnsServiceUnavailable() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/fallback");
        ResponseEntity<ErrorResponse> response = controller.generic(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void generic_includesGenericMessage() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/fallback");
        ResponseEntity<ErrorResponse> response = controller.generic(req);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("unavailable");
    }
}
