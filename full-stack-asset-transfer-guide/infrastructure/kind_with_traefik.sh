#!/usr/bin/env bash
#
# Copyright contributors to the Hyperledgendary Full Stack Asset Transfer project
#
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at:
#
#	  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -eo pipefail
set -x

KIND_CLUSTER_NAME=kind
KIND_CLUSTER_IMAGE=${KIND_CLUSTER_IMAGE:-kindest/node:v1.28.0}
KIND_API_SERVER_ADDRESS=${KIND_API_SERVER_ADDRESS:-127.0.0.1}
KIND_API_SERVER_PORT=${KIND_API_SERVER_PORT:-8888}
CONTAINER_REGISTRY_NAME=${CONTAINER_REGISTRY_NAME:-kind-registry}
CONTAINER_REGISTRY_ADDRESS=${CONTAINER_REGISTRY_ADDRESS:-127.0.0.1}
CONTAINER_REGISTRY_PORT=${CONTAINER_REGISTRY_PORT:-5000}

function kind_with_traefik() {

  delete_cluster

  create_cluster

  start_traefik

  apply_coredns_override

  launch_docker_registry
}

function delete_cluster() {
  kind delete cluster --name $KIND_CLUSTER_NAME
}

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

containerdConfigPatches:
- |-
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:${CONTAINER_REGISTRY_PORT}"]
    endpoint = ["http://${CONTAINER_REGISTRY_NAME}:${CONTAINER_REGISTRY_PORT}"]
EOF

  for node in $(kind get nodes);
  do
      docker exec "$node" sysctl net.ipv4.conf.all.route_localnet=1;
  done
}

function start_traefik() {
  kubectl apply -f infrastructure/kind_console_ingress/templates/ingress/traefik-controller.yaml

  sleep 20

  kubectl wait --namespace traefik \
      --for=condition=ready pod \
      --selector=app=traefik \
      --timeout=3m
}

function apply_coredns_override() {
  CLUSTER_IP=$(kubectl -n traefik get svc traefik -o json | jq -r .spec.clusterIP)

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
  running="$(docker inspect -f '{{.State.Running}}' "${reg_name}" 2>/dev/null || true)"
  if [ "${running}" != 'true' ]; then
    docker run  \
      --detach  \
      --restart always \
      --name    "${CONTAINER_REGISTRY_NAME}" \
      --publish "${CONTAINER_REGISTRY_ADDRESS}:${CONTAINER_REGISTRY_PORT}:${CONTAINER_REGISTRY_PORT}" \
      registry:2
  fi

  docker network connect "kind" "${CONTAINER_REGISTRY_NAME}" || true

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

kind_with_traefik
