# Notification Service — Local Testing Guide

## Prerequisites

- Java 21
- Docker (for MongoDB and Kafka)
- Maven wrapper (`./mvnw`)

---

## 1. Start Infrastructure

### MongoDB

```bash
docker run -d --name mongodb -p 27017:27017 mongo:latest
```

### Kafka (with Zookeeper)

```bash
# Using docker-compose or individual containers:
docker run -d --name zookeeper -p 2181:2181 confluentinc/cp-zookeeper:latest \
  -e ZOOKEEPER_CLIENT_PORT=2181

docker run -d --name kafka -p 9092:9092 \
  --link zookeeper \
  -e KAFKA_BROKER_ID=1 \
  -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  confluentinc/cp-kafka:latest
```

### Create Kafka Topic

```bash
docker exec kafka kafka-topics --create \
  --topic donation-topic \
  --bootstrap-server localhost:9092 \
  --partitions 1 \
  --replication-factor 1
```

---

## 2. Start the Application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Verify startup logs show:
- `Kafka consumer started` (or similar listener registration)
- `Connected to MongoDB` on port 27017
- Application running on port 8084

---

## 3. Test Kafka Consumer Flow (Happy Path)

### Produce a Test Message

```bash
docker exec -it kafka kafka-console-producer \
  --topic donation-topic \
  --bootstrap-server localhost:9092 \
  --property "parse.key=true" \
  --property "key.separator=:" \
  --property "value.serializer=org.apache.kafka.common.serialization.StringSerializer"
```

Then paste this message (key:value format):

```
key1:{"donationId":"D101","donorName":"John Doe","donorEmail":"john@gmail.com","totalAmount":25,"createdAt":"2026-05-16T10:30:00","customer":{"id":"C1","firstname":"John","lastname":"Doe","email":"john@gmail.com","phone":"9999999999"},"organizationName":"Helping Hands NGO","items":[{"foodName":"Rice","quantity":10,"unit":"KG","category":"Food"}]}
```

> **Note:** If your Kafka consumer uses `spring.json.type.mapping`, you need to include the type header. Use the programmatic producer below instead for proper header support.

### Programmatic Test Producer (Recommended)

Create a temporary test class or use a REST endpoint to publish:

```java
// Add to a test class or temporary CommandLineRunner
@Autowired
private KafkaTemplate<String, Donation> kafkaTemplate;

Donation testDonation = new Donation(
    "D101",
    "John Doe",
    "john@gmail.com",
    new BigDecimal("25"),
    LocalDateTime.now(),
    new Customer("C1", "John", "Doe", "john@gmail.com", "9999999999"),
    "Helping Hands NGO",
    List.of(new DonatedItem("Rice", 10, "KG", "Food"))
);

kafkaTemplate.send("donation-topic", "D101", testDonation);
```

### Expected Result

1. Console log: `Received donation event -> ID: D101, Donor: John Doe, Items: 1`
2. Notification saved to MongoDB with status `PENDING` then updated to `SENT` (or `FAILED` if mail is not configured)
3. If mail credentials are set, email sent to `john@gmail.com`

### Verify in MongoDB

```bash
docker exec -it mongodb mongosh
```

```javascript
use greengrub_notifications
db.notifications.find().pretty()
```

Expected document:

```json
{
  "_id": ObjectId("..."),
  "donationId": "D101",
  "eventType": "ACTIVE",
  "status": "SENT",
  "recipient": "john@gmail.com",
  "payload": "{\"donationId\":\"D101\",...}",
  "failureReason": null,
  "notificationTime": ISODate("..."),
  "sentAt": ISODate("..."),
  "createdAt": ISODate("..."),
  "updatedAt": ISODate("...")
}
```

---

## 4. Test Notification History API

```bash
# Get notifications by donation ID
curl http://localhost:8084/api/v1/notifications/donation/D101

# Get notifications by recipient email
curl http://localhost:8084/api/v1/notifications/recipient/john@gmail.com
```

---

## 5. Test Email Failure Scenario

### Without Mail Credentials

Leave `spring.mail.username` and `spring.mail.password` empty in `application-local.yaml`. Send a Kafka message — the notification should:
- Be saved with status `PENDING`
- Attempt email send → fail
- Update status to `FAILED` with `failureReason` populated

### Verify

```javascript
db.notifications.find({ status: "FAILED" }).pretty()
// Should show failureReason like "Authentication failed" or "Mail server connection failed"
```

---

## 6. Test Resilience4j — Retry

The `@Retry(name = "notificationRetry")` annotation retries the `processNotification` method on failure.

### How to Trigger

1. **Stop MongoDB** while the app is running:
   ```bash
   docker stop mongodb
   ```

2. **Send a Kafka message** — the service will attempt to save and fail.

3. **Check logs** for retry attempts:
   ```
   Retry 'notificationRetry', waiting 500ms before attempt #2
   Retry 'notificationRetry', waiting 1000ms before attempt #3
   ```

