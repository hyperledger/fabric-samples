#!/usr/bin/env bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#
set -euo pipefail

function print() {
  GREEN='\033[0;32m'
  NC='\033[0m'
  echo
  echo -e "${GREEN}${1}${NC}"
}

function touteSuite() {
  createCluster
}

function quitterLaScene() {
  destroyCluster
}

function createCluster() {
  print "Initializing KIND Kubernetes cluster"
  just kind
}

function destroyCluster() {
  print "Destroying KIND Kubernetes cluster"
  just destroy
  just unkind
}

# fabric CLI binaries + config
FABRIC_VERSION=2.5.0-beta
FABRIC_CA_VERSION=1.5.6-beta3

curl -sSL https://raw.githubusercontent.com/hyperledger/fabric/main/scripts/install-fabric.sh | bash -s -- binary --fabric-version $FABRIC_VERSION --ca-version $FABRIC_CA_VERSION
export PATH=${PWD}/bin:$PATH
export FABRIC_CFG_PATH=${PWD}/config

# Set The Stage: a local KIND cluster
touteSuite
trap "quitterLaScene" EXIT


# Act I: launch Fabric services
#export FABRIC_CFG_PATH=${PWD}/config

just start org0
just start org1
just start org2

just enroll org0
just enroll org1
just enroll org2

just check-network


# Act II: Build a Consortium

just export-msp org0
just export-msp org1
just export-msp org2

just create-genesis-block
just inspect-genesis-block

just join org0
just join org1
just join org2


# Act III: Chaincode and application

just install-cc org1
just install-cc org2

# org1:
export ORG=org1
export MSP_ID=Org1MSP

export $(just show-context $MSP_ID $ORG peer1)

print "env context:"
export

print "querying cc as org1"
peer chaincode query \
  -n asset-transfer \
  -C mychannel \
  -c '{"Args":["org.hyperledger.fabric:GetMetadata"]}'

# org2:
export ORG=org2
export MSP_ID=Org2MSP

export $(just show-context $MSP_ID $ORG peer1)

peer chaincode query \
  -n asset-transfer \
  -C mychannel \
  -c '{"Args":["org.hyperledger.fabric:GetMetadata"]}'


# Client application:  (still org2 context)

export USER_MSP_DIR=$PWD/organizations/$ORG/enrollments/${ORG}user/msp
export PRIVATE_KEY=$USER_MSP_DIR/keystore/key.pem
export CERTIFICATE=$USER_MSP_DIR/signcerts/cert.pem
export TLS_CERT=$CORE_PEER_TLS_ROOTCERT_FILE
export ENDPOINT=${ORG}-peer-gateway.${ORG}.localho.st:443

( pushd ../full-stack-asset-transfer-guide/applications/trader-typescript \
  && npm install
  && npm start getAllAssets
  && npm start create banana bananaman yellow
  && npm start getAllAssets )

