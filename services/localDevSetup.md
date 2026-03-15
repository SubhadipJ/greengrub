# Local Development Setup

This guide explains how to run the **GreenGrub infrastructure locally** so developers can run individual microservices directly from their IDE without requiring the full Kubernetes environment.

The local environment provides the required infrastructure containers:

* PostgreSQL
* MongoDB
* Kafka
* MailDev
* Developer UIs for database and messaging inspection

All **Spring Boot services run locally from the IDE**, while infrastructure runs inside Docker.

---

# Prerequisites

Ensure the following tools are installed:

* Docker
* Docker Compose
* **JDK 21+**
* Maven

---

# Infrastructure Network

All infrastructure containers run inside a dedicated **Docker bridge network**:

```
microservice-net
```

This network allows containers to communicate with each other using their **container names as hostnames**.

Example:

```
greengrub-postgres
greengrub-mongo
greengrub-kafka
greengrub-maildev
```

The network is defined in `docker-compose.local.yml`:

```yaml
networks:
  microservice-net:
    driver: bridge
```

Each container joins this network using:

```yaml
networks:
  - microservice-net
```

---

# Start Local Infrastructure

Navigate to the `services` directory and start the infrastructure:

```
docker compose -f docker-compose.local.yml up
```

This will start:

* PostgreSQL
* MongoDB
* Kafka
* MailDev
* Adminer (Postgres UI)
* Mongo Express
* Kafka UI

---

# Available Developer Tools

Once the containers start, the following tools are available:

| Service               | URL                   | Purpose                           |
| --------------------- | --------------------- | --------------------------------- |
| MailDev               | http://localhost:1080 | View outgoing emails              |
| Kafka UI              | http://localhost:8090 | Inspect Kafka topics and messages |
| Adminer (Postgres UI) | http://localhost:8088 | Inspect PostgreSQL tables         |
| Mongo Express         | http://localhost:8089 | Inspect MongoDB collections       |

---

# Database Usage by Services

| Service              | Database   |
| -------------------- | ---------- |
| Donation Service     | PostgreSQL |
| Food Request Service | PostgreSQL |
| Customer Service     | MongoDB    |

---

# PostgreSQL Configuration

PostgreSQL is used by:

* **donation-service**
* **food-request-service**

Connection details:

```
Host: localhost
Port: 5432
Database: greengrub
Username: postgres
Password: postgres
```

Spring configuration example:

```
spring.datasource.url=jdbc:postgresql://localhost:5432/greengrub
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

---

# MongoDB Configuration

MongoDB is used by:

* **customer-service**

Connection string:

```
mongodb://localhost:27017
```

Spring configuration example:

```
spring.data.mongodb.uri=mongodb://localhost:27017/customerdb
```

---

# Kafka Configuration

Kafka runs inside Docker and exposes port **9092**.

Spring configuration:

```
spring.kafka.bootstrap-servers=localhost:9092
```

Kafka is used for event-driven communication such as:

```
Donation Service → donation-created topic → Notification Service
```

Kafka topics can be inspected using:

```
http://localhost:8090
```

---

# MailDev (Email Testing)

MailDev captures outgoing emails for development testing.

Spring configuration:

```
spring.mail.host=localhost
spring.mail.port=1025
```

Emails can be viewed at:

```
http://localhost:1080
```

---

# Running Microservices Locally

Each microservice should run using the **local Spring profile**.

Example:

```
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Or configure in your IDE:

```
Environment Variable:
SPRING_PROFILES_ACTIVE=local
```

---

# Disable Cloud Dependencies in Local Profile

Inside `application-local.properties`:

```
spring.cloud.config.enabled=false
spring.config.import=optional:configserver:
eureka.client.enabled=false
```

This disables:

* Config Server
* Service Discovery
* Kubernetes dependencies

---

# Local Development Architecture

```
Spring Boot Services (IDE)
        |
        | localhost connections
        |
------------------------------------------------
| Postgres | MongoDB | Kafka | MailDev | UI |
------------------------------------------------
           Docker (microservice-net)
```

---

# Stopping Infrastructure

To stop all containers:

```
docker compose -f docker-compose.local.yml down
```

---

# Benefits of This Setup

* Fast local development
* Easy debugging with IDE breakpoints
* No need to build service containers
* Shared infrastructure for all services
* Visual inspection tools for Kafka and databases
* Isolated Docker network for infrastructure services

This setup allows developers to **focus on individual services while still interacting with real infrastructure components locally**.
