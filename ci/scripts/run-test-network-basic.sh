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


# Run Go application
print "Initializing Go application"
export CHAINCODE_NAME=basic_go
deployChaincode
pushd ../asset-transfer-basic/application-go
print "Executing AssetTransfer.go"
go run .
popd

# Run Java application
print "Initializing Java application"
export CHAINCODE_NAME=basic_java
deployChaincode
pushd ../asset-transfer-basic/application-java
print "Executing Gradle Run"
gradle run
popd

# Run Javascript application
print "Initializing Javascript application"
export CHAINCODE_NAME=basic_javascript
deployChaincode
pushd ../asset-transfer-basic/application-javascript
npm install
print "Executing app.js"
node app.js
popd

# Run typescript application
print "Initializing Typescript application"
export CHAINCODE_NAME=basic_typescript
deployChaincode
pushd ../asset-transfer-basic/application-typescript
npm install
print "Building app.ts"
npm run build
print "Running the output app"
node dist/app.js
popd


stopNetwork
