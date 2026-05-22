package com.greengrub.gateway.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps everything the controllers / fallback / dispatcher might throw into a
 * uniform {@link ErrorResponse}. Filters that short-circuit before this
 * advice runs (rate limit, JWT) write the same DTO directly.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitOpen(CallNotPermittedException e, HttpServletRequest req) {
        log.warn("Circuit breaker {} is OPEN; short-circuited request {}",
                e.getCausingCircuitBreakerName(), req.getRequestURI());
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "Upstream service is unavailable (circuit open)", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Route not found: " + req.getRequestURI(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e, HttpServletRequest req) {
        log.error("Unhandled error at gateway for {}: {}", req.getRequestURI(), e.toString(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected gateway error", req);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(
                ErrorResponse.of(status.value(), status.getReasonPhrase(), message, req.getRequestURI()));
    }
}
