package com.greengrub.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:5173}")
    private String allowedOriginsRaw;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.asList(allowedOriginsRaw.split(","));
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setExposedHeaders(Arrays.asList("X-Rate-Limit-Remaining", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    // Strip Origin header from non-OPTIONS requests after CorsFilter has already
    // set the CORS response headers — prevents SCG's RoutePredicateHandlerMapping
    // from doing its own (empty-config) CORS validation and returning 403.
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> originStripFilter() {
        FilterRegistrationBean<OncePerRequestFilter> bean = new FilterRegistrationBean<>(
            new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request,
                                                HttpServletResponse response,
                                                FilterChain chain) throws ServletException, IOException {
                    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                        chain.doFilter(request, response);
                        return;
                    }
                    chain.doFilter(new HttpServletRequestWrapper(request) {
                        @Override
                        public String getHeader(String name) {
                            if ("Origin".equalsIgnoreCase(name)) return null;
                            return super.getHeader(name);
                        }
                        @Override
                        public Enumeration<String> getHeaders(String name) {
                            if ("Origin".equalsIgnoreCase(name)) return Collections.emptyEnumeration();
                            return super.getHeaders(name);
                        }
                    }, response);
                }
            }
        );
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return bean;
    }
}
