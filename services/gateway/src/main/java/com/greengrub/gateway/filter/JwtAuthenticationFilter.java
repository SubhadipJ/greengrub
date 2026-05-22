package com.greengrub.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greengrub.gateway.config.GatewayProperties;
import com.greengrub.gateway.exception.ErrorResponse;
import com.greengrub.gateway.security.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * Gateway's authentication boundary. Public paths bypass token validation but
 * still get their {@code X-User-*} headers stripped — otherwise an
 * unauthenticated route (e.g. {@code POST /auth/signup}) becomes a way to
 * inject identity into a downstream service.
 *
 * <p>For authenticated routes we parse the Bearer token, then wrap the
 * request so {@code X-User-Id}, {@code X-User-Email}, {@code X-User-Role}
 * always reflect JWT claims rather than anything the client sent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String HEADER_USER_ID = "X-User-Id";
    static final String HEADER_USER_EMAIL = "X-User-Email";
    static final String HEADER_USER_ROLE = "X-User-Role";
    private static final Set<String> SPOOFABLE_HEADERS = Set.of(
            HEADER_USER_ID.toLowerCase(),
            HEADER_USER_EMAIL.toLowerCase(),
            HEADER_USER_ROLE.toLowerCase());

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (isPublicPath(request)) {
            chain.doFilter(stripSpoofedIdentityHeaders(request), response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeError(request, response, HttpStatus.UNAUTHORIZED,
                    "Missing or malformed Authorization header");
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            writeError(request, response, HttpStatus.UNAUTHORIZED, "Empty bearer token");
            return;
        }

        Claims claims;
        try {
            claims = jwtUtils.parse(token);
        } catch (ExpiredJwtException e) {
            writeError(request, response, HttpStatus.UNAUTHORIZED, "Token expired");
            return;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected JWT for {}: {}", request.getRequestURI(), e.getMessage());
            writeError(request, response, HttpStatus.UNAUTHORIZED, "Invalid token");
            return;
        }

        chain.doFilter(withAuthenticatedHeaders(request, claims), response);
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        for (String entry : properties.getPublicPaths()) {
            String trimmed = entry.trim();
            int sp = trimmed.indexOf(' ');
            String entryMethod = sp >= 0 ? trimmed.substring(0, sp) : null;
            String entryPath = sp >= 0 ? trimmed.substring(sp + 1).trim() : trimmed;
            if (entryMethod != null && !entryMethod.equalsIgnoreCase(method)) {
                continue;
            }
            if (pathMatcher.match(entryPath, uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hide any client-supplied identity headers — used on public paths where
     * the JWT filter doesn't run but the downstream service still trusts those
     * headers based on this gateway's contract.
     */
    private HttpServletRequest stripSpoofedIdentityHeaders(HttpServletRequest request) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                return SPOOFABLE_HEADERS.contains(name.toLowerCase()) ? null : super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                return SPOOFABLE_HEADERS.contains(name.toLowerCase())
                        ? Collections.emptyEnumeration()
                        : super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                List<String> filtered = new ArrayList<>();
                Enumeration<String> names = super.getHeaderNames();
                while (names.hasMoreElements()) {
                    String name = names.nextElement();
                    if (!SPOOFABLE_HEADERS.contains(name.toLowerCase())) {
                        filtered.add(name);
                    }
                }
                return Collections.enumeration(filtered);
            }
        };
    }

    private HttpServletRequest withAuthenticatedHeaders(HttpServletRequest request, Claims claims) {
        String userId = claims.get("userId", String.class);
        String email = claims.getSubject() != null ? claims.getSubject() : claims.get("email", String.class);
        String role = claims.get("role", String.class);

        Map<String, String> injected = new HashMap<>();
        if (userId != null) injected.put(HEADER_USER_ID.toLowerCase(), userId);
        if (email != null)  injected.put(HEADER_USER_EMAIL.toLowerCase(), email);
        if (role != null)   injected.put(HEADER_USER_ROLE.toLowerCase(), role);

        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                String authentic = injected.get(name.toLowerCase());
                if (authentic != null) return authentic;
                return SPOOFABLE_HEADERS.contains(name.toLowerCase()) ? null : super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                String authentic = injected.get(name.toLowerCase());
                if (authentic != null) return Collections.enumeration(List.of(authentic));
                return SPOOFABLE_HEADERS.contains(name.toLowerCase())
                        ? Collections.emptyEnumeration()
                        : super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                Set<String> names = new LinkedHashSet<>();
                Enumeration<String> original = super.getHeaderNames();
                while (original.hasMoreElements()) {
                    String n = original.nextElement();
                    if (!SPOOFABLE_HEADERS.contains(n.toLowerCase())) {
                        names.add(n);
                    }
                }
                if (injected.containsKey(HEADER_USER_ID.toLowerCase()))    names.add(HEADER_USER_ID);
                if (injected.containsKey(HEADER_USER_EMAIL.toLowerCase())) names.add(HEADER_USER_EMAIL);
                if (injected.containsKey(HEADER_USER_ROLE.toLowerCase()))  names.add(HEADER_USER_ROLE);
                return Collections.enumeration(names);
            }
        };
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            HttpStatus status, String message) throws IOException {
        response.resetBuffer();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(
                status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
