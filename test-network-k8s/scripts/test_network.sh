#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

# todo: oof this is rough.


function launch() {
  local yaml=$1
  cat ${yaml} \
    | sed 's,{{FABRIC_CONTAINER_REGISTRY}},'${FABRIC_CONTAINER_REGISTRY}',g' \
    | sed 's,{{FABRIC_VERSION}},'${FABRIC_VERSION}',g' \
    | kubectl -n $NS apply -f -
}

function launch_orderers() {
  push_fn "Launching orderers"

  launch kube/org0/org0-orderer1.yaml
  launch kube/org0/org0-orderer2.yaml
  launch kube/org0/org0-orderer3.yaml

  kubectl -n $NS rollout status deploy/org0-orderer1
  kubectl -n $NS rollout status deploy/org0-orderer2
  kubectl -n $NS rollout status deploy/org0-orderer3

  pop_fn
}

function launch_peers() {
  push_fn "Launching peers"

  launch kube/org1/org1-peer1.yaml
  launch kube/org1/org1-peer2.yaml
  launch kube/org2/org2-peer1.yaml
  launch kube/org2/org2-peer2.yaml

  kubectl -n $NS rollout status deploy/org1-peer1
  kubectl -n $NS rollout status deploy/org1-peer2
  kubectl -n $NS rollout status deploy/org2-peer1
  kubectl -n $NS rollout status deploy/org2-peer2

  pop_fn
}

