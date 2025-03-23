#!/usr/bin/env bash

set -euo pipefail

CHAINCODE_LANGUAGE=${CHAINCODE_LANGUAGE:-go}
CHAINCODE_PATH=${CHAINCODE_PATH:-../asset-transfer-basic}
ORDERER_TYPE=${ORDERER_TYPE:-raft}
CRYPTO=${CRYPTO:-ca}

CRYPTO_OPTION=""
if [ "$CRYPTO" == "ca" ]; then
  CRYPTO_OPTION="-ca"
fi

function print() {
	GREEN='\033[0;32m'
  NC='\033[0m'
  echo
	echo -e "${GREEN}${1}${NC}"
}

function createNetworkWithRaft() {
  print "Creating 3 Org network with Raft Orderers"
  ./network.sh up createChannel ${CRYPTO_OPTION} -s couchdb
  cd addOrg3
  ./addOrg3.sh up ${CRYPTO_OPTION} -s couchdb
  cd ..
}

function createNetworkWithBFT() {
  print "Creating 2 Org network with BFT Orderers"
  ./network.sh up createChannel -bft ${CRYPTO_OPTION}
}

function createNetwork() {
  if  [ "${ORDERER_TYPE}" == "bft" ]; then
    createNetworkWithBFT
  else
    createNetworkWithRaft
  fi
}

function deployChaincode() {
  print "Deploying ${CHAINCODE_NAME} chaincode"
  ./network.sh deployCC -ccn "${CHAINCODE_NAME}" -ccp "${CHAINCODE_PATH}/chaincode-${CHAINCODE_LANGUAGE}" -ccv 1 -ccs 1 -ccl "${CHAINCODE_LANGUAGE}"
}

function stopNetwork() {
  print "Stopping network"
  ./network.sh down
}

# print all executed commands to assist with debug in CI environment
set -x

# Set up one test network to run each test scenario.
# Each test will create an independent scope by installing a new chaincode contract to the channel.
createNetwork


# Run Go application
print "Initializing Go application"
export CHAINCODE_NAME=go_gateway
deployChaincode
pushd ../asset-transfer-basic/application-gateway-go
print "Executing AssetTransfer.go"
go run .
popd


# Run TypeScript application
print "Initializing TypeScript application"
export CHAINCODE_NAME=typescript_gateway
deployChaincode
pushd ../asset-transfer-basic/application-gateway-typescript
npm install
print "Start application"
npm start
popd


# Run JavaScript application
print "Initializing JavaScript application"
export CHAINCODE_NAME=javascript_gateway
deployChaincode
pushd ../asset-transfer-basic/application-gateway-javascript
npm install
print "Start application"
npm start
popd


# Run Java application
print "Initializing Java application"
export CHAINCODE_NAME=java_gateway
deployChaincode
pushd ../asset-transfer-basic/application-gateway-java
print "Executing Gradle Run"
./gradlew run
print "Executing Maven Run"
mvn --batch-mode --no-transfer-progress compile exec:java -Dexec.mainClass=App
popd


stopNetwork

{ set +x; } 2>/dev/null
