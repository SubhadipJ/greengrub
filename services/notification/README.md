# Notification Service

An event-driven microservice that consumes Kafka events from the Donation Service and sends email notifications. Notification payloads are stored as JSON snapshots for audit, retry, and event replay. Metadata is persisted in **Google Cloud Firestore** (k8s) or **MongoDB** (local development).

---

## Project Structure

```
src/main/java/com/greengrub/notification/
├── config/
│   └── KafkaConfig.java             # Kafka consumer configuration
├── consumer/
│   └── NotificationConsumer.java    # Kafka listener — delegates to service layer
├── controller/
│   └── NotificationController.java  # REST API for notification history
├── dto/
│   ├── Customer.java                # Customer record from Donation Service
│   ├── DonatedItem.java             # Individual donated item
│   └── Donation.java                # Full donation event payload
├── email/
│   ├── EmailService.java            # SMTP email sender (Thymeleaf templates)
│   └── EmailTemplate.java           # Template enum (DONATION_CONFIRMATION, etc.)
├── entity/
│   ├── NotificationDocument.java    # MongoDB entity (local profile)
│   └── NotificationEntity.java      # Firestore entity (k8s profile)
├── enums/
│   ├── DonationStatus.java          # ACTIVE, CLAIMED, CANCELLED
│   └── NotificationStatus.java      # PENDING, SENT, FAILED
├── exception/
│   ├── NotificationNotFoundException.java
│   └── NotificationProcessingException.java
├── mapper/
│   └── NotificationMapper.java      # DTO → Entity + JSON payload serialization
├── repository/
│   ├── NotificationFirestoreRepository.java  # FirestoreReactiveRepository (k8s)
│   └── NotificationMongoRepository.java      # MongoRepository (local)
├── service/
│   ├── NotificationService.java              # Interface
│   ├── MongoNotificationService.java         # Implementation (local)
│   └── FirestoreNotificationService.java     # Implementation (k8s)
└── NotificationApplication.java
```

---

## Architecture Overview

```
Donation Service
    │
    ▼ (Kafka: donation-topic)
┌─────────────────────────────────────────────┐
│  NotificationConsumer                       │  ← Kafka listener, validates event
├─────────────────────────────────────────────┤
│  MongoNotificationService (local)           │  ← Business logic + resilience wrappers
│  FirestoreNotificationService (k8s)         │
├─────────────────────────────────────────────┤
│  NotificationMapper                         │  ← DTO → Entity, payload → JSON
├─────────────────────────────────────────────┤
│  MongoRepository (local)                    │  ← Notification persistence
│  FirestoreReactiveRepository (k8s)          │
├─────────────────────────────────────────────┤
│  EmailService                               │  ← SMTP send via Thymeleaf templates
└─────────────────────────────────────────────┘
```

---

## Things to Know Before Contributing

1. **Two parallel implementations** — Every feature must work in both `local` (MongoDB) and `k8s` (Firestore) profiles. The `@Profile` annotation controls which beans are active.

2. **Payload is stored as JSON string** — The full Kafka event is serialized via Jackson and stored in the `payload` field. This provides audit history, retry capability, and event replay without relational coupling to the Donation service.

3. **EmailService is synchronous** — No `@Async`. The service layer needs to know whether sending succeeded or failed to update notification status accordingly.

4. **Resilience annotations go on service methods** — `@Retry` and `@CircuitBreaker` are applied to `processNotification()`. Don't add them to repository or email calls directly.

5. **DonationStatus is reused as event type** — Instead of a separate `NotificationEventType` enum, we reuse `DonationStatus` (ACTIVE, CLAIMED, CANCELLED) from the Donation domain for simpler Kafka event mapping.

6. **Only EMAIL channel is supported** — No `NotificationChannel` enum exists. It can be introduced later if SMS, push, or WhatsApp is needed.

7. **FirestoreReactiveRepository uses `.block()`** — The k8s service calls `.block()` on reactive returns to stay synchronous. This is intentional — Resilience4j needs synchronous methods.

8. **Kafka type mapping** — The consumer uses `spring.json.type.mapping` to map the producer's type header to the local `Donation` DTO. The mapping key must match what the Donation Service producer publishes.

---

## Profiles

| Profile | Database | Communication | Use Case |
|---------|----------|---------------|----------|
| `local` | MongoDB (Docker) | Kafka (localhost) | Local development |
| `k8s` | Google Cloud Firestore | Kafka (cluster) | GKE deployment |

---

## Key Config Files

