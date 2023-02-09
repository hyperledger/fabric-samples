#!/usr/bin/env bash
#
# Copyright contributors to the Hyperledgendary Kubernetes Test Network project
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

# All checks run in the workshop root folder
cd "$(dirname "$0")"/..

. scripts/utils.sh

# todo: need to check the enrollments here (just enroll org)
# todo: need to check the MSP exports here in the channel-config/organizations  (just export-msp org)



EXIT=0

function operator_crds() {
  kubectl get customresourcedefinition.apiextensions.k8s.io/ibpcas.ibp.com
  kubectl get customresourcedefinition.apiextensions.k8s.io/ibpconsoles.ibp.com
  kubectl get customresourcedefinition.apiextensions.k8s.io/ibporderers.ibp.com
  kubectl get customresourcedefinition.apiextensions.k8s.io/ibppeers.ibp.com
}

function org0_operator_deployed() {
  kubectl -n org0 get deployment fabric-operator
}

function org1_operator_deployed() {
  kubectl -n org1 get deployment fabric-operator
}

function org2_operator_deployed() {
  kubectl -n org2 get deployment fabric-operator
}


# Did it apply the CRDs?
function org0_custom_resources() {
  kubectl -n org0 get ibpca ca
  kubectl -n org0 get ibporderer orderernode1
  kubectl -n org0 get ibporderer orderernode2
  kubectl -n org0 get ibporderer orderernode3
}

function org1_custom_resources() {
  kubectl -n org1 get ibpca ca
  kubectl -n org1 get ibppeer peer1
  kubectl -n org1 get ibppeer peer2
}

function org2_custom_resources() {
  kubectl -n org2 get ibpca ca
  kubectl -n org2 get ibppeer peer1
  kubectl -n org2 get ibppeer peer2
}

function org0_deployments() {
  kubectl -n org0 get deployment ca
  kubectl -n org0 get deployment orderernode1
  kubectl -n org0 get deployment orderernode2
  kubectl -n org0 get deployment orderernode3
}

function org1_deployments() {
  kubectl -n org1 get deployment ca
  kubectl -n org1 get deployment peer1
  kubectl -n org1 get deployment peer2 
}

function org2_deployments() {
  kubectl -n org2 get deployment ca
  kubectl -n org2 get deployment peer1
  kubectl -n org2 get deployment peer2
}

# Hit the CAs using the TLS certs, etc.
function org0_cas_ready() {
  curl --fail -s --cacert organizations/org0/enrollments/ca-tls-cert.pem https://org0-ca-ca.org0.localho.st/cainfo
}

function org1_cas_ready() {
  curl --fail -s --cacert organizations/org1/enrollments/ca-tls-cert.pem https://org1-ca-ca.org1.localho.st/cainfo
}

function org2_cas_ready() {
  curl --fail -s --cacert organizations/org2/enrollments/ca-tls-cert.pem https://org2-ca-ca.org2.localho.st/cainfo
}

function channel_msp() {
  find channel-config/organizations
}


check operator_crds         "fabric-operator CRDs have been installed"

check org0_operator_deployed  "org0 fabric-operator has been deployed"
check org1_operator_deployed  "org1 fabric-operator has been deployed"
check org2_operator_deployed  "org2 fabric-operator has been deployed"

check org0_custom_resources "org0 CAs, Orderers, and Peers have been created"
check org1_custom_resources "org1 CAs, Orderers, and Peers have been created"
check org2_custom_resources "org2 CAs, Orderers, and Peers have been created"

check org0_deployments      "org0 services have been deployed"
check org1_deployments      "org1 services have been deployed"
check org2_deployments      "org2 services have been deployed"

check org0_cas_ready        "org0 CAs are available at ingress"
check org1_cas_ready        "org1 CAs are available at ingress"
check org2_cas_ready        "org2 CAs are available at ingress"

#check channel_msp       "Channel MSP has been exported"

exit $EXIT

