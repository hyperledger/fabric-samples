#!/bin/bash
#
# Copyright contributors to the Hyperledger Fabric Operator project
#
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at:
#
# 	  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

function apply_operator() {
  apply_kustomization config/rbac
  apply_kustomization config/manager

  sleep 2
}

function launch_operator() {
  init_namespace
  apply_operator

  wait_for_deployment fabric-operator
}

function network_up() {

  launch_operator

  launch_network_CAs

  apply_network_peers
  apply_network_orderers

  wait_for ibppeer org1-peer1
  wait_for ibppeer org1-peer2
  wait_for ibppeer org2-peer1
  wait_for ibppeer org2-peer2

  wait_for ibporderer org0-orderersnode1
  wait_for ibporderer org0-orderersnode2
  wait_for ibporderer org0-orderersnode3
}

function init_namespace() {
  push_fn "Creating namespace \"$NS\""

  cat << EOF | kubectl apply -f -
apiVersion: v1
kind: Namespace
metadata:
  name: ${NS}
EOF

  # https://kubernetes.io/docs/tasks/configure-pod-container/migrate-from-psp/
  kubectl label --overwrite namespace $NS pod-security.kubernetes.io/enforce=baseline

  pop_fn
}

function delete_namespace() {
  push_fn "Deleting namespace \"$NS\""

  kubectl delete namespace $NS --ignore-not-found

  pop_fn
}

function wait_for() {
  local type=$1
  local name=$2

  # wait for the operator to reconcile the CRD with a Deployment
  # This can be VERY slow in some cases, e.g. IKS dynamic volume provisioning the PVC
  # can take a couple of minutes to come up before the pods/deployments can be scheduled.
  kubectl -n $NS wait $type $name --for jsonpath='{.status.type}'=Deployed --timeout=3m

  # wait for the deployment to reach Ready
  kubectl -n $NS rollout status deploy $name
}

function launch_network_CAs() {
  push_fn "Launching Fabric CAs"

  apply_kustomization config/cas

  # give the operator a chance to run the first reconciliation on the new resource
  sleep 10

  wait_for ibpca org0-ca
  wait_for ibpca org1-ca
  wait_for ibpca org2-ca

  # load CA TLS certificates into the env, for substitution into the peer and orderer CRDs
  export ORG0_CA_CERT=$(kubectl -n $NS get cm/org0-ca-connection-profile -o json | jq -r .binaryData.\"profile.json\" | base64 -d | jq -r .tls.cert)
  export ORG1_CA_CERT=$(kubectl -n $NS get cm/org1-ca-connection-profile -o json | jq -r .binaryData.\"profile.json\" | base64 -d | jq -r .tls.cert)
  export ORG2_CA_CERT=$(kubectl -n $NS get cm/org2-ca-connection-profile -o json | jq -r .binaryData.\"profile.json\" | base64 -d | jq -r .tls.cert)

  enroll_bootstrap_rcaadmin org0 rcaadmin rcaadminpw
  enroll_bootstrap_rcaadmin org1 rcaadmin rcaadminpw
  enroll_bootstrap_rcaadmin org2 rcaadmin rcaadminpw

  pop_fn
}

function enroll_bootstrap_rcaadmin() {
  local org=$1
  local username=$2
  local password=$3

  echo "Enrolling $org root CA admin $username"

  ENROLLMENTS_DIR=${TEMP_DIR}/enrollments
  ORG_ADMIN_DIR=${ENROLLMENTS_DIR}/${org}/users/${username}

  # skip the enrollment if the admin certificate is available.
  if [ -f "${ORG_ADMIN_DIR}/msp/keystore/key.pem" ]; then
    echo "Found an existing admin enrollment at ${ORG_ADMIN_DIR}"
    return
  fi

  # Retrieve the CA information from Kubernetes
  CA_NAME=${org}-ca
  CA_DIR=${TEMP_DIR}/cas/${CA_NAME}
  CONNECTION_PROFILE=${CA_DIR}/connection-profile.json

  get_connection_profile $CA_NAME $CONNECTION_PROFILE

  # extract the CA enrollment URL and tls cert from the org connection profile
  CA_AUTH=${username}:${password}
  CA_ENDPOINT=$(jq -r .endpoints.api $CONNECTION_PROFILE)
  CA_HOST=$(echo ${CA_ENDPOINT} | cut -d/ -f3 | tr ':' '\n' | head -1)
  CA_PORT=$(echo ${CA_ENDPOINT} | cut -d/ -f3 | tr ':' '\n' | tail -1)
  CA_URL=https://${CA_AUTH}@${CA_HOST}:${CA_PORT}

  jq -r .tls.cert $CONNECTION_PROFILE | base64 -d >& $CA_DIR/tls-cert.pem

  # enroll the admin user
  FABRIC_CA_CLIENT_HOME=${ORG_ADMIN_DIR} fabric-ca-client enroll --url ${CA_URL} --tls.certfiles ${CA_DIR}/tls-cert.pem
}

function apply_network_peers() {
  push_fn "Launching Fabric Peers"

  apply_kustomization config/peers

  # give the operator a chance to run the first reconciliation on the new resource
  sleep 1

  pop_fn
}

function apply_network_orderers() {
  push_fn "Launching Fabric Orderers"

  apply_kustomization config/orderers

  # give the operator a chance to run the first reconciliation on the new resource
  sleep 1

  pop_fn
}

function stop_services() {
  push_fn "Stopping Fabric Services"

  undo_kustomization config/cas
  undo_kustomization config/peers
  undo_kustomization config/orderers

  # give the operator a chance to reconcile the deletion and then shut down the operator.
  sleep 10

  undo_kustomization config/manager

  # scrub any residual bits
  kubectl -n $NS delete deployment --all
  kubectl -n $NS delete pod --all
  kubectl -n $NS delete service --all
  kubectl -n $NS delete configmap --all
  kubectl -n $NS delete ingress --all
  kubectl -n $NS delete secret --all

  pop_fn
}

function network_down() {
  stop_services
  delete_namespace

  rm -rf $PWD/temp
}
