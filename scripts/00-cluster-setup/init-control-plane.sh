#!/bin/bash
# ──────────────────────────────────────────────────────────────────────────────
# GreenGrub — Control Plane Init (run ONLY on the control-plane node)
#
# Usage:
#   chmod +x init-control-plane.sh
#   sudo ./init-control-plane.sh
# ──────────────────────────────────────────────────────────────────────────────
set -e

CONTROL_PLANE_IP=$(hostname -I | awk '{print $1}')
echo "=== Initializing control plane at $CONTROL_PLANE_IP ==="

kubeadm init \
  --pod-network-cidr=10.244.0.0/16 \
  --apiserver-advertise-address="$CONTROL_PLANE_IP"

echo "=== Configuring kubectl for current user ==="
mkdir -p "$HOME/.kube"
cp /etc/kubernetes/admin.conf "$HOME/.kube/config"
chown "$(id -u):$(id -g)" "$HOME/.kube/config"

echo "=== Installing Flannel CNI ==="
kubectl apply -f https://raw.githubusercontent.com/flannel-io/flannel/master/Documentation/kube-flannel.yml

echo ""
echo "=== Control plane ready. ==="
echo "Run the kubeadm join command printed above on each worker node."
echo ""
echo "Label worker nodes after joining:"
echo "  kubectl label node <worker-name> kubernetes.io/hostname=<worker-name>"
echo "  kubectl label node <elk-node-name> node-role.kubernetes.io/elk=true"
