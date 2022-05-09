#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

# cluster "group" commands.  Like "main" for the fabric-cli "cluster" sub-command
function cluster_command_group() {

  # Default COMMAND is 'init' if not specified
  if [ "$#" -eq 0 ]; then
    COMMAND="init"

  else
    COMMAND=$1
    shift
  fi

  if [ "${COMMAND}" == "init" ]; then
    log "Initializing K8s cluster"
    cluster_init
    log "🏁 - Cluster is ready"

  elif [ "${COMMAND}" == "clean" ]; then
    log "Cleaning k8s cluster"
    cluster_clean
    log "🏁 - Cluster is cleaned"

  elif [ "${COMMAND}" == "load-images" ]; then
    log "Loading Docker images"
    load_images
    log "🏁 - Images are loaded"

  else
    print_help
    exit 1
  fi
}

function pull_docker_images() {
  push_fn "Pulling docker images for Fabric ${FABRIC_VERSION}"

  $CONTAINER_CLI pull ${CONTAINER_NAMESPACE} ${FABRIC_CONTAINER_REGISTRY}/fabric-ca:$FABRIC_CA_VERSION
  $CONTAINER_CLI pull ${CONTAINER_NAMESPACE} ${FABRIC_CONTAINER_REGISTRY}/fabric-orderer:$FABRIC_VERSION
  $CONTAINER_CLI pull ${CONTAINER_NAMESPACE} ${FABRIC_PEER_IMAGE}
  $CONTAINER_CLI pull ${CONTAINER_NAMESPACE} couchdb:3.2.1

  $CONTAINER_CLI pull ${CONTAINER_NAMESPACE} ghcr.io/hyperledger/fabric-rest-sample:latest
  $CONTAINER_CLI pull ${CONTAINER_NAMESPACE} redis:6.2.5

  pop_fn
}

function cluster_init() {

  apply_nginx_ingress
  apply_cert_manager

  sleep 2

  wait_for_cert_manager
  wait_for_nginx_ingress
  
  if [ "${STAGE_DOCKER_IMAGES}" == true ]; then
    pull_docker_images
    load_docker_images
    pull_docker_images_for_rest_sample
    load_docker_images_for_rest_sample
  fi
}

function apply_nginx() {
  apply_nginx_ingress
  wait_for_nginx_ingress
}

function apply_nginx_ingress() {
  push_fn "Launching ${CLUSTER_RUNTIME} ingress controller"

  # 1.1.2 static ingress with modifications to enable ssl-passthrough
  # k3s : 'cloud'
  # kind : 'kind'
  # kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.1.2/deploy/static/provider/cloud/deploy.yaml

  kubectl apply -f kube/ingress-nginx-${CLUSTER_RUNTIME}.yaml

  pop_fn
}

function delete_nginx_ingress() {
  push_fn "Deleting ${CLUSTER_RUNTIME} ingress controller"

  cat kube/ingress-nginx-${CLUSTER_RUNTIME}.yaml | kubectl delete -f -

  pop_fn
}

function wait_for_nginx_ingress() {
  push_fn "Waiting for ingress controller"

  kubectl wait --namespace ingress-nginx \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/component=controller \
    --timeout=2m

  pop_fn
}

function apply_cert_manager() {
  push_fn "Launching cert-manager"

  # Install cert-manager to manage TLS certificates
  kubectl apply -f https://github.com/jetstack/cert-manager/releases/download/v1.6.1/cert-manager.yaml

  pop_fn
}

function delete_cert_manager() {
  push_fn "Deleting cert-manager"

  # Install cert-manager to manage TLS certificates
  curl https://github.com/jetstack/cert-manager/releases/download/v1.6.1/cert-manager.yaml | kubectl delete -f -

  pop_fn
}

function wait_for_cert_manager() {
  push_fn "Waiting for cert-manager"

  kubectl -n cert-manager rollout status deploy/cert-manager
  kubectl -n cert-manager rollout status deploy/cert-manager-cainjector
  kubectl -n cert-manager rollout status deploy/cert-manager-webhook

  pop_fn
}

function cluster_clean() {
  delete_nginx_ingress
  delete_cert_manager
}

function load_images() {
  if [ "${CLUSTER_RUNTIME}" == "kind" ]; then
    kind_load_docker_images
  fi
}