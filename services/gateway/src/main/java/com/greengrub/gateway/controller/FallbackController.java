package com.greengrub.gateway.controller;

import com.greengrub.gateway.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-route fallbacks targeted by the {@code CircuitBreaker} gateway filter.
 * The route forwards here when the breaker is OPEN or the upstream call
 * throws/exceeds its TimeLimiter. Returning JSON keeps the error shape
 * identical to what filters and the global advice produce.
 */
@RestController
@RequestMapping("/fallback")
@Slf4j
public class FallbackController {

    @RequestMapping("/{routeId}")
    public ResponseEntity<ErrorResponse> fallback(@PathVariable String routeId, HttpServletRequest request) {
        log.warn("Fallback engaged for route '{}' (path {})", routeId, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                        routeId + " is currently unavailable; please retry shortly",
                        request.getRequestURI()));
    }

    /**
     * Catch-all so any unknown route id (e.g. someone configures a new route
     * but forgets to forward to {@code /fallback/<their-route>}) still returns
     * structured JSON instead of a whitelabel 404.
     */
    @GetMapping
    public ResponseEntity<ErrorResponse> generic(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                        "Upstream service is currently unavailable",
                        request.getRequestURI()));
    }
}
