#!/bin/bash

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


# Run Go gateway application
print "Initializing Go gateway application"
export CHAINCODE_NAME=go_gateway
deployChaincode
pushd ../asset-transfer-basic/application-gateway-go
print "Executing AssetTransfer.go"
go run .
popd


# Run gateway typescript application
print "Initializing Typescript gateway application"
export CHAINCODE_NAME=typescript_gateway
deployChaincode
pushd ../asset-transfer-basic/application-gateway-typescript
npm install
print "Building app.ts"
npm run build
print "Running the output app"
node dist/app.js
popd


# Run Java application using gateway
print "Initializing Java application"
export CHAINCODE_NAME=java_gateway
deployChaincode
pushd ../asset-transfer-basic/application-gateway-java
print "Executing Gradle Run"
./gradlew run
popd


stopNetwork
