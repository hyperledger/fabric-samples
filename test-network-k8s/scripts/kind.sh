#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

function pull_docker_images() {
  push_fn "Pulling docker images for Fabric ${FABRIC_VERSION}"

  docker pull ${FABRIC_CONTAINER_REGISTRY}/fabric-ca:$FABRIC_CA_VERSION
  docker pull ${FABRIC_CONTAINER_REGISTRY}/fabric-orderer:$FABRIC_VERSION
  docker pull ${FABRIC_PEER_IMAGE}
  docker pull ${FABRIC_CONTAINER_REGISTRY}/fabric-tools:$FABRIC_VERSION
  docker pull ghcr.io/hyperledgendary/fabric-ccaas-asset-transfer-basic:latest
  docker pull couchdb:3.2.1

  pop_fn
}

function load_docker_images() {
  push_fn "Loading docker images to KIND control plane"

  kind load docker-image ${FABRIC_CONTAINER_REGISTRY}/fabric-ca:$FABRIC_CA_VERSION
  kind load docker-image ${FABRIC_CONTAINER_REGISTRY}/fabric-orderer:$FABRIC_VERSION
  kind load docker-image ${FABRIC_PEER_IMAGE}
  kind load docker-image ${FABRIC_CONTAINER_REGISTRY}/fabric-tools:$FABRIC_VERSION
  kind load docker-image ghcr.io/hyperledgendary/fabric-ccaas-asset-transfer-basic:latest
  kind load docker-image couchdb:3.2.1

  pop_fn
}

function pull_docker_images_for_rest_sample() {
  push_fn "Pulling docker images for fabric-rest-sample"

  docker pull ghcr.io/hyperledger/fabric-rest-sample:latest
  docker pull redis:6.2.5

  pop_fn
}

function load_docker_images_for_rest_sample() {
  push_fn "Loading docker images for fabric-rest-sample to KIND control plane"

  kind load docker-image ghcr.io/hyperledgendary/fabric-ccaas-asset-transfer-basic:latest
  kind load docker-image redis:6.2.5

  pop_fn
}


function apply_nginx_ingress() {
  push_fn "Launching ingress controller"

  # This ingress-nginx.yaml was generated 9/24 from https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
  # with modifications for ssl-passthrough required to launch IBP-support with the nginx ingress.
  # It may be preferable to always load from the remote mainline?
  # kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
  kubectl apply -f kube/ingress-nginx.yaml

  pop_fn
}

function wait_for_nginx_ingress() {
  push_fn "Waiting for ingress controller"

  kubectl wait --namespace ingress-nginx \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/component=controller \
    --timeout=90s

  pop_fn
}

function apply_cert_manager() {
  push_fn "Launching cert-manager"

  # Install cert-manager to manage TLS certificates
  kubectl apply -f https://github.com/jetstack/cert-manager/releases/download/v1.6.1/cert-manager.yaml

  pop_fn
}

function wait_for_cert_manager() {
  push_fn "Waiting for cert-manager"

  kubectl -n cert-manager rollout status deploy/cert-manager
  kubectl -n cert-manager rollout status deploy/cert-manager-cainjector
  kubectl -n cert-manager rollout status deploy/cert-manager-webhook

  pop_fn
}

function kind_create() {
  push_fn  "Creating cluster \"${CLUSTER_NAME}\""

  # prevent the next kind cluster from using the previous Fabric network's enrollments.
  rm -rf $PWD/build

  # todo: always delete?  Maybe return no-op if the cluster already exists?
  kind delete cluster --name $CLUSTER_NAME

  local reg_name=${LOCAL_REGISTRY_NAME}
  local reg_port=${LOCAL_REGISTRY_PORT}
  local ingress_http_port=${NGINX_HTTP_PORT}
  local ingress_https_port=${NGINX_HTTPS_PORT}

  # the 'ipvs'proxy mode permits better HA abilities

  cat <<EOF | kind create cluster --name $CLUSTER_NAME --config=-
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
        hostPort: ${ingress_http_port}
        protocol: TCP
      - containerPort: 443
        hostPort: ${ingress_https_port}
        protocol: TCP
#networking:
#  kubeProxyMode: "ipvs"

# create a cluster with the local registry enabled in containerd
containerdConfigPatches:
- |-
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:${reg_port}"]
    endpoint = ["http://${reg_name}:${reg_port}"]

EOF

  # workaround for https://github.com/hyperledger/fabric-samples/issues/550 - pods can not resolve external DNS
  for node in $(kind get nodes);
  do
    docker exec "$node" sysctl net.ipv4.conf.all.route_localnet=1;
  done

  pop_fn
}

function launch_docker_registry() {
  push_fn "Launching container registry \"${LOCAL_REGISTRY_NAME}\" at localhost:${LOCAL_REGISTRY_PORT}"

  # create registry container unless it already exists
  local reg_name=${LOCAL_REGISTRY_NAME}
  local reg_port=${LOCAL_REGISTRY_PORT}

  running="$(docker inspect -f '{{.State.Running}}' "${reg_name}" 2>/dev/null || true)"
  if [ "${running}" != 'true' ]; then
    docker run \
      -d --restart=always -p "127.0.0.1:${reg_port}:5000" --name "${reg_name}" \
      registry:2
  fi

  # connect the registry to the cluster network
  # (the network may already be connected)
  docker network connect "kind" "${reg_name}" || true

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
    host: "localhost:${reg_port}"
    help: "https://kind.sigs.k8s.io/docs/user/local-registry/"
EOF

  pop_fn
}

function kind_delete() {
  push_fn "Deleting KIND cluster ${CLUSTER_NAME}"

  kind delete cluster --name $CLUSTER_NAME

  pop_fn 2
}

function kind_init() {
  # todo: how to pass this through to push_fn ?
  set -o errexit

  kind_create
  apply_nginx_ingress
  apply_cert_manager
  launch_docker_registry

  if [ "${STAGE_DOCKER_IMAGES}" == true ]; then
    pull_docker_images
    load_docker_images
    pull_docker_images_for_rest_sample
    load_docker_images_for_rest_sample
  fi

  wait_for_cert_manager
  wait_for_nginx_ingress
}

function kind_unkind() {
  kind_delete
}
