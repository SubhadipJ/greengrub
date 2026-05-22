package com.greengrub.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Parse-only counterpart to user-service's JwtUtil. The gateway never mints
 * tokens — that authority remains with user-service — but it must validate
 * the same HS256 secret that user-service signs with.
 */
@Component
@Slf4j
public class JwtUtils {

    private final String secret;
    private SecretKey key;

    public JwtUtils(@Value("${jwt.secret}") String secret) {
        this.secret = secret;
    }

    @PostConstruct
    void init() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        log.info("JwtUtils initialised with HS256 secret ({} bytes)", secret.getBytes(StandardCharsets.UTF_8).length);
    }

    /**
     * Verify the token and return its claims. Throws on any signature, format
     * or expiry failure — caller is expected to catch and translate to 401.
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
