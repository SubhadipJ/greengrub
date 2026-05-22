# GreenGrub — Gateway (local)

The browser-facing entrypoint for the GreenGrub microservices platform on a developer machine. Validates the JWT minted by user-service, sanitises identity headers, propagates `X-User-Id` / `X-User-Email` / `X-User-Role` to downstream services, applies a generous per-IP rate limit, and wraps every downstream call in a circuit breaker.

> **Production note**: in production, this role is filled by **GCP API Gateway**. This Spring Cloud Gateway service is **local-only**. There is no `cloud` profile yet — production routing is a separate, deferred concern.

---

## Table of contents

1. [What this service does](#what-this-service-does)
2. [Tech stack](#tech-stack)
3. [Project layout](#project-layout)
4. [Running locally](#running-locally)
5. [Profiles](#profiles)
6. [Routes](#routes)
7. [Security model](#security-model)
8. [Rate limiting](#rate-limiting)
9. [Resilience](#resilience)
10. [CORS](#cors)
11. [Observability](#observability)
12. [Exception handling](#exception-handling)
13. [Conventions](#conventions)

---

## What this service does

Production traffic terminates at GCP API Gateway, which validates the JWT and forwards `X-User-*` headers to the right backend. Locally we still need *something* to honour that contract — otherwise dev either bypasses auth (and finds prod bugs late) or every service grows its own JWT filter (which contradicts user-service's deliberately gateway-fronted design).

The gateway:

- Validates the HS256 JWT minted by **user-service** (same secret, same claim shape: `userId`, `email`, `role`).
- **Strips client-supplied `X-User-Id` / `X-User-Email` / `X-User-Role` on every request** — even on public paths — so the only way those headers reach a downstream service is through this gateway.
- Routes to **user-service** (`:8082`), **donation-service** (`:8083`), **food-request** (`:8081`) by path prefix, all under a uniform `/api/v1/...` shape.
- Applies a generous in-memory per-IP rate limit (no Redis).
- Wraps each downstream call in a per-route Resilience4j circuit breaker with a uniform 503 fallback.
- Tags every request with a correlation id (`X-Request-Id`) and logs method/path/status/duration.

---

## Tech stack

| Layer | Choice |
| --- | --- |
| Runtime | Java 21, Spring Boot 4.0.3 |
| Gateway | `spring-cloud-starter-gateway-server-webmvc` (Spring Cloud 2025.1.0 — **WebMVC** flavour, not reactive) |
| Security | Spring Security 6 (`permitAll` chain — JWT filter is the actual auth boundary) |
| JWT | `jjwt` 0.12.6 — same library and version as user-service |
| Rate limit | Bucket4j 8.10 + Caffeine 3.1 (in-memory; no Redis) |
| Resilience | Resilience4j via `spring-cloud-starter-circuitbreaker-resilience4j` |
| Build | Maven (`./mvnw clean install -s settings.xml`) |

`settings.xml` carries credentials for the GitHub Packages repo (shared with the rest of the platform). Every Maven invocation passes `-s settings.xml`.

`spring-cloud-starter-netflix-eureka-client` and `spring-cloud-starter-loadbalancer` are kept on the classpath but disabled — local routes use static hostnames. Re-enabling Eureka in the future is one yaml flip away.

---

## Project layout

```
src/main/java/com/greengrub/gateway
├── GatewayApplication.java                     # @SpringBootApplication; excludes UserDetailsServiceAutoConfiguration
├── config/
│   ├── GatewayProperties.java                  # @ConfigurationProperties("app") — cors, rate-limit, public-paths
│   ├── SecurityConfig.java                     # SecurityFilterChain, permitAll, registers JWT filter
│   ├── CorsConfig.java                         # single CorsFilter at HIGHEST_PRECEDENCE
│   └── RateLimitConfig.java                    # Caffeine<String,Bucket> + filter registration
├── filter/
│   ├── JwtAuthenticationFilter.java            # Bearer parsing, claim → header injection, identity sanitisation
│   ├── RateLimitFilter.java                    # per-IP token bucket; 429 + Retry-After on exhaustion
│   └── RequestLoggingFilter.java               # X-Request-Id, MDC, response-line logging
├── security/JwtUtils.java                      # parse-only wrapper around jjwt 0.12.x
├── exception/
│   ├── ErrorResponse.java                      # uniform error envelope
│   └── GlobalExceptionHandler.java             # @RestControllerAdvice for circuit-open / 404 / catch-all
└── controller/FallbackController.java          # /fallback/{routeId} → 503 JSON

src/main/resources
├── application.yml                             # base config (eureka disabled, actuator, logging)
└── application-local.yml                       # routes, http-client timeouts, resilience4j, jwt secret, app.*
```

---

## Running locally

```bash
# 1. Start the downstream services (each in its own terminal):
#    user-service       :8082
#    donation-service   :8083
#    food-request       :8081

# 2. Then the gateway
./mvnw spring-boot:run -s settings.xml
```

The gateway is reachable at `http://localhost:8080`. Health: `http://localhost:8080/actuator/health`.

End-to-end smoke test:

```bash
# Login through the gateway (public path — no token needed)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
   -H 'Content-Type: application/json' \
   -d '{"email":"alice@example.com","password":"password123"}' \
   | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')

# Hit a protected route
curl -i http://localhost:8080/api/v1/users \
   -H "Authorization: Bearer $TOKEN"
```

---

## Profiles

The gateway has **only one profile today: `local`** (the default). Anything else is environment variables.

> Cloud profile is deliberately deferred. Production traffic will go through **GCP API Gateway** which already terminates JWT validation, routing, rate limiting, and CORS. Adding a Spring Cloud Gateway `cloud` profile now would mean maintaining a second authority that does nothing — easier to add it when we know what it actually needs to do.

`application.yml` (shared) sets the app name, server port, exposes actuator, and disables Eureka. `application-local.yml` carries everything route-related and the JWT secret default.

---

## Routes

All routes live under `spring.cloud.gateway.server.webmvc.routes` in `application-local.yml`. **The property prefix is `spring.cloud.gateway.server.webmvc.*`** in Spring Cloud 2025.1.0 — older blog posts using `spring.cloud.gateway.routes` (reactive) or `spring.cloud.gateway.mvc.routes` will not bind.

| Public path | → Downstream | Predicate |
| --- | --- | --- |
| `/api/v1/auth/**`, `/api/v1/users/**` | `localhost:8082` (user-service) | `Path` |
| `/api/v1/donations/**` | `localhost:8083` (donation-service) | `Path` |
| `/api/v1/food-requests/**` | `localhost:8081` (food-request) | `Path` |

Each route applies these filters in order:

1. `RemoveRequestHeader=X-User-Id` / `X-User-Email` / `X-User-Role` — strips any client-supplied identity headers **before** any authentic value is added. Belt-and-suspenders with the JWT filter's request wrapper (see [Security model](#security-model)).
2. `CircuitBreaker` — Resilience4j-backed; fallback forwards to `/fallback/{routeId}`.

The HTTP client connect/read timeouts are global (Spring Cloud Gateway WebMVC has no per-route `responseTimeout`):

```yaml
spring.cloud.gateway.server.webmvc.http-client:
  connect-timeout: 2s
  read-timeout: 5s
```

Per-route latency budgeting is enforced by the circuit breaker's `TimeLimiter` — see [Resilience](#resilience).

### Why downstream paths were renamed

When the gateway was added, the three services exposed inconsistent prefixes (`/api/v1/users`, `/api/donation`, `/api/foodRequests`). Rather than rewrite at the gateway and bake the inconsistency into the public surface, the controllers were updated:

- `donation-service` → `/api/v1/donations`
- `food-request` → `/api/v1/food-requests`

`user-service` was already on `/api/v1/...`. The public API now has one shape.

---

## Security model

The gateway is the **trust boundary**. Identity comes from **one place only** — the JWT — and reaches downstream services as `X-User-*` headers, never from the wire.

### Request flow

```
Browser ──▶ CorsFilter ──▶ RequestLoggingFilter ──▶ RateLimitFilter ──▶ JwtAuthenticationFilter ──▶ Spring Cloud Gateway ──▶ downstream
            (HIGHEST_         (X-Request-Id        (per-IP             (Bearer parse,                  (RemoveRequestHeader
             PRECEDENCE,       + MDC + log line)    bucket;             claim → header,                 X-User-* + CircuitBreaker)
             preflight                              429 if dry)         strip on public path)
             short-circuits)
```

### JWT contract

- Algorithm: HS256, secret loaded from `jwt.secret` (matches user-service's `JWT_SECRET` default in local).
- Claims: `userId` (string), `email` (string), `role` (string). Subject = email.
- Expiration: 24h (issued by user-service; the gateway only verifies).

The gateway only **parses** tokens — it never mints them. `AuthService` in user-service is still the credential authority.

### Public paths (skip JWT)

Configured via `app.public-paths` in `application-local.yml`. Uses `METHOD /pattern` shape (Ant-style wildcards):

- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `GET  /api/v1/auth/validate`
- `GET  /api/v1/users/check-email`
- `/actuator/**`
- `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`
- `/fallback/**`

### Identity-header sanitisation

The `RemoveRequestHeader` route filter strips client-supplied `X-User-*` before any authentic value is added — protecting **authenticated** routes. The `JwtAuthenticationFilter` *also* wraps the request to overwrite those headers with claims-derived values, AND on **public** paths it still strips them via `stripSpoofedIdentityHeaders` so an unauthenticated client cannot inject identity into a downstream by hitting `/auth/login` with `X-User-Id: somebody-else`.

This is deliberate belt-and-suspenders. A single layer of defence (route filter only, or wrapper only) leaves a gap in the other path.

### Local testing — manual identity injection bypass

Need to call user-service or another backend without going through the gateway? You'd be calling them directly on `localhost:808x`, in which case **the gateway-headers contract isn't enforced** — user-service's `GatewayHeadersFilter` will trust whatever you send. This is fine for local dev (non-prod isolation) but is exactly why `Never expose this service directly to the public internet` is on every backend's README.

---

## Rate limiting

Per-IP token bucket, in-memory (no Redis), generous defaults aimed at not getting in a developer's way:

| Setting | Default |
| --- | --- |
| `app.rate-limit.requests-per-minute` | 100 |
| `app.rate-limit.burst` (per second) | 20 |
| `app.rate-limit.cache-max-size` | 50,000 |

`Bucket4j` + `Caffeine` cache (`Caffeine.maximumSize(...)` to bound memory; entries expire 10 min after last access). Two bandwidths so a quick page load with a handful of parallel requests doesn't burn the per-minute budget.

Key: first hop of `X-Forwarded-For` if present, else `request.getRemoteAddr()`.

On exhaustion: 429 with the same `ErrorResponse` shape used everywhere else, plus `Retry-After: <seconds>` and `X-Rate-Limit-Remaining: 0`.

`/actuator/**` is **always** exempt — health probes never get rate-limited.

> Production rate limit lives in GCP API Gateway, with policy by route and by API key, backed by Cloud Armor. The local bucket is a developer safety net, not the production policy.

---

## Resilience

Each route gets its own Resilience4j circuit breaker. A dead user-service does **not** trip the donation or food breakers (and vice versa) — failure isolation by service.

| Property | userServiceBreaker | donationServiceBreaker | foodServiceBreaker |
| --- | --- | --- | --- |
| `sliding-window-size` | 10 | 10 | 10 |
| `failure-rate-threshold` | 50% | 50% | 50% |
| `wait-duration-in-open-state` | 10s | 10s | 10s |
| `permitted-number-of-calls-in-half-open-state` | 3 | 3 | 3 |
| TimeLimiter `timeout-duration` | 5s | 5s | 5s |

Per-route latency budgeting is enforced by the breaker's TimeLimiter (Spring Cloud Gateway WebMVC has no `responseTimeout` per route — that's a reactive-only knob). The global HTTP client connect-timeout (2s) and read-timeout (5s) form an outer envelope.

When a breaker opens, the route's `fallbackUri: forward:/fallback/<route-id>` kicks in. `FallbackController` returns **503 JSON** with the same `ErrorResponse` envelope used for every other failure path. State is exposed at `/actuator/health` (look for `circuitBreakers.<name>.state`).

### Why one breaker per route, not one shared

Same reason user-service has separate breakers for Postgres, image-service, and donation-service: a sick downstream should not poison sibling routes. Sharing a breaker would mean a flaky food-service trips traffic to the user-service login flow.

---

## CORS

A single `CorsFilter` is registered at `Ordered.HIGHEST_PRECEDENCE`. Spring Security's `cors(...)` integration is **not** used (it conflicted with the standalone filter when both tried to consume `corsFilter`). The standalone filter ensures preflight (`OPTIONS`) returns 200 before rate-limit, JWT, or any other filter sees it.

| Setting | Default |
| --- | --- |
| `app.cors.allowed-origins` | `http://localhost:3000,http://localhost:5173` |
| Allowed methods | `GET, POST, PUT, PATCH, DELETE, OPTIONS` |
| Allowed headers | `Authorization, Content-Type, Accept, X-Requested-With` |
| Exposed headers | `X-Rate-Limit-Remaining, Retry-After` |
| Credentials | enabled iff `allowed-origins` is non-empty (never `*` with credentials) |
| Max age | 1h |

---

## Observability

- `/actuator/health` — overall + circuit-breaker state for all three breakers
- `/actuator/info`
- `/actuator/metrics`, `/actuator/prometheus` — for scraping
- `/actuator/gateway` — Spring Cloud Gateway's introspection (route list, filter chain)

Every request is tagged with a correlation id by `RequestLoggingFilter`:

- Reads `X-Request-Id` from the incoming request, generating a UUID if absent
- Echoes it back on the response so the frontend can show it in error reports
- Puts it in the SLF4J `MDC` under key `requestId` so it appears on every log line for that request
- Logs the response line as `<method> <path> -> <status> (<elapsed> ms)`

---

## Exception handling

Every failure path emits the same JSON shape:

```json
{
  "timestamp": "2026-05-21T11:42:13.451+05:30",
  "status": 401,
  "error": "Unauthorized",
  "message": "Token expired",
  "path": "/api/v1/users"
}
```

| Source | Status | When |
| --- | --- | --- |
| `JwtAuthenticationFilter` | 401 | Missing/empty bearer; signature invalid; token expired; malformed JWT |
| `RateLimitFilter` | 429 | Per-IP bucket exhausted; includes `Retry-After` |
| `FallbackController` | 503 | Circuit breaker OPEN, or downstream timeout/exception |
| `GlobalExceptionHandler` for `CallNotPermittedException` | 503 | Edge case — breaker rejection that escapes the route filter |
| `GlobalExceptionHandler` for `NoResourceFoundException` | 404 | Path didn't match any route or controller |
| `GlobalExceptionHandler` catch-all | 500 | Unhandled gateway error; logged at ERROR with stack trace |

Filters write JSON directly via `response.getWriter()` because they short-circuit before any controller. The advice covers everything that does reach a controller (or the dispatcher).

---

## Conventions

- **Don't reintroduce a `cloud` profile here without a clear use case.** Production gateway is GCP API Gateway. If you find yourself building something the cloud gateway already does, that's a sign to push the work upstream rather than duplicate it.
- **One breaker per downstream.** Each new route gets its own `*Breaker` instance and its own `TimeLimiter` instance. Don't share.
- **One JWT contract.** The gateway parses `userId`, `email`, `role` claims. If a new claim is needed, add it to user-service's issuer first, then teach the gateway to forward it.
- **Strip client-supplied `X-User-*` on every path that targets a backend.** If a new public path is added under `app.public-paths`, the JWT filter still strips identity headers before forwarding — but verify any new route filter chain also includes `RemoveRequestHeader` for the three identity headers.
- **Property prefix is `spring.cloud.gateway.server.webmvc.*`** — *not* `spring.cloud.gateway.routes` (reactive) or `spring.cloud.gateway.mvc.routes`.
- **`./mvnw ... -s settings.xml`** for every Maven invocation, like the rest of the platform.
