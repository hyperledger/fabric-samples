#!/bin/bash -e
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#
set -euo pipefail

# Test matrix parameters
export CONTAINER_CLI=${CONTAINER_CLI:-docker}
export CLIENT_LANGUAGE=${CLIENT_LANGUAGE:-typescript}
export CHAINCODE_LANGUAGE=${CHAINCODE_LANGUAGE:-java}
export TEST_NETWORK_CHAINCODE_BUILDER=${CHAINCODE_BUILDER:-ccaas}

# test-network-k8s parameters
export TEST_TAG=$(git describe)
export TEST_NETWORK_KIND_CLUSTER_NAME=${TEST_NETWORK_KIND_CLUSTER_NAME:-kind}

# asset-transfer-basic chaincode target
export TEST_NETWORK_CHAINCODE_NAME=${TEST_NETWORK_CHAINCODE_NAME:-asset-transfer-basic}
export TEST_NETWORK_CHAINCODE_PATH=${TEST_NETWORK_CHAINCODE_PATH:-$PWD/../asset-transfer-basic/chaincode-${CHAINCODE_LANGUAGE}}
export TEST_NETWORK_CHAINCODE_IMAGE=${TEST_NETWORK_CHAINCODE_IMAGE:-fabric-samples/asset-transfer-basic/chaincode-${CHAINCODE_LANGUAGE}}

# gateway client application parameters
export GATEWAY_CLIENT_APPLICATION_PATH=${GATEWAY_CLIENT_APPLICATION_PATH:-../asset-transfer-basic/application-gateway-${CLIENT_LANGUAGE}}
export CHANNEL_NAME=${TEST_NETWORK_CHANNEL_NAME:-mychannel}
export CHAINCODE_NAME=${TEST_NETWORK_CHAINCODE_NAME:-asset-transfer-basic}
export MSP_ID=${MSP_ID:-Org1MSP}
export CRYPTO_PATH=${CRYPTO_PATH:-../../test-network-k8s/build/channel-msp/peerOrganizations/org1}
export KEY_DIRECTORY_PATH=${KEY_DIRECTORY_PATH:-../../test-network-k8s/build/enrollments/org1/users/org1admin/msp/keystore}
export CERT_DIRECTORY_PATH=${CERT_DIRECTORY_PATH:-../../test-network-k8s/build/enrollments/org1/users/org1admin/msp/signcerts}
export TLS_CERT_PATH=${TLS_CERT_PATH:-../../test-network-k8s/build/channel-msp/peerOrganizations/org1/msp/tlscacerts/tlsca-signcert.pem}
export PEER_ENDPOINT=${PEER_ENDPOINT:-org1-peer1.localho.st:443}
export PEER_HOST_ALIAS=${PEER_HOST_ALIAS:-org1-peer1.localho.st}

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
  ./network kind
  ./network cluster init
}

function destroyCluster() {
  print "Destroying KIND Kubernetes cluster"
  ./network unkind
}

function createNetwork() {
  print "Launching network"
  ./network up
  ./network channel create

  print "Deploying chaincode"
  ./network chaincode deploy $CHAINCODE_NAME $TEST_NETWORK_CHAINCODE_PATH
}

function stopNetwork() {
  print "Stopping network"
  ./network down
}

# Set up the suite with a KIND cluster
touteSuite
trap "quitterLaScene" EXIT

createNetwork

print "Inserting and querying assets"
( ./network chaincode metadata $CHAINCODE_NAME \
  && ./network chaincode invoke $CHAINCODE_NAME '{"Args":["InitLedger"]}' \
  && sleep 5 \
  && ./network chaincode query $CHAINCODE_NAME '{"Args":["ReadAsset","asset1"]}' )
print "OK"

print "Running rest-easy test"
( ./network rest-easy \
  && sleep 5 \
  && export SAMPLE_APIKEY='97834158-3224-4CE7-95F9-A148C886653E' \
  && curl -s --header "X-Api-Key: ${SAMPLE_APIKEY}" "http://fabric-rest-sample.localho.st/api/assets/asset1" | jq )
print "OK"

stopNetwork

# Run the basic-asset-transfer basic application
createNetwork
print "Running Gateway client application"
( pushd ${GATEWAY_CLIENT_APPLICATION_PATH} \
  && npm install \
  && npm start )
print "OK"
stopNetwork

# Run additional test ...
# Run additional test ...
# Run additional test ...

# destroyCluster will be invoked on EXIT trap handler at the end of this suite.
