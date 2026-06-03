package com.greengrub.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.greengrub.gateway.filter.RateLimitFilter;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigTest {

    private final RateLimitConfig config = new RateLimitConfig();

    @Test
    void rateLimitBuckets_returnsNonNullCache() {
        GatewayProperties props = new GatewayProperties();
        LoadingCache<String, Bucket> cache = config.rateLimitBuckets(props);
        assertThat(cache).isNotNull();
    }

    @Test
    void rateLimitBuckets_createsNewBucketPerKey() {
        GatewayProperties props = new GatewayProperties();
        LoadingCache<String, Bucket> cache = config.rateLimitBuckets(props);

        Bucket b1 = cache.get("192.168.1.1");
        Bucket b2 = cache.get("10.0.0.1");

        assertThat(b1).isNotNull();
        assertThat(b2).isNotNull();
    }

    @Test
    void rateLimitBuckets_sameKeyReturnsSameBucket() {
        GatewayProperties props = new GatewayProperties();
        LoadingCache<String, Bucket> cache = config.rateLimitBuckets(props);

        Bucket b1 = cache.get("192.168.1.1");
        Bucket b2 = cache.get("192.168.1.1");

        assertThat(b1).isSameAs(b2);
    }

    @Test
    void rateLimitBuckets_bucketHasTokens() {
        GatewayProperties props = new GatewayProperties();
        LoadingCache<String, Bucket> cache = config.rateLimitBuckets(props);

        Bucket bucket = cache.get("client-ip");
        assertThat(bucket.tryConsume(1)).isTrue();
    }

    @Test
    void rateLimitFilterRegistration_hasCorrectOrder() {
        GatewayProperties props = new GatewayProperties();
        LoadingCache<String, Bucket> buckets = config.rateLimitBuckets(props);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RateLimitFilter filter = new RateLimitFilter(buckets, mapper);

        FilterRegistrationBean<RateLimitFilter> bean = config.rateLimitFilterRegistration(filter);

        assertThat(bean.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 20);
        assertThat(bean.getUrlPatterns()).contains("/*");
    }
}
