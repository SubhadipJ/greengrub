#!/bin/bash
# Creates the 4 namespaces visible in the cloud architecture diagram.
# Run this FIRST before applying any other manifests.

set -e

echo "Creating Kubernetes namespaces..."

kubectl create namespace services    --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace utility     --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace monitoring  --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace logging     --dry-run=client -o yaml | kubectl apply -f -

echo ""
echo "Namespaces created:"
kubectl get namespaces services utility monitoring logging
