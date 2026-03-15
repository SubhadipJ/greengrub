# scripts

This folder contains helper scripts used to **build Docker images and deploy services to Kubernetes**.

These scripts simplify development and deployment workflows.

## Structure

```
scripts
│
├── build-images.sh
└── deploy-k8s.sh
```

## build-images.sh

Builds Docker images for all services.

Example usage:

```
./scripts/build-images.sh
```

This script builds images for:

* API Gateway
* Config Server
* User Service
* Donation Service
* Food Request Service
* Notification Service

## deploy-k8s.sh

Deploys the application to the Kubernetes cluster.

Example usage:

```
./scripts/deploy-k8s.sh
```

This script will:

1. Create the project namespace
2. Deploy infrastructure components
3. Deploy application services

These scripts are mainly intended for **local development and testing with k3d**.