4. **Restart MongoDB** before the last retry to see recovery:
   ```bash
   docker start mongodb
   ```

### Configuration (from application-local.yaml)

```yaml
resilience4j:
  retry:
    instances:
      notificationRetry:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
```

- Attempt 1: immediate
- Attempt 2: after 500ms
- Attempt 3: after 1000ms (500 * 2)

---

## 7. Test Resilience4j — Circuit Breaker

The `@CircuitBreaker(name = "notificationBreaker")` protects against cascading failures.

### How to Trigger

1. **Ensure mail is misconfigured** (wrong host or credentials).

2. **Send 10+ Kafka messages rapidly** (to fill the sliding window):
   ```bash
   for i in {1..12}; do
     docker exec kafka kafka-console-producer \
       --topic donation-topic \
       --bootstrap-server localhost:9092 <<< \
       "{\"donationId\":\"D$i\",\"donorName\":\"Test\",\"donorEmail\":\"test@test.com\",\"totalAmount\":10,\"createdAt\":\"2026-05-16T10:30:00\",\"customer\":{\"id\":\"C1\",\"firstname\":\"Test\",\"lastname\":\"User\",\"email\":\"test@test.com\",\"phone\":\"0000000000\"},\"organizationName\":\"Test Org\",\"items\":[{\"foodName\":\"Rice\",\"quantity\":1,\"unit\":\"KG\",\"category\":\"Food\"}]}"
   done
   ```

3. **Observe logs** — after failure rate exceeds 50% within the sliding window of 10 calls:
   ```
   CircuitBreaker 'notificationBreaker' is OPEN and does not permit further calls
   ```

4. **Wait 30 seconds** (wait-duration-in-open-state), then the circuit moves to HALF_OPEN:
   ```
   CircuitBreaker 'notificationBreaker' changed state from OPEN to HALF_OPEN
   ```

5. **Fix the mail config and restart** — next 3 calls (permitted-number-of-calls-in-half-open-state) will determine if circuit closes.

### Configuration

```yaml
resilience4j:
  circuitbreaker:
    instances:
      notificationBreaker:
        failure-rate-threshold: 50        # open when 50% of calls fail
        sliding-window-size: 10           # evaluate over last 10 calls
        wait-duration-in-open-state: 30s  # stay open for 30s before half-open
        permitted-number-of-calls-in-half-open-state: 3
        register-health-indicator: true   # exposed via /actuator/health
```

### Check Circuit Breaker Health via Actuator

```bash
curl http://localhost:8084/actuator/health | jq '.components.circuitBreakers'
```

Response when OPEN:

```json
{
  "status": "DOWN",
  "details": {
    "notificationBreaker": {
      "status": "CIRCUIT_OPEN",
      "details": {
        "failureRate": "60.0%",
        "state": "OPEN"
      }
    }
  }
}
```

> **Note:** You may need to expose circuit breaker health in config:
> ```yaml
> management:
>   health:
>     circuitbreakers:
>       enabled: true
>   endpoints:
>     web:
>       exposure:
>         include: health,info
> ```

---

## 8. Test Exception Handling

### NotificationProcessingException

Triggered when JSON serialization fails (unlikely with valid DTOs) or when an unrecoverable error occurs during processing. Check logs for:

```
ERROR c.g.n.service.MongoNotificationService - Email send failed for donationId: D101
```

### NotificationNotFoundException

Triggered when querying a non-existent notification (future use). Test:

```bash
curl http://localhost:8084/api/v1/notifications/donation/NON_EXISTENT_ID
# Returns: [] (empty list, no exception for list queries)
```

---

## 9. Test Kafka Deserialization Failure

Send a malformed JSON message:

```bash
docker exec kafka kafka-console-producer \
  --topic donation-topic \
  --bootstrap-server localhost:9092 <<< "this is not json"
```

Expected: Spring Kafka logs a deserialization error. The consumer does NOT crash — it skips the bad message (default error handler behavior).

---

## 10. End-to-End Checklist

| # | Test Case | Expected Status | How to Verify |
|---|-----------|----------------|---------------|
| 1 | Valid event + mail configured | `SENT` | MongoDB doc + email received |
| 2 | Valid event + mail NOT configured | `FAILED` | MongoDB doc with `failureReason` |
| 3 | MongoDB down during processing | Retry 3x then fail | Logs show retry attempts |
| 4 | 10+ consecutive failures | Circuit OPEN | Actuator health endpoint |
| 5 | Circuit OPEN → wait 30s | HALF_OPEN | Logs + actuator |
| 6 | Fix issue during HALF_OPEN | Circuit CLOSED | 3 successful calls close it |
| 7 | Malformed Kafka message | Skipped | Error log, no crash |
| 8 | GET notifications by donationId | 200 OK + list | curl response |
| 9 | GET notifications by recipient | 200 OK + list | curl response |

---

## Cleanup

```bash
docker stop mongodb kafka zookeeper
docker rm mongodb kafka zookeeper
```
