package com.greengrub.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Tags every request with a correlation id (echoed downstream in
 * {@code X-Request-Id}) and logs method/path/status/duration. Runs after
 * CORS but before rate limiting so 429s and 401s still get logged.
 */
@Slf4j
@Configuration
public class RequestLoggingFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> requestLoggingFilterRegistration() {
        FilterRegistrationBean<OncePerRequestFilter> bean = new FilterRegistrationBean<>(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                String requestId = request.getHeader(REQUEST_ID_HEADER);
                if (requestId == null || requestId.isBlank()) {
                    requestId = UUID.randomUUID().toString();
                }
                MDC.put(MDC_KEY, requestId);
                response.setHeader(REQUEST_ID_HEADER, requestId);
                long start = System.currentTimeMillis();
                try {
                    chain.doFilter(request, response);
                } finally {
                    long elapsed = System.currentTimeMillis() - start;
                    log.info("{} {} -> {} ({} ms)",
                            request.getMethod(), request.getRequestURI(), response.getStatus(), elapsed);
                    MDC.remove(MDC_KEY);
                }
            }
        });
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
