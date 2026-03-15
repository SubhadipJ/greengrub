# k8s

This folder contains the **Kubernetes manifests used to deploy the GreenGrub microservices**.

Each service has its own **deployment and service configuration** so that it can be scaled and managed independently.

## Structure

```
k8s
│
├── namespace.yaml
│
├── api-gateway
│   ├── deployment.yaml
│   └── service.yaml
│
├── config-server
│   ├── deployment.yaml
│   └── service.yaml
│
├── user-service
│   ├── deployment.yaml
│   └── service.yaml
│
├── donation-service
│   ├── deployment.yaml
│   └── service.yaml
│
├── food-request-service
│   ├── deployment.yaml
│   └── service.yaml
│
└── notification-service
    ├── deployment.yaml
    └── service.yaml
```

## Deployment Steps

1. Create namespace

```
kubectl apply -f k8s/namespace.yaml
```

2. Deploy infrastructure

```
kubectl apply -f infrastructure/
```

3. Deploy services

```
kubectl apply -f k8s/
```

Kubernetes automatically handles:

* service discovery
* load balancing
* scaling
