package com.greengrub.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CorsConfigTest {

    private CorsConfig corsConfig;

    @BeforeEach
    void setUp() {
        corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "allowedOriginsRaw", "http://localhost:5173");
    }

    @Test
    void corsFilter_hasHighestPrecedence() {
        FilterRegistrationBean<?> bean = corsConfig.corsFilter();
        assertThat(bean.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void corsFilter_isNotNull() {
        assertThat(corsConfig.corsFilter()).isNotNull();
        assertThat(corsConfig.corsFilter().getFilter()).isNotNull();
    }

    @Test
    void originStripFilter_hasOrderHighestPrecedencePlusOne() {
        FilterRegistrationBean<?> bean = corsConfig.originStripFilter();
        assertThat(bean.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
    }

    @Test
    void originStripFilter_allowsOptionsThrough() throws Exception {
        FilterRegistrationBean<OncePerRequestFilter> bean = corsConfig.originStripFilter();
        OncePerRequestFilter filter = bean.getFilter();

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        request.addHeader("Origin", "http://localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        // OPTIONS must pass through with the original request unchanged
        verify(chain).doFilter(request, response);
    }

    @Test
    void originStripFilter_stripsOriginFromNonOptionsRequest() throws Exception {
        FilterRegistrationBean<OncePerRequestFilter> bean = corsConfig.originStripFilter();
        OncePerRequestFilter filter = bean.getFilter();

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("Content-Type", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            HttpServletRequest wrappedReq = invocation.getArgument(0);
            // Origin must be hidden from downstream
            assertThat(wrappedReq.getHeader("Origin")).isNull();
            assertThat(wrappedReq.getHeaders("Origin").hasMoreElements()).isFalse();
            // Other headers must still be present
            assertThat(wrappedReq.getHeader("Content-Type")).isEqualTo("application/json");
            return null;
        }).when(chain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    void corsFilter_multipleOriginsConfigured() {
        ReflectionTestUtils.setField(corsConfig, "allowedOriginsRaw",
                "http://localhost:5173,http://localhost:5773");
        FilterRegistrationBean<?> bean = corsConfig.corsFilter();
        assertThat(bean).isNotNull();
        assertThat(bean.getFilter()).isNotNull();
    }
}
