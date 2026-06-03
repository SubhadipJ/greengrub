package com.greengrub.gateway.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    void of_setsAllFields() {
        ErrorResponse err = ErrorResponse.of(401, "Unauthorized", "Invalid token", "/api/v1/users");

        assertThat(err.getStatus()).isEqualTo(401);
        assertThat(err.getError()).isEqualTo("Unauthorized");
        assertThat(err.getMessage()).isEqualTo("Invalid token");
        assertThat(err.getPath()).isEqualTo("/api/v1/users");
        assertThat(err.getTimestamp()).isNotNull();
    }

    @Test
    void of_503_setsCorrectFields() {
        ErrorResponse err = ErrorResponse.of(503, "Service Unavailable", "Circuit open", "/api/v1/donations");

        assertThat(err.getStatus()).isEqualTo(503);
        assertThat(err.getError()).isEqualTo("Service Unavailable");
    }

    @Test
    void builder_createsValidObject() {
        ErrorResponse err = ErrorResponse.builder()
                .status(404)
                .error("Not Found")
                .message("Route not found")
                .path("/unknown")
                .build();

        assertThat(err.getStatus()).isEqualTo(404);
        assertThat(err.getMessage()).isEqualTo("Route not found");
    }
}
