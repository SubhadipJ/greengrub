package com.greengrub.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Single canonical {@link CorsFilter} registered at HIGHEST_PRECEDENCE so
 * CORS preflight ({@code OPTIONS}) is answered before the rate-limit and
 * JWT filters run. Allowed origins are profile-driven via {@code
 * app.cors.allowed-origins} — never {@code *} with credentials.
 */
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration(GatewayProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration cfg = new CorsConfiguration();

        List<String> origins = properties.getCors().getAllowedOrigins();
        if (origins != null && !origins.isEmpty()) {
            cfg.setAllowedOrigins(origins);
            cfg.setAllowCredentials(true);
        }
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        cfg.setExposedHeaders(List.of("X-Rate-Limit-Remaining", "Retry-After"));
        cfg.setMaxAge(3600L);

        source.registerCorsConfiguration("/**", cfg);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
