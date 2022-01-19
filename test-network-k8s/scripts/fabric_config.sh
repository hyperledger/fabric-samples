#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

function init_namespace() {
  push_fn "Creating namespace \"$NS\""

  kubectl create namespace $NS || true

  pop_fn
}

function init_storage_volumes() {
  push_fn "Provisioning volume storage"

  kubectl create -f kube/pv-fabric-org0.yaml || true
  kubectl create -f kube/pv-fabric-org1.yaml || true
  kubectl create -f kube/pv-fabric-org2.yaml || true

  kubectl -n $NS create -f kube/pvc-fabric-org0.yaml || true
  kubectl -n $NS create -f kube/pvc-fabric-org1.yaml || true
  kubectl -n $NS create -f kube/pvc-fabric-org2.yaml || true

  pop_fn
}

function load_org_config() {
  push_fn "Creating fabric config maps"

  kubectl -n $NS delete configmap org0-config || true
  kubectl -n $NS delete configmap org1-config || true
  kubectl -n $NS delete configmap org2-config || true

  kubectl -n $NS create configmap org0-config --from-file=config/org0
  kubectl -n $NS create configmap org1-config --from-file=config/org1
  kubectl -n $NS create configmap org2-config --from-file=config/org2

  pop_fn
}