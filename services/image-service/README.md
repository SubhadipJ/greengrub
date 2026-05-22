# Image Service

A gRPC-based microservice that handles image upload, retrieval, and deletion for the GreenGrub platform. Images are stored in **GCP Cloud Storage** (binary data) and metadata is persisted in **Google Cloud Firestore** (k8s) or **MongoDB** (local development).

---

## Project Structure

```
src/main/java/com/greengrub/image_service/
├── config/                  # (empty after Firebase removal — Firestore is auto-configured)
├── entity/
│   ├── Image.java           # Firestore entity (k8s profile)
│   └── LocalImage.java      # MongoDB entity (local profile)
├── enumeration/
│   └── CreatorType.java     # CUSTOMER, FOOD_REQUEST
├── exception/
│   ├── GcpUploadException.java
│   ├── ImageNotFoundException.java
│   ├── ImageStorageException.java
│   └── InvalidImageRequestException.java
├── helper/
│   └── ImageHelper.java     # Entity ↔ Proto conversion
├── interceptor/
│   └── GrpcExceptionInterceptor.java
├── repository/
│   ├── GCPStorageImageRepository.java   # FirestoreReactiveRepository (k8s)
│   └── LocalStorageImageRepository.java # MongoRepository (local)
├── service/
│   ├── GcpStorageService.java           # GCP Cloud Storage upload logic
│   ├── ImageServiceImpl.java            # gRPC service (k8s)
│   └── LocalImageServiceImpl.java       # gRPC service (local)
└── ImageServiceApplication.java
```

---

## Architecture Overview

```
Client (gRPC)
    │
    ▼
┌─────────────────────────────────────────────┐
│  GrpcExceptionInterceptor                   │  ← Maps exceptions to gRPC status codes
├─────────────────────────────────────────────┤
│  ImageServiceImpl (k8s)                     │  ← Business logic + resilience wrappers
│  LocalImageServiceImpl (local)              │
├─────────────────────────────────────────────┤
│  FirestoreTemplate (k8s)                    │  ← Metadata persistence
│  MongoRepository (local)                    │
├─────────────────────────────────────────────┤
│  GcpStorageService                          │  ← Binary image storage (GCP Cloud Storage)
└─────────────────────────────────────────────┘
```
---

## Things to Know Before Contributing

1. **Two parallel implementations** — Every feature must work in both `local` (MongoDB) and `k8s` (Firestore) profiles. The `@Profile` annotation controls which beans are active.

2. **Resilience annotations go on wrapper methods** — Don't put `@Retry`/`@CircuitBreaker` directly on repository calls. Wrap them in a private method so the exception handling logic stays consistent.

3. **Retryable vs non-retryable** — Only infrastructure failures (`ImageStorageException`, `GcpUploadException`) are retried. Never add business exceptions to the retry-exceptions list.

4. **Delete has no retry** — Deletes are intentionally not retried because a failed delete may have already succeeded server-side. Retrying would be unsafe.

5. **Proto contracts are shared** — Don't modify proto definitions here. They live in the `proto-contracts` repo and are pulled as a Maven dependency.

6. **FirestoreTemplate is reactive under the hood** — The k8s service calls `.block()` on `Mono`/`Flux` returns to stay synchronous. This is intentional — the gRPC layer is blocking and Resilience4j needs synchronous methods.

7. **Circuit breaker state is observable** — Check `GET /actuator/health` to see breaker states (`CLOSED`, `OPEN`, `HALF_OPEN`).

8. **GrpcExceptionInterceptor is the single point of error translation** — All new exception types must be added to `mapToGrpcStatus()` or they'll surface as `INTERNAL`.
---

## Profiles

| Profile | Database | Storage | Use Case |
|---------|----------|---------|----------|
| `local` | MongoDB (Docker) | Local filesystem (in-DB as bytes) | Local development |
| `k8s` | Google Cloud Firestore | GCP Cloud Storage | GKE deployment |


---

## Key Config Files

| File | Purpose |
|------|---------|
| `application-local.properties` | Local profile — MongoDB connection, lighter resilience thresholds |
| `application-k8s.yml` | K8s profile — Firestore config, production resilience thresholds |
---

