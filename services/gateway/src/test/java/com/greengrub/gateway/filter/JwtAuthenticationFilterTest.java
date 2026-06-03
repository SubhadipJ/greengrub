package com.greengrub.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.greengrub.gateway.config.GatewayProperties;
import com.greengrub.gateway.security.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    private static final String SECRET = "ThisIsAValidSecretKeyThatIsAtLeast32BytesLong!!";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @Mock private JwtUtils jwtUtils;
    @Mock private GatewayProperties properties;
    @Mock private GatewayProperties.RateLimit rateLimit;

    private ObjectMapper objectMapper;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        GatewayProperties props = new GatewayProperties();
        props.setPublicPaths(List.of(
                "POST /api/v1/auth/signup",
                "POST /api/v1/auth/login",
                "GET /api/v1/auth/validate",
                "GET /api/v1/users/check-email",
                "/actuator/**",
                "/fallback/**"
        ));
        filter = new JwtAuthenticationFilter(jwtUtils, props, objectMapper);
    }

    private String buildToken(String subject, String userId, String role, long expiryMs) {
        return Jwts.builder()
                .subject(subject)
                .claim("userId", userId)
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(KEY)
                .compact();
    }

    private Claims buildClaims(String subject, String userId, String role) {
        String token = buildToken(subject, userId, role, 60_000);
        return Jwts.parser().verifyWith(KEY).build().parseSignedClaims(token).getPayload();
    }

    @Test
    void publicPath_noToken_passesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        verify(jwtUtils, never()).parse(anyString());
    }

    @Test
    void publicPath_stripsIdentityHeaders() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/signup");
        req.addHeader("X-User-Id", "spoofed-id");
        req.addHeader("X-User-Email", "spoofed@evil.com");
        req.addHeader("X-User-Role", "ADMIN");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        jakarta.servlet.http.HttpServletRequest forwarded = (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        assertThat(forwarded.getHeader("X-User-Id")).isNull();
        assertThat(forwarded.getHeader("X-User-Email")).isNull();
        assertThat(forwarded.getHeader("X-User-Role")).isNull();
    }

    @Test
    void actuatorPath_noToken_passesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(jwtUtils, never()).parse(anyString());
    }

    @Test
    void protectedPath_noAuthHeader_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/donations");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Missing or malformed");
    }

    @Test
    void protectedPath_malformedAuthHeader_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/donations");
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void protectedPath_emptyBearerToken_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/donations");
        req.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Empty bearer token");
    }

    @Test
    void protectedPath_expiredToken_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/donations");
        req.addHeader("Authorization", "Bearer expired.token.here");
        MockHttpServletResponse res = new MockHttpServletResponse();

        when(jwtUtils.parse("expired.token.here"))
                .thenThrow(mock(ExpiredJwtException.class));

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Token expired");
    }

    @Test
    void protectedPath_invalidToken_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/donations");
        req.addHeader("Authorization", "Bearer invalid.token");
        MockHttpServletResponse res = new MockHttpServletResponse();

        when(jwtUtils.parse("invalid.token")).thenThrow(new JwtException("bad"));

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Invalid token");
    }

    @Test
    void protectedPath_validToken_injectsHeaders() throws Exception {
        Claims claims = buildClaims("user@example.com", "user-123", "USER");
        when(jwtUtils.parse("valid.token")).thenReturn(claims);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/donations");
        req.addHeader("Authorization", "Bearer valid.token");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        jakarta.servlet.http.HttpServletRequest forwarded = (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        assertThat(forwarded.getHeader("X-User-Id")).isEqualTo("user-123");
        assertThat(forwarded.getHeader("X-User-Email")).isEqualTo("user@example.com");
        assertThat(forwarded.getHeader("X-User-Role")).isEqualTo("USER");
    }

    @Test
    void protectedPath_validToken_stripsClientSpoofedHeaders() throws Exception {
        Claims claims = buildClaims("real@example.com", "real-id", "USER");
        when(jwtUtils.parse("valid.token")).thenReturn(claims);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/food-requests");
        req.addHeader("Authorization", "Bearer valid.token");
        req.addHeader("X-User-Id", "spoofed-id");
        req.addHeader("X-User-Role", "ADMIN");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        jakarta.servlet.http.HttpServletRequest forwarded = (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        assertThat(forwarded.getHeader("X-User-Id")).isEqualTo("real-id");
        assertThat(forwarded.getHeader("X-User-Role")).isEqualTo("USER");
    }

    @Test
    void fallbackPath_noToken_passesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/fallback/user-service");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(jwtUtils, never()).parse(anyString());
    }

    // --- Wrapper getHeaders / getHeaderNames coverage ---

    @Test
    void publicPath_getHeaders_returnEmptyForSpoofedHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/signup");
        req.addHeader("X-User-Id", "evil");
        req.addHeader("Accept", "application/json");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        jakarta.servlet.http.HttpServletRequest forwarded = (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        assertThat(forwarded.getHeaders("X-User-Id").hasMoreElements()).isFalse();
        assertThat(forwarded.getHeaders("Accept").hasMoreElements()).isTrue();
    }

    @Test
    void publicPath_getHeaderNames_excludesSpoofedHeaders() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/signup");
        req.addHeader("X-User-Role", "ADMIN");
        req.addHeader("Accept", "application/json");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        jakarta.servlet.http.HttpServletRequest forwarded = (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        java.util.List<String> names = java.util.Collections.list(forwarded.getHeaderNames());
        assertThat(names).doesNotContain("X-User-Role");
        assertThat(names).contains("Accept");
    }

    @Test
    void authenticatedPath_getHeaders_returnsJwtValues() throws Exception {
        Claims claims = buildClaims("user@example.com", "user-123", "USER");
        when(jwtUtils.parse("valid.token")).thenReturn(claims);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/donations");
        req.addHeader("Authorization", "Bearer valid.token");
        req.addHeader("X-User-Id", "spoofed");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        jakarta.servlet.http.HttpServletRequest forwarded = (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        java.util.List<String> userIdValues = java.util.Collections.list(forwarded.getHeaders("X-User-Id"));
        assertThat(userIdValues).containsExactly("user-123");
    }

    @Test
    void authenticatedPath_getHeaderNames_includesInjectedHeaders() throws Exception {
        Claims claims = buildClaims("user@example.com", "user-123", "USER");
        when(jwtUtils.parse("valid.token")).thenReturn(claims);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/donations");
        req.addHeader("Authorization", "Bearer valid.token");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        jakarta.servlet.http.HttpServletRequest forwarded = (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        java.util.List<String> names = java.util.Collections.list(forwarded.getHeaderNames());
        assertThat(names).contains("X-User-Id", "X-User-Email", "X-User-Role");
    }

    @Test
    void authenticatedPath_getHeaders_emptyForOtherSpoofedHeaders() throws Exception {
        Claims claims = buildClaims("user@example.com", "user-123", "USER");
        when(jwtUtils.parse("valid.token")).thenReturn(claims);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/donations");
        req.addHeader("Authorization", "Bearer valid.token");
        req.addHeader("X-User-Email", "hacker@evil.com");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        jakarta.servlet.http.HttpServletRequest forwarded = (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        java.util.List<String> emailValues = java.util.Collections.list(forwarded.getHeaders("X-User-Email"));
        assertThat(emailValues).containsExactly("user@example.com");
    }
}
