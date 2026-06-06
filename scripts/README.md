# GreenGrub — Kubernetes Deployment Scripts

This directory contains all Kubernetes manifests, Helm values files, and shell scripts needed to deploy the GreenGrub food donation platform to a self-managed kubeadm cluster on GCP.

---

## Cluster Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        GCP — asia-south1-c                      │
│                                                                 │
│  ┌──────────────────┐  ┌───────────────────┐  ┌──────────────┐  │
│  │  k8s-control-    │  │  k8s-worker-node  │  │ k8s-elk-node │  │
│  │  plane           │  │  (e2-medium)      │  │ (e2-medium)  │  │
│  │  (e2-medium)     │  │  2 vCPU / 4 GB    │  │ 2 vCPU / 4GB │  │
│  │  2 vCPU / 4 GB   │  │                   │  │              │  │
│  │                  │  │  namespaces:      │  │ namespaces:  │  │
│  │  Kubernetes API  │  │  • services       │  │ • logging    │  │
│  │  etcd            │  │  • utility        │  │   (ELK+FB)   │  │
│  │  scheduler       │  │  • monitoring     │  │              │  │
│  │  controller-mgr  │  │  • ingress-nginx  │  │              │  │
│  └──────────────────┘  └───────────────────┘  └──────────────┘  │
│                                                                 │
│  External IPs:                                                  │
│    control-plane : 8.231.105.8                                  │
│    worker-node   : 34.93.217.208   (Prometheus :30090,          │
│                                     Grafana    :30300)          │
│    elk-node      : 35.200.214.132  (Kibana     :30601,          │
│                                     Elasticsearch :30920)       │
└─────────────────────────────────────────────────────────────────┘
```

### Namespaces

| Namespace | Node | Contents |
|---|---|---|
| `services` | k8s-worker-node | gateway, customer, donation, food, image, notification |
| `utility` | k8s-worker-node | Kafka, Redis |
| `monitoring` | k8s-worker-node | Prometheus, Grafana, AlertManager |
| `ingress-nginx` | k8s-worker-node | nginx ingress controller |
| `logging` | k8s-elk-node | Elasticsearch, Logstash, Kibana, Fluent Bit |

### Services and Ports

| Service | Internal Port | NodePort | Node |
|---|---|---|---|
| Gateway | 8080 | via ingress | worker |
| Customer Service | 8082 | — | worker |
| Donation Service | 8083 | — | worker |
| Food Service | 8081 | — | worker |
| Image Service | 8085 | — | worker |
| Notification Service | 8084 | — | worker |
| Prometheus | 9090 | 30090 | worker |
| Grafana | 3000 | 30300 | worker |
| Elasticsearch | 9200 | 30920 | elk |
| Kibana | 5601 | 30601 | elk |
| Kubernetes Dashboard | 8443 | 8551 (HTTPS) | any node |

### Ingress Routing (via nginx, port 80/443)

| Path | Backend Service |
|---|---|
| `/api/v1/auth/**` | customer-service:80 |
| `/api/v1/users/**` | customer-service:80 |
| `/api/v1/donations/**` | donation-service:80 |
| `/api/v1/food-requests/**` | food-service:80 |
| `/api/v1/images/**` | image-service:80 |

---

## Directory Structure

```
scripts/
│
├── 00-cluster-setup/          # One-time node bootstrap (kubeadm)
│   ├── bootstrap-node.sh      # Run on EVERY node before joining cluster
│   └── init-control-plane.sh  # Run ONLY on control-plane node
│
├── 01-namespaces/
│   └── create-namespaces.sh   # Creates all 4 namespaces
│
├── 02-secrets/                # K8s Secrets (DB passwords, JWT, GCS keys)
│   ├── customer-service-secret.yaml
│   ├── donation-service-secret.yaml
│   ├── food-service-secret.yaml
│   ├── image-service-secret.yaml
│   └── notification-service-secret.yaml
│
├── 03-utility/                # Kafka + Redis (utility namespace)
│   ├── utility-configmap.yaml # Kafka/Redis connection config
│   ├── kafka.yaml             # Kafka StatefulSet (pinned to worker node)
│   └── redis.yaml             # Redis Deployment (pinned to worker node)
│
├── 04-services/               # Config for the microservices namespace
│   ├── cors-configmap.yaml    # CORS_ALLOWED_ORIGINS for gateway
│   ├── services-configmap.yaml # gRPC host addresses between services
│   ├── utility-config-mirror.yaml # Mirrors Kafka/Redis config into services ns
│   └── cross-namespace-rbac.yaml  # RBAC: services-sa reads utility ConfigMaps
│
├── 05-ingress/
│   └── ingress.yaml           # nginx Ingress rules for all API routes
│
├── 06-monitoring/             # Prometheus + Grafana (Helm)
│   ├── prometheus-values.yaml # kube-prometheus-stack Helm values
│   ├── prometheus-rbac.yaml   # ClusterRole for cross-namespace scraping
│   └── service-monitors.yaml  # ServiceMonitor CRDs for all 6 services
│
├── 07-logging/                # ELK stack + Fluent Bit (Helm, elk node)
│   ├── elasticsearch-values.yaml
│   ├── logstash-values.yaml
│   ├── kibana-values.yaml
│   └── fluentbit-values.yaml
│
└── 08-dashboards/             # Grafana dashboard JSON exports
    └── grafana-circuit-breaker-dashboard.json

└── 09-dashboard/              # Kubernetes Dashboard (cluster UI)
    └── kubernetes-dashboard.yaml
```

---

## Prerequisites

Before running any scripts you need:

1. **GCP project** with Compute Engine API enabled
2. **3 GCP VMs** created (Ubuntu 22.04, e2-medium, 50GB disk):
   - `k8s-control-plane`
   - `k8s-worker-node`
   - `k8s-elk-node`
3. **GCP Firewall rules** allowing:
   - Port 6443 (Kubernetes API)
   - Ports 30090, 30300 (Prometheus, Grafana NodePorts)
   - Ports 30601, 30920 (Kibana, Elasticsearch NodePorts)
   - Port 80, 443 (nginx ingress)
4. **Helm** installed on control plane:
   ```bash
   curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
   ```
5. **Helm repos** added:
   ```bash
   helm repo add elastic    https://helm.elastic.co
   helm repo add fluent     https://fluent.github.io/helm-charts
   helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
   helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
   helm repo update
   ```

---

## Deployment Order

Follow this order exactly. Each phase depends on the previous.

### Phase 0 — Bootstrap nodes (all 3 VMs)

```bash
# Run on k8s-control-plane, k8s-worker-node, AND k8s-elk-node
chmod +x 00-cluster-setup/bootstrap-node.sh
sudo ./00-cluster-setup/bootstrap-node.sh
```

### Phase 1 — Initialize cluster (control-plane only)

```bash
# Run ONLY on k8s-control-plane
chmod +x 00-cluster-setup/init-control-plane.sh
sudo ./00-cluster-setup/init-control-plane.sh

# Then on EACH worker node, run the kubeadm join command printed above
# After joining, label the elk node:
kubectl label node k8s-elk-node node-role.kubernetes.io/elk=true
```

### Phase 2 — Namespaces

```bash
chmod +x 01-namespaces/create-namespaces.sh
./01-namespaces/create-namespaces.sh
```

### Phase 3 — Secrets

> **⚠️ Fill in actual values before applying. Never commit real secrets to git.**

```bash
# Edit each file and replace placeholder values with real credentials
kubectl apply -f 02-secrets/
```

Each secret contains:
- `customer-service-secret.yaml` — DB password, JWT secret
- `donation-service-secret.yaml` — DB password
- `food-service-secret.yaml` — DB password
- `image-service-secret.yaml` — DB password, GCS service account key
- `notification-service-secret.yaml` — DB password, mail credentials

### Phase 4 — Utility (Kafka + Redis)

```bash
kubectl apply -f 03-utility/utility-configmap.yaml
kubectl apply -f 03-utility/kafka.yaml
kubectl apply -f 03-utility/redis.yaml

# Wait for both to be Running
kubectl get pods -n utility -w
```

### Phase 5 — Services config

```bash
kubectl apply -f 04-services/cross-namespace-rbac.yaml
kubectl apply -f 04-services/utility-config-mirror.yaml
kubectl apply -f 04-services/services-configmap.yaml
kubectl apply -f 04-services/cors-configmap.yaml
```

> **Note:** `cors-configmap.yaml` contains `CORS_ALLOWED_ORIGINS`. Update the IP/domain to match your ingress external IP before applying.

### Phase 6 — Deploy microservices (via Jenkins CI/CD)

Each service has its own Jenkins pipeline that:
1. Runs tests + SonarQube quality gate (80% coverage minimum)
2. Builds Docker image → pushes to GCR (`gcr.io/project-1edfecb7-ca87-40ca-997`)
3. Applies `k8s.yaml` from the service repo

Service repos and their k8s.yaml locations:
| Service | Repo path |
|---|---|
| gateway | `services/gateway/k8s.yaml` |
| customer | `GreenGrubUserManagementService/k8s.yaml` |
| donation | `Greengrub-donation-service/k8s.yaml` |
| food | `greengrub-food-request/k8s.yaml` |
| image | `services/image-service/k8s.yaml` |
| notification | `services/notification/k8s.yaml` |

```bash
# Verify all services are Running
kubectl get pods -n services
```

### Phase 7 — Ingress

```bash
# Install nginx ingress controller
helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace \
  --set controller.nodeSelector."kubernetes\.io/hostname"=k8s-worker-node

# Apply routing rules
kubectl apply -f 05-ingress/ingress.yaml

# Get the NodePort
kubectl get svc -n ingress-nginx
```

### Phase 8 — Monitoring (Prometheus + Grafana)

```bash
# Install kube-prometheus-stack
helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  -f 06-monitoring/prometheus-values.yaml

# Apply cross-namespace RBAC so Prometheus can scrape the services namespace
kubectl apply -f 06-monitoring/prometheus-rbac.yaml

# Apply ServiceMonitors for all 6 microservices
kubectl apply -f 06-monitoring/service-monitors.yaml

# Verify targets are up (wait ~2 min)
curl http://<worker-node-ip>:30090/api/v1/targets
```

Access:
- Prometheus: `http://<worker-node-ip>:30090`
- Grafana: `http://<worker-node-ip>:30300` (admin / prom-operator)

### Phase 9 — Import Grafana dashboards

In Grafana UI: **Dashboards → Import → Upload JSON**

```
08-dashboards/grafana-circuit-breaker-dashboard.json
```

Also import dashboard ID **12900** (Spring Boot 3.x Statistics) from grafana.com.

### Phase 10 — ELK Stack + Fluent Bit (logging node)

```bash
# Install Elasticsearch
helm install elasticsearch elastic/elasticsearch \
  --namespace logging \
  -f 07-logging/elasticsearch-values.yaml

# Wait until Ready (takes ~2 min)
kubectl get pods -n logging -w

# Install Logstash
helm install logstash elastic/logstash \
  --namespace logging \
  -f 07-logging/logstash-values.yaml

# Install Kibana
# Note: --no-hooks skips the xpack security pre-install job (not needed)
# The two dummy secrets below are required by the chart even with security off
kubectl create secret generic elasticsearch-master-certs \
  -n logging --from-literal=tls.crt="" --from-literal=tls.key="" --from-literal=ca.crt=""
kubectl create secret generic kibana-es-token \
  -n logging --from-literal=token=""

helm install kibana elastic/kibana \
  --namespace logging \
  --no-hooks \
  -f 07-logging/kibana-values.yaml

# Install Fluent Bit DaemonSet (collects logs from all nodes)
helm install fluent-bit fluent/fluent-bit \
  --namespace logging \
  -f 07-logging/fluentbit-values.yaml

# Verify all pods Running
kubectl get pods -n logging
```

Access:
- Kibana: `http://<elk-node-ip>:30601`
- Elasticsearch: `http://<elk-node-ip>:30920`

In Kibana, create data views:
| Name | Index pattern | Shows |
|---|---|---|
| Services | `services-*` | All microservice logs |
| Monitoring | `monitoring-*` | Prometheus/Grafana logs |
| Logging | `logging-*` | ELK stack own logs |

### Phase 11 — Kubernetes Dashboard

```bash
kubectl apply -f 09-dashboard/kubernetes-dashboard.yaml

# Wait for pods to be Running
kubectl get pods -n kubernetes-dashboard -w
```

**Get the login token:**
```bash
kubectl get secret dashboard-admin-token -n kubernetes-dashboard \
  -o jsonpath="{.data.token}" | base64 -d
```

Copy the token output — you'll need it to log in.

**Access:**
```
https://<any-node-ip>:8551
```

> The dashboard uses a self-signed certificate so your browser will show a security warning. Click **Advanced → Proceed** to continue.

On the login screen select **Token** and paste the token from above.

**What you can do in the dashboard:**
- View all pods, deployments, services across all namespaces
- Check pod logs directly from the UI
- View CPU/memory usage per pod (requires metrics-server)
- Scale deployments up/down
- Inspect ConfigMaps, Secrets, and PVCs

> **Security note:** The `dashboard-admin` service account has `cluster-admin` privileges. This is intentional for a dev/staging cluster but should be scoped down for production.

---

## Common Operations

### Check all pods across namespaces
```bash
kubectl get pods -A
```

### Restart a service
```bash
kubectl rollout restart deployment/<service-name> -n services
```

### Check Prometheus scrape targets
```bash
curl http://<worker-ip>:30090/api/v1/targets | python3 -m json.tool | grep health
```

### Re-apply ServiceMonitors after changes
```bash
kubectl apply -f 06-monitoring/service-monitors.yaml
```

### Force Elasticsearch index refresh in Kibana
```bash
curl http://<elk-ip>:30920/_cat/indices?v
```

### Check node resource usage
```bash
kubectl top nodes
kubectl top pods -n services
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Logstash CrashLoopBackOff exit 143 | Liveness probe firing before startup | `initialDelaySeconds` in logstash-values.yaml is 360s — wait 6 min |
| Kibana pre-install hook stuck | Chart tries to create xpack token | Use `--no-hooks` on install and create dummy secrets manually |
| Grafana OOMKilled | Memory limit too low | Patch limits: `kubectl patch deploy monitoring-grafana -n monitoring ...` |
| Dashboard shows N/A | Metric label mismatch | ServiceMonitors have `metricRelabelings` mapping `app`→`application` |
| CORS 403 Invalid CORS request | SCG WebMVC 5.0.0 bug | Gateway uses `CorsFilter` at `HIGHEST_PRECEDENCE` + `originStripFilter` |
| Duplicate `Access-Control-Allow-Origin` | Downstream service also setting CORS | Remove `CORS_ALLOWED_ORIGINS` from downstream service deployments |
| Dashboard shows blank / NET::ERR_CERT | Self-signed cert | Click Advanced → Proceed in browser |
| Dashboard login token expired | Token rotated | Re-run: `kubectl get secret dashboard-admin-token -n kubernetes-dashboard -o jsonpath="{.data.token}" | base64 -d` |
