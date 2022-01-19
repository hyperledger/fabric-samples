#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

function launch_CA() {
  local yaml=$1
  cat ${yaml} \
    | sed 's,{{FABRIC_CONTAINER_REGISTRY}},'${FABRIC_CONTAINER_REGISTRY}',g' \
    | sed 's,{{FABRIC_CA_VERSION}},'${FABRIC_CA_VERSION}',g' \
    | kubectl -n $NS apply -f -
}

function launch_TLS_CAs() {
  push_fn "Launching TLS CAs"

  launch_CA kube/org0/org0-tls-ca.yaml
  launch_CA kube/org1/org1-tls-ca.yaml
  launch_CA kube/org2/org2-tls-ca.yaml

  kubectl -n $NS rollout status deploy/org0-tls-ca
  kubectl -n $NS rollout status deploy/org1-tls-ca
  kubectl -n $NS rollout status deploy/org2-tls-ca

  # todo: this papers over a nasty bug whereby the CAs are ready, but sporadically refuse connections after a down / up
  sleep 10

  pop_fn
}

function launch_ECert_CAs() {
  push_fn "Launching ECert CAs"

  launch_CA kube/org0/org0-ecert-ca.yaml
  launch_CA kube/org1/org1-ecert-ca.yaml
  launch_CA kube/org2/org2-ecert-ca.yaml

  kubectl -n $NS rollout status deploy/org0-ecert-ca
  kubectl -n $NS rollout status deploy/org1-ecert-ca
  kubectl -n $NS rollout status deploy/org2-ecert-ca

  # todo: this papers over a nasty bug whereby the CAs are ready, but sporadically refuse connections after a down / up
  sleep 10

  pop_fn
}

# Enroll bootstrap user with TLS CA
# https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#enroll-bootstrap-user-with-tls-ca
function enroll_bootstrap_TLS_CA_user() {
  local org=$1
  local auth=$2
  local tlsca=${org}-tls-ca

  # todo: get rid of export here - put in yaml

  echo 'set -x

  mkdir -p $FABRIC_CA_CLIENT_HOME/tls-root-cert
  cp $FABRIC_CA_SERVER_HOME/ca-cert.pem $FABRIC_CA_CLIENT_HOME/tls-root-cert/tls-ca-cert.pem

  fabric-ca-client enroll \
    --url https://'$auth'@'${tlsca}' \
    --tls.certfiles $FABRIC_CA_CLIENT_HOME/tls-root-cert/tls-ca-cert.pem \
    --csr.hosts '${tlsca}' \
    --mspdir $FABRIC_CA_CLIENT_HOME/tls-ca/tlsadmin/msp

  ' | exec kubectl -n $NS exec deploy/${tlsca} -i -- /bin/sh
}

function enroll_bootstrap_TLS_CA_users() {
  push_fn "Enrolling bootstrap TLS CA users"

  enroll_bootstrap_TLS_CA_user org0 $TLSADMIN_AUTH
  enroll_bootstrap_TLS_CA_user org1 $TLSADMIN_AUTH
  enroll_bootstrap_TLS_CA_user org2 $TLSADMIN_AUTH

  pop_fn
}

function register_enroll_ECert_CA_bootstrap_user() {
  local org=$1
  local tlsauth=$2
  local tlsca=${org}-tls-ca
  local ecertca=${org}-ecert-ca

  echo 'set -x

  fabric-ca-client register \
    --id.name rcaadmin \
    --id.secret rcaadminpw \
    --url https://'${tlsca}' \
    --tls.certfiles $FABRIC_CA_CLIENT_HOME/tls-root-cert/tls-ca-cert.pem \
    --mspdir $FABRIC_CA_CLIENT_HOME/tls-ca/tlsadmin/msp

  fabric-ca-client enroll \
    --url https://'${tlsauth}'@'${tlsca}' \
    --tls.certfiles $FABRIC_CA_CLIENT_HOME/tls-root-cert/tls-ca-cert.pem \
    --csr.hosts '${ecertca}' \
    --mspdir $FABRIC_CA_CLIENT_HOME/tls-ca/rcaadmin/msp

  # Important: the rcaadmin signing certificate is referenced by the ECert CA FABRIC_CA_SERVER_TLS_CERTFILE config attribute.
  # For simplicity, reference the key at a fixed, known location
  cp $FABRIC_CA_CLIENT_HOME/tls-ca/rcaadmin/msp/keystore/*_sk $FABRIC_CA_CLIENT_HOME/tls-ca/rcaadmin/msp/keystore/key.pem

  ' | exec kubectl -n $NS exec deploy/${tlsca} -i -- /bin/sh
}

# https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#register-and-enroll-the-organization-ca-bootstrap-identity-with-the-tls-ca
function register_enroll_ECert_CA_bootstrap_users() {
  push_fn "Registering and enrolling ECert CA bootstrap users"

  register_enroll_ECert_CA_bootstrap_user org0 $TLSADMIN_AUTH
  register_enroll_ECert_CA_bootstrap_user org1 $TLSADMIN_AUTH
  register_enroll_ECert_CA_bootstrap_user org2 $TLSADMIN_AUTH

  pop_fn
}

function enroll_bootstrap_ECert_CA_user() {
  local org=$1
  local auth=$2
  local ecert_ca=${org}-ecert-ca

  echo 'set -x

  fabric-ca-client enroll \
    --url https://'${auth}'@'${ecert_ca}' \
    --tls.certfiles $FABRIC_CA_CLIENT_HOME/tls-root-cert/tls-ca-cert.pem \
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