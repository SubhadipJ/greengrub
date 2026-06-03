package com.greengrub.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitFilterTest {

    @Mock private LoadingCache<String, Bucket> buckets;
    @Mock private Bucket bucket;
    @Mock private ConsumptionProbe probe;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(buckets, new ObjectMapper().registerModule(new JavaTimeModule()));
        when(buckets.get(anyString())).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
    }

    @Test
    void actuatorPath_isSkipped() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void nonActuatorPath_isNotSkipped() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/donations");
        assertThat(filter.shouldNotFilter(req)).isFalse();
    }

    @Test
    void tokenAvailable_passesThrough() throws Exception {
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(99L);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/donations");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getHeader("X-Rate-Limit-Remaining")).isEqualTo("99");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void tokenExhausted_returns429() throws Exception {
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(5_000_000_000L);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("5");
        assertThat(res.getHeader("X-Rate-Limit-Remaining")).isEqualTo("0");
        assertThat(res.getContentAsString()).contains("Too many requests");
    }

    @Test
    void tokenExhausted_retryAfterMinimumOne() throws Exception {
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(0L);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/food-requests");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    void xForwardedFor_usedAsClientKey() throws Exception {
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(50L);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/users");
        req.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        verify(buckets).get("203.0.113.5");
    }

    @Test
    void noXForwardedFor_usesRemoteAddr() throws Exception {
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(50L);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/users");
        req.setRemoteAddr("192.168.1.1");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        verify(buckets).get("192.168.1.1");
    }
}
