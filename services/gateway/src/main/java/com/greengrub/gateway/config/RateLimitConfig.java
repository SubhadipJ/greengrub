package com.greengrub.gateway.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.greengrub.gateway.filter.RateLimitFilter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-memory rate-limit infrastructure. Production rate limits will be enforced
 * by GCP API Gateway; this is a developer-facing safety net so a runaway
 * frontend can't hammer downstream services into the ground locally.
 */
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class RateLimitConfig {

    /**
     * One bucket per client (keyed by IP). Two bandwidths so a quick page
     * load (multiple parallel requests) isn't shut down by the per-minute cap
     * before the user has done anything wrong.
     */
    @Bean
    public LoadingCache<String, Bucket> rateLimitBuckets(GatewayProperties properties) {
        GatewayProperties.RateLimit cfg = properties.getRateLimit();
        return Caffeine.newBuilder()
                .maximumSize(cfg.getCacheMaxSize())
                .expireAfterAccess(Duration.ofMinutes(10))
                .build(ignored -> Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(cfg.getRequestsPerMinute())
                                .refillIntervally(cfg.getRequestsPerMinute(), Duration.ofMinutes(1))
                                .build())
                        .addLimit(Bandwidth.builder()
                                .capacity(cfg.getBurst())
                                .refillIntervally(cfg.getBurst(), Duration.ofSeconds(1))
                                .build())
                        .build());
    }

    /**
     * Register the rate-limit filter at high precedence so abusive
     * unauthenticated traffic is shed before any work is done.
     */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<RateLimitFilter>
    rateLimitFilterRegistration(RateLimitFilter filter) {
        org.springframework.boot.web.servlet.FilterRegistrationBean<RateLimitFilter> bean =
                new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        bean.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 20);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
