package com.greengrub.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter config = new RequestLoggingFilter();

    @Test
    void registrationBean_hasCorrectOrder() {
        FilterRegistrationBean<OncePerRequestFilter> bean =
                config.requestLoggingFilterRegistration();
        assertThat(bean.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10);
    }

    @Test
    void registrationBean_urlPatternIsWildcard() {
        FilterRegistrationBean<OncePerRequestFilter> bean =
                config.requestLoggingFilterRegistration();
        assertThat(bean.getUrlPatterns()).contains("/*");
    }

    @Test
    void filter_setsRequestIdHeader_whenAbsent() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        FilterRegistrationBean<OncePerRequestFilter> bean =
                config.requestLoggingFilterRegistration();
        bean.getFilter().doFilter(req, res, chain);

        assertThat(res.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void filter_preservesExistingRequestId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/users");
        req.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "trace-abc-123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        FilterRegistrationBean<OncePerRequestFilter> bean =
                config.requestLoggingFilterRegistration();
        bean.getFilter().doFilter(req, res, chain);

        assertThat(res.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("trace-abc-123");
    }

    @Test
    void filter_chainsToNextFilter() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        FilterRegistrationBean<OncePerRequestFilter> bean =
                config.requestLoggingFilterRegistration();
        bean.getFilter().doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void filter_blankRequestId_generatesNewOne() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/donations");
        req.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "   ");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        FilterRegistrationBean<OncePerRequestFilter> bean =
                config.requestLoggingFilterRegistration();
        bean.getFilter().doFilter(req, res, chain);

        String id = res.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(id).isNotBlank().isNotEqualTo("   ");
    }
}
