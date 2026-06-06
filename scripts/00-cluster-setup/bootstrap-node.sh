#!/bin/bash
# ──────────────────────────────────────────────────────────────────────────────
# GreenGrub — Kubernetes Cluster Bootstrap (kubeadm on GCP)
#
# Run this on each node BEFORE initializing the cluster.
# This script installs containerd + kubeadm + kubelet + kubectl v1.29.
#
# Usage:
#   chmod +x bootstrap-node.sh
#   sudo ./bootstrap-node.sh
# ──────────────────────────────────────────────────────────────────────────────
set -e

echo "=== Step 1: Disable swap ==="
swapoff -a
sed -i "/swap/d" /etc/fstab

echo "=== Step 2: Kernel modules ==="
modprobe overlay
modprobe br_netfilter
cat <<EOF > /etc/modules-load.d/k8s.conf
overlay
br_netfilter
EOF

cat <<EOF > /etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF
sysctl --system

echo "=== Step 3: Install containerd ==="
apt-get update -qq
apt-get install -y containerd apt-transport-https curl gnupg
mkdir -p /etc/containerd
containerd config default > /etc/containerd/config.toml
sed -i "s/SystemdCgroup = false/SystemdCgroup = true/" /etc/containerd/config.toml
systemctl restart containerd
systemctl enable containerd

echo "=== Step 4: Install kubeadm/kubelet/kubectl v1.29 ==="
curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.29/deb/Release.key \
  | gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.29/deb/ /" \
  > /etc/apt/sources.list.d/kubernetes.list
apt-get update -qq
apt-get install -y kubelet=1.29.15-1.1 kubeadm=1.29.15-1.1 kubectl=1.29.15-1.1
apt-mark hold kubelet kubeadm kubectl
systemctl enable kubelet

echo "=== Node bootstrap complete. ==="
echo "Control plane: run 'kubeadm init'"
echo "Worker node:   run the 'kubeadm join' command printed by kubeadm init"
