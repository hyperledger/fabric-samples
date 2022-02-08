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

# Fabric version and Docker registry source: use the latest stable tag image from JFrog
export FABRIC_VERSION=${FABRIC_VERSION:-2.4}
export TEST_NETWORK_FABRIC_CONTAINER_REGISTRY=hyperledger-fabric.jfrog.io
export TEST_NETWORK_FABRIC_VERSION=amd64-${FABRIC_VERSION}-stable
export TEST_NETWORK_FABRIC_CA_VERSION=amd64-${FABRIC_VERSION}-stable

# test-network-k8s parameters
export TEST_TAG=$(git describe)
export TEST_NETWORK_KIND_CLUSTER_NAME=${TEST_NETWORK_KIND_CLUSTER_NAME:-kind}
export TEST_NETWORK_CHAINCODE_NAME=${TEST_NETWORK_CHAINCODE_NAME:-asset-transfer-basic}
export TEST_NETWORK_CHAINCODE_IMAGE=${TEST_NETWORK_CHAINCODE_NAME}:${TEST_TAG}
export TEST_NETWORK_CHAINCODE_PATH=${TEST_NETWORK_CHAINCODE_PATH:-../asset-transfer-basic/chaincode-external}

# gateway client application parameters
export GATEWAY_CLIENT_APPLICATION_PATH=${GATEWAY_CLIENT_APPLICATION_PATH:-../asset-transfer-basic/application-gateway-${CLIENT_LANGUAGE}}
export CHANNEL_NAME=${TEST_NETWORK_CHANNEL_NAME:-mychannel}
export CHAINCODE_NAME=${TEST_NETWORK_CHAINCODE_NAME:-asset-transfer-basic}
export MSP_ID=${MSP_ID:-Org1MSP}
export CRYPTO_PATH=${CRYPTO_PATH:-../../test-network-k8s/build/organizations/peerOrganizations/org1.example.com}
export KEY_DIRECTORY_PATH=${KEY_DIRECTORY_PATH:-../../test-network-k8s/build/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/keystore}
export CERT_PATH=${CERT_PATH:-../../test-network-k8s/build/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/signcerts/cert.pem}
export TLS_CERT_PATH=${TLS_CERT_PATH:-../../test-network-k8s/build/organizations/peerOrganizations/org1.example.com/msp/tlscacerts/org1-tls-ca.pem}
export PEER_ENDPOINT=${PEER_ENDPOINT:-localhost:7051}
export PEER_HOST_ALIAS=${PEER_HOST_ALIAS:-org1-peer1}

function print() {
  GREEN='\033[0;32m'
  NC='\033[0m'
  echo
  echo -e "${GREEN}${1}${NC}"
}

function touteSuite() {
  createCluster
  buildChaincodeImage
}

function quitterLaScene() {
  destroyCluster
  scrubCCImages
}

function createCluster() {
  print "Initializing KIND Kubernetes cluster"
  ./network kind
}

function destroyCluster() {
  print "Destroying KIND Kubernetes cluster"
  ./network unkind
}

function buildChaincodeImage() {
  print "Building chaincode image $TEST_NETWORK_CHAINCODE_IMAGE"
  ${CONTAINER_CLI} build -t $TEST_NETWORK_CHAINCODE_IMAGE $TEST_NETWORK_CHAINCODE_PATH

  # todo: work with local reg, or k3s, or KIND, or ...
  kind load docker-image $TEST_NETWORK_CHAINCODE_IMAGE
}

function scrubCCImages() {
  print "Scrubbing chaincode images"
  ${CONTAINER_CLI} rmi $TEST_NETWORK_CHAINCODE_IMAGE
}

function createNetwork() {
  print "Launching network"
  ./network up
  ./network channel create

  print "Opening gateway port-forward to 'localhost:7051'"
  kubectl -n test-network port-forward svc/org1-peer1 7051:7051 &

  print "Deploying chaincode"
  ./network chaincode deploy

  print "Extracting certificates"
  kubectl \
    -n test-network \
    exec deploy/org1-peer1 \
    -c main \
    -- tar zcf - -C /var/hyperledger/fabric organizations/peerOrganizations/org1.example.com \
    | tar zxvf - -C ../test-network-k8s/build/
}

function stopNetwork() {
  pkill -f "port-forward"

  print "Stopping network"
  ./network down

  print "Cleaning client certificates"
  rm -rf ../test-network-k8s/build/
}

# Set up the suite with a KIND cluster
touteSuite
trap "quitterLaScene" EXIT

# invoke / query
createNetwork

print "Inserting and querying assets"
( ./network chaincode invoke '{"Args":["InitLedger"]}' \
  && sleep 5 \
  && ./network chaincode query '{"Args":["ReadAsset","asset1"]}' )
print "OK"

print "Running rest-easy test"
( ./network rest-easy \
  && sleep 5 \
  && export SAMPLE_APIKEY='97834158-3224-4CE7-95F9-A148C886653E' \
  && curl -s --header "X-Api-Key: ${SAMPLE_APIKEY}" "http://localhost/api/assets/asset1" | jq \
  && curl -s --insecure --header "X-Api-Key: ${SAMPLE_APIKEY}" "https://localhost/api/assets/asset1" | jq )
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