function create_org0_local_MSP() {
  echo 'set -x
  export FABRIC_CA_CLIENT_HOME=/var/hyperledger/fabric-ca-client
  export FABRIC_CA_CLIENT_TLS_CERTFILES=$FABRIC_CA_CLIENT_HOME/tls-root-cert/tls-ca-cert.pem

  # Each identity in the network needs a registration and enrollment.
  fabric-ca-client register --id.name org0-orderer1 --id.secret ordererpw --id.type orderer --url https://org0-ecert-ca --mspdir $FABRIC_CA_CLIENT_HOME/org0-ecert-ca/rcaadmin/msp
  fabric-ca-client register --id.name org0-orderer2 --id.secret ordererpw --id.type orderer --url https://org0-ecert-ca --mspdir $FABRIC_CA_CLIENT_HOME/org0-ecert-ca/rcaadmin/msp
  fabric-ca-client register --id.name org0-orderer3 --id.secret ordererpw --id.type orderer --url https://org0-ecert-ca --mspdir $FABRIC_CA_CLIENT_HOME/org0-ecert-ca/rcaadmin/msp
  fabric-ca-client register --id.name org0-admin --id.secret org0adminpw  --id.type admin   --url https://org0-ecert-ca --mspdir $FABRIC_CA_CLIENT_HOME/org0-ecert-ca/rcaadmin/msp --id.attrs "hf.Registrar.Roles=client,hf.Registrar.Attributes=*,hf.Revoker=true,hf.GenCRL=true,admin=true:ecert,abac.init=true:ecert"

  fabric-ca-client enroll --url https://org0-orderer1:ordererpw@org0-ecert-ca --csr.hosts org0-orderer1 --mspdir /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer1.org0.example.com/msp
  fabric-ca-client enroll --url https://org0-orderer2:ordererpw@org0-ecert-ca --csr.hosts org0-orderer2 --mspdir /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer2.org0.example.com/msp
  fabric-ca-client enroll --url https://org0-orderer3:ordererpw@org0-ecert-ca --csr.hosts org0-orderer3 --mspdir /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer3.org0.example.com/msp
  fabric-ca-client enroll --url https://org0-admin:org0adminpw@org0-ecert-ca --mspdir /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/users/Admin@org0.example.com/msp

  # Each node in the network needs a TLS registration and enrollment.
  fabric-ca-client register --id.name org0-orderer1 --id.secret ordererpw --id.type orderer --url https://org0-tls-ca --mspdir $FABRIC_CA_CLIENT_HOME/tls-ca/tlsadmin/msp
  fabric-ca-client register --id.name org0-orderer2 --id.secret ordererpw --id.type orderer --url https://org0-tls-ca --mspdir $FABRIC_CA_CLIENT_HOME/tls-ca/tlsadmin/msp
  fabric-ca-client register --id.name org0-orderer3 --id.secret ordererpw --id.type orderer --url https://org0-tls-ca --mspdir $FABRIC_CA_CLIENT_HOME/tls-ca/tlsadmin/msp

  fabric-ca-client enroll --url https://org0-orderer1:ordererpw@org0-tls-ca --csr.hosts org0-orderer1 --mspdir /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer1.org0.example.com/tls
  fabric-ca-client enroll --url https://org0-orderer2:ordererpw@org0-tls-ca --csr.hosts org0-orderer2 --mspdir /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer2.org0.example.com/tls
  fabric-ca-client enroll --url https://org0-orderer3:ordererpw@org0-tls-ca --csr.hosts org0-orderer3 --mspdir /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer3.org0.example.com/tls

  # Copy the TLS signing keys to a fixed path for convenience when starting the orderers.
  cp /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer1.org0.example.com/tls/keystore/*_sk /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer1.org0.example.com/tls/keystore/server.key
  cp /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer2.org0.example.com/tls/keystore/*_sk /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer2.org0.example.com/tls/keystore/server.key
  cp /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer3.org0.example.com/tls/keystore/*_sk /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer3.org0.example.com/tls/keystore/server.key

  # Create an MSP config.yaml (why is this not generated by the enrollment by fabric-ca-client?)
  echo "NodeOUs:
    Enable: true
    ClientOUIdentifier:
      Certificate: cacerts/org0-ecert-ca.pem
      OrganizationalUnitIdentifier: client
    PeerOUIdentifier:
      Certificate: cacerts/org0-ecert-ca.pem
      OrganizationalUnitIdentifier: peer
    AdminOUIdentifier:
      Certificate: cacerts/org0-ecert-ca.pem
      OrganizationalUnitIdentifier: admin
    OrdererOUIdentifier:
      Certificate: cacerts/org0-ecert-ca.pem
      OrganizationalUnitIdentifier: orderer" > /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer1.org0.example.com/msp/config.yaml

  cp /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer1.org0.example.com/msp/config.yaml /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer2.org0.example.com/msp/config.yaml
  cp /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer1.org0.example.com/msp/config.yaml /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/orderers/org0-orderer3.org0.example.com/msp/config.yaml
  ' | exec kubectl -n $NS exec deploy/org0-ecert-ca -i -- /bin/sh
}

function create_org1_local_MSP() {

  echo 'set -x
  export FABRIC_CA_CLIENT_HOME=/var/hyperledger/fabric-ca-client
  export FABRIC_CA_CLIENT_TLS_CERTFILES=$FABRIC_CA_CLIENT_HOME/tls-root-cert/tls-ca-cert.pem

  # Each identity in the network needs a registration and enrollment.
  fabric-ca-client register --id.name org1-peer1 --id.secret peerpw --id.type peer --url https://org1-ecert-ca --mspdir $FABRIC_CA_CLIENT_HOME/org1-ecert-ca/rcaadmin/msp
  fabric-ca-client register --id.name org1-peer2 --id.secret peerpw --id.type peer --url https://org1-ecert-ca --mspdir $FABRIC_CA_CLIENT_HOME/org1-ecert-ca/rcaadmin/msp
  fabric-ca-client register --id.name org1-admin --id.secret org1adminpw  --id.type admin   --url https://org1-ecert-ca --mspdir $FABRIC_CA_CLIENT_HOME/org1-ecert-ca/rcaadmin/msp --id.attrs "hf.Registrar.Roles=client,hf.Registrar.Attributes=*,hf.Revoker=true,hf.GenCRL=true,admin=true:ecert,abac.init=true:ecert"

  fabric-ca-client enroll --url https://org1-peer1:peerpw@org1-ecert-ca --csr.hosts org1-peer1,org1-peer-gateway-svc --mspdir /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/peers/org1-peer1.org1.example.com/msp
  fabric-ca-client enroll --url https://org1-peer2:peerpw@org1-ecert-ca --csr.hosts org1-peer2,org1-peer-gateway-svc --mspdir /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/peers/org1-peer2.org1.example.com/msp
  fabric-ca-client enroll --url https://org1-admin:org1adminpw@org1-ecert-ca  --mspdir /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp

  # Each node in the network needs a TLS registration and enrollment.
  fabric-ca-client register --id.name org1-peer1 --id.secret peerpw --id.type peer --url https://org1-tls-ca --mspdir $FABRIC_CA_CLIENT_HOME/tls-ca/tlsadmin/msp
  fabric-ca-client register --id.name org1-peer2 --id.secret peerpw --id.type peer --url https://org1-tls-ca --mspdir $FABRIC_CA_CLIENT_HOME/tls-ca/tlsadmin/msp

  fabric-ca-client enroll --url https://org1-peer1:peerpw@org1-tls-ca --csr.hosts org1-peer1 --mspdir /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/peers/org1-peer1.org1.example.com/tls
  fabric-ca-client enroll --url https://org1-peer2:peerpw@org1-tls-ca --csr.hosts org1-peer2 --mspdir /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/peers/org1-peer2.org1.example.com/tls

  # Copy the TLS signing keys to a fixed path for convenience when launching the peers
  cp /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/peers/org1-peer1.org1.example.com/tls/keystore/*_sk /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/peers/org1-peer1.org1.example.com/tls/keystore/server.key
  cp /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/peers/org1-peer2.org1.example.com/tls/keystore/*_sk /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/peers/org1-peer2.org1.example.com/tls/keystore/server.key

  cp /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/keystore/*_sk /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/keystore/server.key

  # Create local MSP config.yaml
  echo "NodeOUs:
    Enable: true
    ClientOUIdentifier:
      Certificate: cacerts/org1-ecert-ca.pem
      OrganizationalUnitIdentifier: client
    PeerOUIdentifier:
      Certificate: cacerts/org1-ecert-ca.pem
      OrganizationalUnitIdentifier: peer
    AdminOUIdentifier:
      Certificate: cacerts/org1-ecert-ca.pem
      OrganizationalUnitIdentifier: admin
    OrdererOUIdentifier:
      Certificate: cacerts/org1-ecert-ca.pem
      OrganizationalUnitIdentifier: orderer" > /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/peers/org1-peer1.org1.example.com/msp/config.yaml


  cp /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/peers/org1-peer1.org1.example.com/msp/config.yaml /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/peers/org1-peer2.org1.example.com/msp/config.yaml
  cp /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/peers/org1-peer1.org1.example.com/msp/config.yaml /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/config.yaml
  ' | exec kubectl -n $NS exec deploy/org1-ecert-ca -i -- /bin/sh

}

function create_org2_local_MSP() {
  echo 'set -x
  export FABRIC_CA_CLIENT_HOME=/var/hyperledger/fabric-ca-client
  export FABRIC_CA_CLIENT_TLS_CERTFILES=$FABRIC_CA_CLIENT_HOME/tls-root-cert/tls-ca-cert.pem

  # Each identity in the network needs a registration and enrollment.
  fabric-ca-client register --id.name org2-peer1 --id.secret peerpw --id.type peer --url https://org2-ecert-ca --mspdir $FABRIC_CA_CLIENT_HOME/org2-ecert-ca/rcaadmin/msp
  fabric-ca-client register --id.name org2-peer2 --id.secret peerpw --id.type peer --url https://org2-ecert-ca --mspdir $FABRIC_CA_CLIENT_HOME/org2-ecert-ca/rcaadmin/msp
  fabric-ca-client register --id.name org2-admin --id.secret org2adminpw  --id.type admin   --url https://org2-ecert-ca --mspdir $FABRIC_CA_CLIENT_HOME/org2-ecert-ca/rcaadmin/msp --id.attrs "hf.Registrar.Roles=client,hf.Registrar.Attributes=*,hf.Revoker=true,hf.GenCRL=true,admin=true:ecert,abac.init=true:ecert"

  fabric-ca-client enroll --url https://org2-peer1:peerpw@org2-ecert-ca --csr.hosts org2-peer1,org2-peer-gateway-svc --mspdir /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/peers/org2-peer1.org2.example.com/msp
  fabric-ca-client enroll --url https://org2-peer2:peerpw@org2-ecert-ca --csr.hosts org2-peer2,org2-peer-gateway-svc --mspdir /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/peers/org2-peer2.org2.example.com/msp
  fabric-ca-client enroll --url https://org2-admin:org2adminpw@org2-ecert-ca  --mspdir /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp

  # Each node in the network needs a TLS registration and enrollment.
  fabric-ca-client register --id.name org2-peer1 --id.secret peerpw --id.type peer --url https://org2-tls-ca --mspdir $FABRIC_CA_CLIENT_HOME/tls-ca/tlsadmin/msp
  fabric-ca-client register --id.name org2-peer2 --id.secret peerpw --id.type peer --url https://org2-tls-ca --mspdir $FABRIC_CA_CLIENT_HOME/tls-ca/tlsadmin/msp

  fabric-ca-client enroll --url https://org2-peer1:peerpw@org2-tls-ca --csr.hosts org2-peer1 --mspdir /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/peers/org2-peer1.org2.example.com/tls
  fabric-ca-client enroll --url https://org2-peer2:peerpw@org2-tls-ca --csr.hosts org2-peer2 --mspdir /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/peers/org2-peer2.org2.example.com/tls

  # Copy the TLS signing keys to a fixed path for convenience when launching the peers
  cp /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/peers/org2-peer1.org2.example.com/tls/keystore/*_sk /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/peers/org2-peer1.org2.example.com/tls/keystore/server.key
  cp /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/peers/org2-peer2.org2.example.com/tls/keystore/*_sk /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/peers/org2-peer2.org2.example.com/tls/keystore/server.key

  cp /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp/keystore/*_sk /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp/keystore/server.key

  # Create local MSP config.yaml
  echo "NodeOUs:
    Enable: true
    ClientOUIdentifier:
      Certificate: cacerts/org2-ecert-ca.pem
      OrganizationalUnitIdentifier: client
    PeerOUIdentifier:
      Certificate: cacerts/org2-ecert-ca.pem
      OrganizationalUnitIdentifier: peer
    AdminOUIdentifier:
      Certificate: cacerts/org2-ecert-ca.pem
      OrganizationalUnitIdentifier: admin
    OrdererOUIdentifier:
      Certificate: cacerts/org2-ecert-ca.pem
      OrganizationalUnitIdentifier: orderer" > /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/peers/org2-peer1.org2.example.com/msp/config.yaml

  cp /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/peers/org2-peer1.org2.example.com/msp/config.yaml /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/peers/org2-peer2.org2.example.com/msp/config.yaml
  cp /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/peers/org2-peer1.org2.example.com/msp/config.yaml /var/hyperledger/fabric/organizations/peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp/config.yaml
  ' | exec kubectl -n $NS exec deploy/org2-ecert-ca -i -- /bin/sh
}

function create_local_MSP() {
  push_fn "Creating local node MSP"

  create_org0_local_MSP
  create_org1_local_MSP
  create_org2_local_MSP

  pop_fn
}

function network_up() {

  # Kube config
  init_namespace
  init_storage_volumes
  load_org_config

  # Network TLS CAs
  launch_TLS_CAs
  enroll_bootstrap_TLS_CA_users

  # Network ECert CAs
  register_enroll_ECert_CA_bootstrap_users
  launch_ECert_CAs
  enroll_bootstrap_ECert_CA_users

  # Test Network
  create_local_MSP
  launch_orderers
  launch_peers
}

function stop_services() {
  push_fn "Stopping Fabric services"

  # These pods are busy executing `sleep MAX_INT` and do not shut down very quickly...
#  kubectl -n $NS delete deployment/org0-admin-cli --grace-period=0 --force
#  kubectl -n $NS delete deployment/org1-admin-cli --grace-period=0 --force
#  kubectl -n $NS delete deployment/org2-admin-cli --grace-period=0 --force

  kubectl -n $NS delete deployment --all
  kubectl -n $NS delete pod --all
  kubectl -n $NS delete service --all
  kubectl -n $NS delete configmap --all
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
  stop_services
  scrub_org_volumes
}