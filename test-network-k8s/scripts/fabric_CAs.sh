#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

function launch_ECert_CAs() {
  push_fn "Launching Fabric CAs"

  apply_template kube/org0/org0-ca.yaml
  apply_template kube/org1/org1-ca.yaml
  apply_template kube/org2/org2-ca.yaml

  kubectl -n $NS rollout status deploy/org0-ca
  kubectl -n $NS rollout status deploy/org1-ca
  kubectl -n $NS rollout status deploy/org2-ca

  # todo: this papers over a nasty bug whereby the CAs are ready, but sporadically refuse connections after a down / up
  sleep 5

  pop_fn
}

# experimental: create TLS CA issuers using cert-manager for each org.
function init_tls_cert_issuers() {
  push_fn "Initializing TLS certificate Issuers"

  # Create a self-signing certificate issuer / root TLS certificate for the blockchain.
  # TODO : Bring-Your-Own-Key - allow the network bootstrap to read an optional ECDSA key pair for the TLS trust root CA.
  kubectl -n $NS apply -f kube/root-tls-cert-issuer.yaml
  kubectl -n $NS wait --timeout=30s --for=condition=Ready issuer/root-tls-cert-issuer

  # Use the self-signing issuer to generate three Issuers, one for each org.
  kubectl -n $NS apply -f kube/org0/org0-tls-cert-issuer.yaml
  kubectl -n $NS apply -f kube/org1/org1-tls-cert-issuer.yaml
  kubectl -n $NS apply -f kube/org2/org2-tls-cert-issuer.yaml

  kubectl -n $NS wait --timeout=30s --for=condition=Ready issuer/org0-tls-cert-issuer
  kubectl -n $NS wait --timeout=30s --for=condition=Ready issuer/org1-tls-cert-issuer
  kubectl -n $NS wait --timeout=30s --for=condition=Ready issuer/org2-tls-cert-issuer

  pop_fn
}

function enroll_bootstrap_ECert_CA_user() {
  local org=$1
  local auth=$2
  local ecert_ca=${org}-ca

  echo 'set -x

  fabric-ca-client enroll \
    --url https://'${auth}'@'${ecert_ca}' \
    --tls.certfiles /var/hyperledger/fabric/config/tls/ca.crt \
    --mspdir $FABRIC_CA_CLIENT_HOME/'${ecert_ca}'/rcaadmin/msp

  ' | exec kubectl -n $NS exec deploy/${ecert_ca} -i -- /bin/sh
}

function enroll_bootstrap_ECert_CA_users() {
  push_fn "Enrolling bootstrap ECert CA users"

  enroll_bootstrap_ECert_CA_user org0 $RCAADMIN_AUTH
  enroll_bootstrap_ECert_CA_user org1 $RCAADMIN_AUTH
  enroll_bootstrap_ECert_CA_user org2 $RCAADMIN_AUTH

  pop_fn
}