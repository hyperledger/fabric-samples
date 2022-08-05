#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

function launch_orderers() {
  push_fn "Launching orderers"

  apply_template kube/org0/org0-orderer1.yaml
  apply_template kube/org0/org0-orderer2.yaml
  apply_template kube/org0/org0-orderer3.yaml

  kubectl -n $NS rollout status deploy/org0-orderer1
  kubectl -n $NS rollout status deploy/org0-orderer2
  kubectl -n $NS rollout status deploy/org0-orderer3

  pop_fn
}

function launch_peers() {
  push_fn "Launching peers"

  apply_template kube/org1/org1-peer1.yaml
  apply_template kube/org1/org1-peer2.yaml
  apply_template kube/org2/org2-peer1.yaml
  apply_template kube/org2/org2-peer2.yaml

  kubectl -n $NS rollout status deploy/org1-peer1
  kubectl -n $NS rollout status deploy/org1-peer2
  kubectl -n $NS rollout status deploy/org2-peer1
  kubectl -n $NS rollout status deploy/org2-peer2

  pop_fn
}

# Each network node needs a registration, enrollment, and MSP config.yaml
function create_node_local_MSP() {
  local node_type=$1
  local org=$2
  local node=$3
  local csr_hosts=$4
  local id_name=${org}-${node}
  local id_secret=${node_type}pw
  local ca_name=${org}-ca

  # Register the node admin
  rc=0
  fabric-ca-client  register \
    --id.name       ${id_name} \
    --id.secret     ${id_secret} \
    --id.type       ${node_type} \
    --url           https://${ca_name}.${DOMAIN} \
    --tls.certfiles $TEMP_DIR/cas/${ca_name}/tlsca-cert.pem \
    --mspdir        $TEMP_DIR/enrollments/${org}/users/${RCAADMIN_USER}/msp \
    || rc=$?        # trap error code from registration without exiting the network driver script"

  if [ $rc -eq 1 ]; then
    echo "CA admin was (probably) previously registered - continuing"
  fi

  # Enroll the node admin user from within k8s.  This will leave the certificates available on a volume share in the
  # cluster for access by the nodes when launching in a container.
  cat <<EOF | kubectl -n $NS exec deploy/${ca_name} -i -- /bin/sh

  set -x
  export FABRIC_CA_CLIENT_HOME=/var/hyperledger/fabric-ca-client
  export FABRIC_CA_CLIENT_TLS_CERTFILES=/var/hyperledger/fabric/config/tls/ca.crt

  fabric-ca-client enroll \
    --url https://${id_name}:${id_secret}@${ca_name} \
    --csr.hosts ${csr_hosts} \
    --mspdir /var/hyperledger/fabric/organizations/${node_type}Organizations/${org}.example.com/${node_type}s/${id_name}.${org}.example.com/msp

  # Create local MSP config.yaml
  echo "NodeOUs:
    Enable: true
    ClientOUIdentifier:
      Certificate: cacerts/${org}-ca.pem
      OrganizationalUnitIdentifier: client
    PeerOUIdentifier:
      Certificate: cacerts/${org}-ca.pem
      OrganizationalUnitIdentifier: peer
    AdminOUIdentifier:
      Certificate: cacerts/${org}-ca.pem
      OrganizationalUnitIdentifier: admin
    OrdererOUIdentifier:
      Certificate: cacerts/${org}-ca.pem
      OrganizationalUnitIdentifier: orderer" > /var/hyperledger/fabric/organizations/${node_type}Organizations/${org}.example.com/${node_type}s/${id_name}.${org}.example.com/msp/config.yaml
EOF
}

function create_orderer_local_MSP() {
  local org=$1
  local orderer=$2
  local csr_hosts=${org}-${orderer}

  create_node_local_MSP orderer $org $orderer $csr_hosts
}

function create_peer_local_MSP() {
  local org=$1
  local peer=$2
  local csr_hosts=localhost,${org}-${peer},${org}-peer-gateway-svc

  create_node_local_MSP peer $org $peer $csr_hosts
}

function create_local_MSP() {
  push_fn "Creating local node MSP"

  create_orderer_local_MSP org0 orderer1
  create_orderer_local_MSP org0 orderer2
  create_orderer_local_MSP org0 orderer3

  create_peer_local_MSP org1 peer1
  create_peer_local_MSP org1 peer2

  create_peer_local_MSP org2 peer1
  create_peer_local_MSP org2 peer2

  pop_fn
}

function network_up() {

  # Kube config
  init_namespace
  init_storage_volumes
  load_org_config

  # Service account permissions for the k8s builder
  if [ "${CHAINCODE_BUILDER}" == "k8s" ]; then
    apply_k8s_builder_roles
    apply_k8s_builders
  fi

  # Network TLS CAs
  init_tls_cert_issuers

  # Network ECert CAs
  launch_ECert_CAs
  enroll_bootstrap_ECert_CA_users

  # Test Network
  create_local_MSP

  launch_orderers
  launch_peers
}

function stop_services() {
  push_fn "Stopping Fabric services"

  kubectl -n $NS delete ingress --all
  kubectl -n $NS delete deployment --all
  kubectl -n $NS delete pod --all
  kubectl -n $NS delete service --all
  kubectl -n $NS delete configmap --all
  kubectl -n $NS delete cert --all
  kubectl -n $NS delete issuer --all
  kubectl -n $NS delete secret --all

  pop_fn
}

function scrub_org_volumes() {
  push_fn "Scrubbing Fabric volumes"
  
  # clean job to make this function can be rerun
  kubectl -n $NS delete jobs --all

  # scrub all pv contents
  kubectl -n $NS create -f kube/job-scrub-fabric-volumes.yaml
  kubectl -n $NS wait --for=condition=complete --timeout=60s job/job-scrub-fabric-volumes
  kubectl -n $NS delete jobs --all

  pop_fn
}

function network_down() {

  set +e

  kubectl get namespace $NS > /dev/null
  if [[ $? -ne 0 ]]; then
    echo "No namespace $NS found - nothing to do."
    return
  fi

  set -e

  stop_services
  scrub_org_volumes

  delete_namespace

  rm -rf $PWD/build
}
