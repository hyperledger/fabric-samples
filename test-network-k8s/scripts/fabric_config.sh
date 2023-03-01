#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

function init_namespace() {
  local namespaces=$(echo "$ORG0_NS $ORG1_NS $ORG2_NS" | xargs -n1 | sort -u)
  for ns in $namespaces; do
    push_fn "Creating namespace \"$ns\""
    kubectl create namespace $ns || true
    pop_fn
  done
}

function delete_namespace() {
  local namespaces=$(echo "$ORG0_NS $ORG1_NS $ORG2_NS" | xargs -n1 | sort -u)
  for ns in $namespaces; do
    push_fn "Deleting namespace \"$ns\""
    kubectl delete namespace $ns || true
    pop_fn
  done
}

function init_storage_volumes() {
  push_fn "Provisioning volume storage"

  # Both KIND and k3s use the Rancher local-path provider.  In KIND, this is installed
  # as the 'standard' storage class, and in Rancher as the 'local-path' storage class.
  if [ "${CLUSTER_RUNTIME}" == "kind" ]; then
    export STORAGE_CLASS="standard"

  elif [ "${CLUSTER_RUNTIME}" == "k3s" ]; then
    export STORAGE_CLASS="local-path"

  else
    echo "Unknown CLUSTER_RUNTIME ${CLUSTER_RUNTIME}"
    exit 1
  fi

  cat kube/pvc-fabric-org0.yaml | envsubst | kubectl -n $ORG0_NS create -f - || true
  cat kube/pvc-fabric-org1.yaml | envsubst | kubectl -n $ORG1_NS create -f - || true
  cat kube/pvc-fabric-org2.yaml | envsubst | kubectl -n $ORG2_NS create -f - || true

  pop_fn
}

function load_org_config() {
  push_fn "Creating fabric config maps"

  kubectl -n $ORG0_NS delete configmap org0-config || true
  kubectl -n $ORG1_NS delete configmap org1-config || true
  kubectl -n $ORG2_NS delete configmap org2-config || true

  kubectl -n $ORG0_NS create configmap org0-config --from-file=config/org0
  kubectl -n $ORG1_NS create configmap org1-config --from-file=config/org1
  kubectl -n $ORG2_NS create configmap org2-config --from-file=config/org2

  pop_fn
}

function apply_k8s_builder_roles() {
  push_fn "Applying k8s chaincode builder roles"

  apply_template kube/fabric-builder-role.yaml $ORG1_NS
  apply_template kube/fabric-builder-rolebinding.yaml $ORG1_NS

  pop_fn
}

function apply_k8s_builders() {
  push_fn "Installing k8s chaincode builders"

  apply_template kube/org1/org1-install-k8s-builder.yaml $ORG1_NS
  apply_template kube/org2/org2-install-k8s-builder.yaml $ORG2_NS

  kubectl -n $ORG1_NS wait --for=condition=complete --timeout=60s job/org1-install-k8s-builder
  kubectl -n $ORG2_NS wait --for=condition=complete --timeout=60s job/org2-install-k8s-builder

  pop_fn
}