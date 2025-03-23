#!/usr/bin/env bash

set -euo pipefail

CHAINCODE_LANGUAGE=${CHAINCODE_LANGUAGE:-go}
CHAINCODE_PATH=${CHAINCODE_PATH:-../asset-transfer-basic}

function print() {
	GREEN='\033[0;32m'
  NC='\033[0m'
  echo
	echo -e "${GREEN}${1}${NC}"
}

function createNetwork() {
  print "Creating 3 Org network"
  ./network.sh up createChannel -ca -s couchdb
  cd addOrg3
  ./addOrg3.sh up -ca -s couchdb
  cd ..
}

function deployChaincode() {
  print "Deploying ${CHAINCODE_NAME} chaincode"
  ./network.sh deployCC -ccn "${CHAINCODE_NAME}" -ccp "${CHAINCODE_PATH}/chaincode-${CHAINCODE_LANGUAGE}" -ccv 1 -ccs 1 -ccl "${CHAINCODE_LANGUAGE}"
}

function stopNetwork() {
  print "Stopping network"
  ./network.sh down
}

# Set up one test network to run each test scenario.
# Each test will create an independent scope by installing a new chaincode contract to the channel.
createNetwork

echo 'go install pkcs11 enabled fabric-ca-client'
GOBIN=${PWD}/../bin go install -tags pkcs11 github.com/hyperledger/fabric-ca/cmd/fabric-ca-client@latest
fabric-ca-client version

# Run Typescript HSM gateway application
print "Initializing Typescript HSM Gateway application"
export CHAINCODE_NAME=ts_hsm_gateway
deployChaincode
print "Initializing Typescript HSM gateway application"
pushd ../hardware-security-module/scripts/
print "Enroll and register User in HSM"
./generate-hsm-user.sh HSMUser
pushd ../application-typescript/
print "install dependencies and prepare for running"
npm install
print "Running the output app"
npm run start
popd
popd

# Run Go HSM gateway application
print "Initializing Go HSM gateway application"
export CHAINCODE_NAME=go_hsm
deployChaincode
pushd ../hardware-security-module/scripts/
print "Register and enroll user in HSM"
./generate-hsm-user.sh HSMUser
pushd ../application-go
print "Running the output app"
go run -tags pkcs11 .
popd
popd



stopNetwork
