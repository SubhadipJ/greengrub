package com.greengrub.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Properties under {@code app.*}. Bound at startup so misconfiguration fails
 * fast rather than at first request.
 */
@Data
@ConfigurationProperties(prefix = "app")
public class GatewayProperties {

    private final Cors cors = new Cors();
    private final RateLimit rateLimit = new RateLimit();

    /**
     * Path patterns that bypass JWT validation. Format: either {@code "METHOD /path"}
     * (e.g. {@code "POST /api/v1/auth/login"}) or just {@code "/path"} to match any
     * method. Ant-style wildcards (*, **) are supported.
     */
    private List<String> publicPaths = new ArrayList<>();

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
    }

    @Data
    public static class RateLimit {
        /** Sustained rate per IP. */
        private long requestsPerMinute = 100;
        /** Short-window burst per IP — allows quick page loads without immediately tripping the minute bucket. */
        private long burst = 20;
        /** Cap the in-memory bucket cache so a flood of distinct IPs can't OOM the gateway. */
        private long cacheMaxSize = 50_000;
    }
}
