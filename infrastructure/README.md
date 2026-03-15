# infrastructure

This folder contains the **infrastructure components required by the GreenGrub microservices**.
These services provide messaging, caching, and observability support for the application.

## Components

| Component  | Purpose                                              |
| ---------- | ---------------------------------------------------- |
| Kafka      | Message broker used for event-driven communication   |
| Redis      | Caching and rate limiting                            |
| Monitoring | Prometheus and Grafana for metrics and observability |

## Structure

```
infrastructure
│
├── kafka
│   ├── deployment.yaml
│   └── service.yaml
│
├── redis
│   ├── deployment.yaml
│   └── service.yaml
│
└── monitoring
    ├── prometheus.yaml
    └── grafana.yaml
```

## Usage

These resources are deployed to Kubernetes before deploying the application services.

Example:

```
kubectl apply -f infrastructure/
```

Once deployed, services inside the cluster can connect using:

```
kafka:9092
redis:6379
```
