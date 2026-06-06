# Local Development Setup

This guide explains how to run **GreenGrub** locally so developers can work on individual microservices from their IDE without requiring the full Kubernetes environment.

The local stack uses **Docker Compose** for all infrastructure (databases, messaging, observability) while **Spring Boot services run directly from your IDE** with the `local` Spring profile active.

---

## Project Overview

GreenGrub is a food donation platform built as a microservices application.

| Layer | Technology |
|---|---|
| API Gateway | Spring Cloud Gateway WebMVC 5.0.0 |
| Services | Spring Boot 3.x (Java 21) |
| Inter-service communication | gRPC |
| Async events | Apache Kafka |
| Databases | PostgreSQL (donations, food, customer) + MongoDB (customer) |
| Caching | Redis |
| Image storage | Google Cloud Storage |
| Email | MailDev (local) / Gmail SMTP (production) |
| Observability | Prometheus + Grafana + ELK (Elasticsearch, Logstash, Kibana) |
| Frontend | React + Vite |

### Service Locations

| Service | Path |
|---|---|
| Gateway | `services/gateway` |
| Image Service | `services/image-service` |
| Notification Service | `services/notification` |
| Customer Service | `GreenGrubUserManagementService/` |
| Donation Service | `Greengrub-donation-service/` |
| Food Service | `greengrub-food-request/` |
| Frontend (React) | `Greengrub-ui-V2/` |

---

## Prerequisites

Ensure the following tools are installed before starting:

- **Docker** + **Docker Compose**
- **JDK 21+**
- **Maven 3.9+**
- **Node.js 18+** (for the React frontend)

---

## Infrastructure (Docker Compose)

All infrastructure runs via a single Docker Compose file located at:

```
services/docker-compose.yml
```

The file also requires two config files in the same directory:
- `services/logstash.conf` — Logstash pipeline config
- `services/prometheus.yml` — Prometheus scrape config

### Start all infrastructure

```bash
cd services/
docker compose up -d
```

### Stop all infrastructure

```bash
docker compose down
```

---

## Infrastructure Services

All containers run on a shared Docker bridge network called `microservice-net`. Containers can reach each other by container name.

### Core Services

| Container | Port | Purpose |
|---|---|---|
| `greengrub-postgres` | 5432 | PostgreSQL — donations, food, customer data |
| `greengrub-mongo` | 27017 | MongoDB — customer service |
| `greengrub-kafka` | 9002 | Kafka broker (KRaft mode, no Zookeeper) |
| `greengrub-redis` | 6379 | Redis — rate limiting + session cache in gateway |
| `greengrub-maildev` | 1025 (SMTP) | Mail server — captures outgoing emails |

### Developer UI Tools

| Tool | URL | Purpose |
|---|---|---|
| MailDev | http://localhost:1080 | View emails sent by notification service |
| Kafka UI | http://localhost:8890 | Inspect Kafka topics and messages |
| Adminer | http://localhost:8088 | PostgreSQL browser |
| Mongo Express | http://localhost:8089 | MongoDB browser |

### Observability (optional for local dev)

| Tool | URL | Purpose |
|---|---|---|
| Prometheus | http://localhost:9090 | Metrics collection |
| Grafana | http://localhost:3001 | Dashboards (admin / admin) |
| Kibana | http://localhost:5601 | Log viewer |
| Elasticsearch | http://localhost:9200 | Log storage |

> Prometheus and ELK are included in docker-compose.yml but are optional for day-to-day development. You can comment them out to save memory if you don't need observability locally.

---

## Database Configuration

### PostgreSQL

Used by: **customer-service**, **donation-service**, **food-service**

```
Host:     localhost
Port:     5432
Database: greengrub
Username: postgres
Password: postgres
```

Spring Boot `application-local.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/greengrub
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update
```

### MongoDB

Used by: **customer-service**

```
URI:      mongodb://localhost:27017
Username: admin
Password: password
```

Spring Boot `application-local.properties`:
```properties
spring.data.mongodb.uri=mongodb://admin:password@localhost:27017/customerdb?authSource=admin
```

---

## Kafka Configuration

Kafka runs in **KRaft mode** (no Zookeeper) and exposes port **9002** locally.

```
Bootstrap servers: localhost:9002
```

Spring Boot `application-local.properties`:
```properties
spring.kafka.bootstrap-servers=localhost:9002
```

### Event flow

```
Donation Service → topic: donation-created → Notification Service
```

Inspect topics and messages at: http://localhost:8890

---

## Redis Configuration

Used by: **gateway** (rate limiting + token caching)

```
Host: localhost
Port: 6379
```

