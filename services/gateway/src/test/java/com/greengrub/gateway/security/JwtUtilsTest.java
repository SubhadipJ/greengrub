package com.greengrub.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

class JwtUtilsTest {

    private static final String VALID_SECRET = "ThisIsAValidSecretKeyThatIsAtLeast32BytesLong!!";
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils(VALID_SECRET);
        jwtUtils.init();
    }

    private String buildToken(String subject, long expiryMs) {
        SecretKey key = Keys.hmacShaKeyFor(VALID_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .claim("userId", "user-123")
                .claim("role", "USER")
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(key)
                .compact();
    }

    @Test
    void init_throwsWhenSecretTooShort() {
        JwtUtils weak = new JwtUtils("short");
        assertThatThrownBy(weak::init).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void init_succeedsWithValidSecret() {
        assertThatCode(() -> {
            JwtUtils utils = new JwtUtils(VALID_SECRET);
            utils.init();
        }).doesNotThrowAnyException();
    }

    @Test
    void parse_validToken_returnsClaims() {
        String token = buildToken("user@example.com", 60_000);
        Claims claims = jwtUtils.parse(token);
        assertThat(claims.getSubject()).isEqualTo("user@example.com");
        assertThat(claims.get("userId", String.class)).isEqualTo("user-123");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void parse_expiredToken_throwsJwtException() {
        String token = buildToken("user@example.com", -1000);
        assertThatThrownBy(() -> jwtUtils.parse(token))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    void parse_tamperedToken_throwsJwtException() {
        String token = buildToken("user@example.com", 60_000);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";
        assertThatThrownBy(() -> jwtUtils.parse(tampered))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void parse_randomString_throwsJwtException() {
        assertThatThrownBy(() -> jwtUtils.parse("not.a.jwt"))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void parse_wrongSecret_throwsJwtException() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "AnotherSecretKeyThatIsAlso32BytesLongXX".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user@example.com")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(wrongKey)
                .compact();
        assertThatThrownBy(() -> jwtUtils.parse(token))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void parse_emptyString_throwsJwtException() {
        assertThatThrownBy(() -> jwtUtils.parse(""))
                .isInstanceOf(Exception.class);
    }
}