## gRPC API

| Method | Request | Description |
|--------|---------|-------------|
| `UploadImages` | `UploadImagesRequest` | Upload one or more images for a creator |
| `GetImagesByCreator` | `GetImagesByCreatorRequest` | Get all images by creator ID |
| `GetImageByImageId` | `ImageByImageIdRequest` | Get a single image by ID |
| `DeleteImagesByImageId` | `ImageByImageIdRequest` | Delete an image by ID |

Proto definitions live in the shared [`proto-contracts`](https://maven.pkg.github.com/greengrub-team/proto-contracts) repository.

---

## Resilience Patterns

We use **Resilience4j** to protect the service from cascading failures. Three patterns are applied:

### Retry

Automatically retries failed operations caused by transient infrastructure issues.

| Instance | Applied To | Config |
|----------|-----------|--------|
| `firestoreRetry` (k8s) / `mongoRetry` (local) | All DB read/write operations | 3 attempts, exponential backoff (500ms → 1s → 2s) |
| `gcpRetry` | GCP Cloud Storage uploads | 3 attempts, exponential backoff (1s → 2s → 4s) |

**Only `ImageStorageException` and `GcpUploadException` trigger retries.** Business exceptions (`ImageNotFoundException`, `InvalidImageRequestException`) propagate immediately without retrying.

### Circuit Breaker

Stops sending requests to a failing dependency, letting it recover instead of overwhelming it.

| Instance | Applied To | Config |
|----------|-----------|--------|
| `firestoreBreaker` (k8s) / `mongoBreaker` (local) | All DB operations | Opens at 50% failure rate in 10-call window, 30s recovery wait |
| `gcpBreaker` | GCP Cloud Storage | Opens at 50% failure rate in 10-call window, 60s recovery wait |

**States:** `CLOSED` (normal) → `OPEN` (fail-fast) → `HALF_OPEN` (probing recovery)

When open, all requests fail immediately with `UNAVAILABLE` — no retries, no database calls.

### TimeLimiter

Cancels operations that take too long, preventing thread starvation.

| Instance | Applied To | Config |
|----------|-----------|--------|
| `gcpUploadLimiter` | GCP Cloud Storage uploads | 10s timeout, cancels future |

---

## Exception Handling

All exceptions are caught by `GrpcExceptionInterceptor` and mapped to appropriate gRPC status codes. No raw stack traces ever reach the client.

### Exception Hierarchy

```
RuntimeException
├── InvalidImageRequestException  →  INVALID_ARGUMENT (400)
├── ImageNotFoundException        →  NOT_FOUND (404)
└── ImageStorageException         →  UNAVAILABLE (503)
    └── GcpUploadException        →  UNAVAILABLE (503)
```

### Where Each Exception is Thrown

| Exception | Thrown When | Retryable? |
|-----------|------------|------------|
| `InvalidImageRequestException` | Blank or missing image ID in request | No |
| `ImageNotFoundException` | Image ID doesn't exist in the database | No |
| `ImageStorageException` | Firestore/MongoDB operation fails (timeout, connection error) | Yes |
| `GcpUploadException` | GCP Cloud Storage upload fails | Yes |

### How the Interceptor Works

`GrpcExceptionInterceptor` is a global gRPC server interceptor (`@GrpcGlobalServerInterceptor`). It wraps every incoming call, catches any exception thrown during processing, and translates it:

1. Exception is thrown in the service layer
2. If retryable → Resilience4j retries it (up to max attempts)
3. If retries exhausted or circuit breaker open → exception propagates
4. Interceptor catches it → maps to gRPC `Status` → returns structured error to client

---

## Running Locally

### Prerequisites

- Java 21
- Docker Desktop (for MongoDB)
- Maven

### Steps

```bash
# Start MongoDB
docker run -d --name mongo-local -p 27017:27017 mongo:7

# Run the service
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The gRPC server starts on port `9090`, HTTP actuator on port `8080`.

### Verify

```bash
curl http://localhost:8080/actuator/health
```



