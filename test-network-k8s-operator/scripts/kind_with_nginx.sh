#!/bin/bash

#
# IBM Confidential
# OCO Source Materials
#
# Organic Growth Ventures
# (C) Copyright IBM Corp. 2022 All Rights Reserved.
#
# The source code for this program is not published or otherwise
# divested of its trade secrets, irrespective of what has been
# deposited with the U.S. Copyright Office.
#

set -eo pipefail
set -x

KIND_CLUSTER_NAME=kind
KIND_CLUSTER_IMAGE=${KIND_CLUSTER_IMAGE:-kindest/node:v1.24.4}
KIND_API_SERVER_ADDRESS=${KIND_API_SERVER_ADDRESS:-127.0.0.1}
KIND_API_SERVER_PORT=${KIND_API_SERVER_PORT:-8888}
CONTAINER_REGISTRY_NAME=${CONTAINER_REGISTRY_NAME:-kind-registry}
CONTAINER_REGISTRY_ADDRESS=${CONTAINER_REGISTRY_ADDRESS:-127.0.0.1}
CONTAINER_REGISTRY_PORT=${CONTAINER_REGISTRY_PORT:-5000}

function kind_with_nginx() {

  delete_cluster

  create_cluster

  #start_cert_manager

  start_nginx

  apply_coredns_override

  launch_docker_registry
}

#
# Delete a kind cluster if it exists
#
function delete_cluster() {
  kind delete cluster --name $KIND_CLUSTER_NAME
}

#
# Create a local KIND cluster
#
function create_cluster() {
  cat << EOF | kind create cluster --name $KIND_CLUSTER_NAME --image $KIND_CLUSTER_IMAGE --config=-
---
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
networking:
  apiServerAddress: ${KIND_API_SERVER_ADDRESS}
  apiServerPort: ${KIND_API_SERVER_PORT}

# create a cluster with the local registry enabled in containerd
containerdConfigPatches:
- |-
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:${CONTAINER_REGISTRY_PORT}"]
    endpoint = ["http://${CONTAINER_REGISTRY_NAME}:${CONTAINER_REGISTRY_PORT}"]
EOF

  #
  # Work around a bug in KIND where DNS is not always resolved correctly on machines with IPv6
  #
  for node in $(kind get nodes);
  do
      docker exec "$node" sysctl net.ipv4.conf.all.route_localnet=1;
  done
}

#
# Install cert-manager.io
#
function start_cert_manager() {
  kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.10.0/cert-manager.yaml

  sleep 5

  kubectl -n cert-manager rollout status deploy/cert-manager
  kubectl -n cert-manager rollout status deploy/cert-manager-cainjector
  kubectl -n cert-manager rollout status deploy/cert-manager-webhook

  # Check for a root CA certificate / secret created by a previous cluster.  If present, re-use the
  # cert as it could have been imported into the system's keychain.
  # TODO: this would be best stored outside of the project - maybe override with an ENV?
  local issuer_secret_path=kind/cert-manager/ca-issuer-secret.yaml
  if test -f ${issuer_secret_path}; then
    echo "Overriding CA root issuer secret" ${issuer_secret_path}
    kubectl -n cert-manager create -f ${issuer_secret_path}
  fi

  # Apply the cert-manager cluster-issuers
  kubectl -n cert-manager apply -k kind/cert-manager

  # Save the root cert for future use in future KIND clusters
  if ! test -f ${issuer_secret_path}; then
    # todo: use a better wait for the issuer to be ready / secret to be created
    sleep 5
    kubectl -n cert-manager get secret ca-issuer-secret -o yaml > ${issuer_secret_path}
  fi
}

#
# Install an Nginx ingress controller bound to port 80 and 443.
#
function start_nginx() {
  kubectl apply -k kind/nginx

  sleep 10

  kubectl wait \
    --namespace ingress-nginx \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/component=controller \
    --timeout=3m
}

#
# Override Core DNS with a wildcard matcher for the "*.localho.st" domain, binding to the
# IP address of the Nginx ingress controller on the kubernetes internal network.  Effectively this
# "steals" the domain name for *.localho.st, directing traffic to the Nginx load balancer, rather
# than to the loopback interface at 127.0.0.1.
#
function apply_coredns_override() {
  CLUSTER_IP=$(kubectl -n ingress-nginx get svc ingress-nginx-controller -o json | jq -r .spec.clusterIP)

  cat << EOF | kubectl apply -f -
---
kind: ConfigMap
apiVersion: v1
metadata:
  name: coredns
  namespace: kube-system
data:
  Corefile: |
    .:53 {
        errors
        health {
           lameduck 5s
        }
        ready
        rewrite name regex (.*)\.localho\.st host.ingress.internal
        hosts {
          ${CLUSTER_IP} host.ingress.internal
          fallthrough
        }
        kubernetes cluster.local in-addr.arpa ip6.arpa {
           pods insecure
           fallthrough in-addr.arpa ip6.arpa
           ttl 30
        }
        prometheus :9153
        forward . /etc/resolv.conf {
           max_concurrent 1000
        }
        cache 30
        loop
        reload
        loadbalance
    }
EOF

  kubectl -n kube-system rollout restart deployment/coredns
}

function launch_docker_registry() {
  
  # create registry container unless it already exists
  running="$(docker inspect -f '{{.State.Running}}' "${reg_name}" 2>/dev/null || true)"
  if [ "${running}" != 'true' ]; then
    docker run  \
      --detach  \
      --restart always \
      --name    "${CONTAINER_REGISTRY_NAME}" \
      --publish "${CONTAINER_REGISTRY_ADDRESS}:${CONTAINER_REGISTRY_PORT}:${CONTAINER_REGISTRY_PORT}" \
      registry:2
  fi

  # connect the registry to the cluster network
  # (the network may already be connected)
  docker network connect "kind" "${CONTAINER_REGISTRY_NAME}" || true

  # Document the local registry
  # https://github.com/kubernetes/enhancements/tree/master/keps/sig-cluster-lifecycle/generic/1755-communicating-a-local-registry
  cat <<EOF | kubectl apply -f -
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: local-registry-hosting
  namespace: kube-public
data:
  localRegistryHosting.v1: |
    host: "localhost:${CONTAINER_REGISTRY_PORT}"
    help: "https://kind.sigs.k8s.io/docs/user/local-registry/"
EOF
  
}

kind_with_nginx