Spring Boot `application-local.properties` (gateway):
```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

---

## Email Configuration (MailDev)

MailDev captures all outgoing emails locally — nothing is actually sent.

```
SMTP Host: localhost
SMTP Port: 1025
```

Spring Boot `application-local.yaml` (notification service):
```yaml
spring:
  mail:
    host: localhost
    port: 1025
```

View captured emails at: http://localhost:1080

---

## Service Ports

Each service listens on a fixed port locally. Start them in this order (gateway last):

| Service | HTTP Port | gRPC Port | Spring Profile |
|---|---|---|---|
| food-service | 8081 | 9091 | `local` |
| customer-service | 8082 | 9092 | `local` |
| donation-service | 8083 | 9093 | `local` |
| notification-service | 8084 | — | `local` |
| image-service | 8085 | 9095 | `local` |
| **gateway** | **8080** | — | `local` |

> **Start gateway last** — it routes to all other services so they must be running first.

### gRPC inter-service calls (local)

| Caller | Calls | Address |
|---|---|---|
| customer-service | image-service | `localhost:9095` |
| customer-service | donation-service | `localhost:9093` |
| food-service | image-service | `localhost:9095` |

---

## Running Services

### 1. Set the Spring profile

Every service must run with `SPRING_PROFILES_ACTIVE=local`.

**Maven:**
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

**IntelliJ IDEA:**
- Run/Debug Configurations → Environment Variables → add `SPRING_PROFILES_ACTIVE=local`

**VS Code** (`launch.json`):
```json
"env": { "SPRING_PROFILES_ACTIVE": "local" }
```

### 2. Local profile behaviour

The `application-local` profile automatically:
- Points to localhost databases/Kafka/Redis
- Disables Kubernetes service discovery (`eureka.client.enabled=false`)
- Uses MailDev instead of Gmail
- Sets a default JWT secret so no env var is needed

---

## Running the Frontend (React)

The React UI is located at `Greengrub-ui-V2/`.

```bash
cd Greengrub-ui-V2/
npm install
npm run dev
```

The UI runs at: http://localhost:5173

### API base URL

The frontend reads `VITE_API_BASE_URL` from the environment:

| File | Value | Use |
|---|---|---|
| `.env.local` | `http://localhost:8080` | Local dev — hits gateway directly |
| `.env.production` | `http://<ingress-ip>` | Production build |

For local development the UI talks to the gateway at `localhost:8080` which proxies all requests to the appropriate service.

---

## Local Architecture

```
Browser
  └── http://localhost:5173  (React + Vite)
         │
         │  VITE_API_BASE_URL = http://localhost:8080
         ▼
    Gateway :8080  (Spring Cloud Gateway WebMVC)
         │
         ├── /api/v1/auth/**  /api/v1/users/**  ──► Customer Service :8082
         │                                               │
         │                                               ├─ gRPC ──► Image Service :9095
         │                                               └─ gRPC ──► Donation Service :9093
         │
         ├── /api/v1/donations/**  ────────────────► Donation Service :8083
         │                                               │
         │                                               └─ Kafka ──► Notification Service :8084
         │
         ├── /api/v1/food-requests/** ───────────► Food Service :8081
         │                                               │
         │                                               └─ gRPC ──► Image Service :9095
         │
         └── /api/v1/images/**  ─────────────────► Image Service :8085


Infrastructure (Docker)
  ├── PostgreSQL  :5432
  ├── MongoDB     :27017
  ├── Kafka       :9002
  ├── Redis       :6379
  └── MailDev     :1025 / :1080
```

---

## Seed Data

A seed script is available to populate the database with test data:

```bash
# Make sure all services are running first
chmod +x seed-greengrub.sh
./seed-greengrub.sh
```

Default admin credentials created by the seed script:
```
Email:    admin@greengrub.com
Password: Admin@2024
```

The script creates:
- 1 admin user
- 4 food requests
- 3 donations

---

## CORS

CORS is handled **only at the gateway** — downstream services do not set CORS headers.

For local development the gateway allows requests from `http://localhost:5173` by default (set via `CORS_ALLOWED_ORIGINS` env var, defaulting to `http://localhost:5173` in the local profile).

---

## Common Issues

| Problem | Cause | Fix |
|---|---|---|
| `Connection refused` on startup | Infrastructure not running | Run `docker compose up -d` first |
| `SPRING_PROFILES_ACTIVE` not set | Wrong profile loaded | Set env var before running service |
| Kafka consumer not receiving events | Kafka port mismatch | Use port `9002` locally (not 9092) |
| MongoDB auth failure | Missing auth params | Use `mongodb://admin:password@localhost:27017/customerdb?authSource=admin` |
| Gateway returns 403 on preflight | CORS origin mismatch | Check `CORS_ALLOWED_ORIGINS` — must match the exact origin including port |
| Emails not appearing | MailDev not running | Check http://localhost:1080 and verify SMTP port is 1025 |