| File | Purpose |
|------|---------|
| `application-local.yaml` | Local profile — MongoDB, Kafka (localhost:9092), mail, resilience thresholds |
| `application-k8s.yaml` | K8s profile — Firestore, Kafka (env vars), production resilience thresholds |

---

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/notifications/donation/{donationId}` | Get all notifications for a donation |
| `GET` | `/api/v1/notifications/recipient/{email}` | Get all notifications for a recipient |

---

## Kafka Consumer

| Topic | Group ID | Event Type | Action |
|-------|----------|-----------|--------|
| `donation-topic` | `notificationGroup` | Donation event | Persist notification → send email → update status |

### Consumer Flow

```
Kafka Message (donation-topic)
    │
    ▼
NotificationConsumer.handleDonationEvent()
    │
    ▼
NotificationService.processNotification()
    │
    ├── 1. Map DTO → Entity (status: PENDING)
    ├── 2. Serialize payload to JSON
    ├── 3. Persist to DB
    ├── 4. Send email via EmailService
    ├── 5. Update status → SENT or FAILED
    └── 6. Persist updated entity
```

### Kafka Deserialization Config

```yaml
spring:
  kafka:
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: '*'
        spring.json.type.mapping: donationEvent:com.greengrub.notification.dto.Donation
```

---

## Resilience Patterns

We use **Resilience4j** to protect the service from cascading failures.

### Retry

Automatically retries failed operations caused by transient infrastructure issues.

| Instance | Applied To | Config |
|----------|-----------|--------|
| `notificationRetry` | `processNotification()` | 3 attempts, exponential backoff (500ms → 1s → 2s) |

### Circuit Breaker

Stops processing when failure rate exceeds threshold, letting dependencies recover.

| Instance | Applied To | Config |
|----------|-----------|--------|
| `notificationBreaker` | `processNotification()` | Opens at 50% failure rate in 10-call window, 30s recovery wait |

**States:** `CLOSED` (normal) → `OPEN` (fail-fast) → `HALF_OPEN` (probing recovery)

When open, all notification processing fails immediately — no DB calls, no email attempts.

---

## Exception Handling

### Exception Hierarchy

```
RuntimeException
├── NotificationProcessingException  →  Unrecoverable processing failure
└── NotificationNotFoundException   →  Notification ID not found (future use)
```

### Where Each Exception is Thrown

| Exception | Thrown When | Retryable? |
|-----------|------------|------------|
| `NotificationProcessingException` | JSON serialization fails or unrecoverable error | No |
| `NotificationNotFoundException` | Query for non-existent notification ID | No |
| `MessagingException` (Jakarta Mail) | SMTP send failure | Yes (via service retry) |

---

## Running Locally

### Prerequisites

- Java 21
- Docker Desktop (for MongoDB and Kafka)
- Maven

### Steps

```bash
# Start MongoDB
docker run -d --name mongodb -p 27017:27017 mongo:latest

# Start Kafka (with Zookeeper)
docker run -d --name zookeeper -p 2181:2181 \
  -e ZOOKEEPER_CLIENT_PORT=2181 \
  confluentinc/cp-zookeeper:latest

docker run -d --name kafka -p 9092:9092 \
  --link zookeeper \
  -e KAFKA_BROKER_ID=1 \
  -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  confluentinc/cp-kafka:latest

# Create topic
docker exec kafka kafka-topics --create \
  --topic donation-topic \
  --bootstrap-server localhost:9092 \
  --partitions 1 --replication-factor 1

# Run the service
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The service starts on port `8084`.

### Verify

```bash
curl http://localhost:8084/actuator/health
```

---

## Email Templates

Templates are rendered using **Thymeleaf** and located in `src/main/resources/templates/`.

| Template | File | Used For |
|----------|------|----------|
| Donation Confirmation | `donation.html` | Thank-you email after donation is created |

### Template Variables

| Variable | Source |
|----------|--------|
| `donorName` | `customer.firstname + customer.lastname` |
| `organizationName` | `donation.organizationName` |
| `donationId` | `donation.donationId` |
| `donationDate` | `donation.createdAt` (formatted) |
| `totalAmount` | `donation.totalAmount` |
| `items` | `donation.items` (list of DonatedItem) |

---

## Future Improvements

- [ ] Dead Letter Queue (DLQ) for failed Kafka messages (`notification-dlq-topic`)
- [ ] Additional email templates (Donation Claimed, Donation Cancelled, NGO Notification)
- [ ] Retry scheduler for FAILED notifications
- [ ] SMS / Push Notification channel support
- [ ] Notification preferences per user
