# GreenGrub

GreenGrub is a **microservice-based backend platform** for managing food donations and food requests.
The system is built using **Spring Boot**, **gRPC**, **Kafka**, and **Kubernetes**, and follows a **cloud-native architecture** where services are independently deployable and scalable.

---

## Architecture Overview

GreenGrub is composed of multiple independent services communicating through **REST, gRPC, and event-driven messaging**.

### Core Components

| Service              | Description                                                         |
| -------------------- | ------------------------------------------------------------------- |
| API Gateway          | Entry point for client requests, handles authentication and routing |
| Config Server        | Centralized configuration management                                |
| User Service         | Manages user registration, login, and profiles                      |
| Donation Service     | Handles donation creation and management                            |
| Food Request Service | Handles food request creation and tracking                          |
| Notification Service | Consumes events and sends notifications                             |
| Proto Contracts      | Shared gRPC contract definitions                                    |

### Supporting Infrastructure

* Kafka – asynchronous event communication
* Redis – caching and rate limiting
* PostgreSQL – service databases
* Kubernetes (k3d / GKE) – deployment and orchestration
* Prometheus & Grafana – monitoring and metrics

---

## Communication Pattern

| Communication     | Technology |
| ----------------- | ---------- |
| Client → Gateway  | REST       |
| Service → Service | gRPC       |
| Async Events      | Kafka      |
| Caching           | Redis      |

---

## Project Structure

```
greengrub
│
├── proto-contracts
├── services
│   ├── api-gateway
│   ├── config-server
│   ├── user-service
│   ├── donation-service
│   ├── food-request-service
│   └── notification-service
│
├── infrastructure
│   ├── kafka
│   └── redis
│
└── k8s
    ├── deployments
    └── services
```

---

## Running a Service Locally

Each service can run independently using a **local profile**.

Example:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

This disables external dependencies such as Config Server and Service Discovery so developers can work on a service in isolation.

---

## Deployment

The application is designed to run on **Kubernetes** (k3d locally or GKE in cloud).

Typical deployment steps:

```
kubectl apply -f k8s/namespace.yaml
kubectl apply -f infrastructure/
kubectl apply -f k8s/
```

---

## Key Features

* Microservice architecture
* gRPC-based internal communication
* Event-driven design using Kafka
* Centralized configuration
* Kubernetes-ready deployment
* Independent service development

---

## Purpose

This project demonstrates a **production-style microservice architecture** suitable for scalable backend systems and cloud deployment.
