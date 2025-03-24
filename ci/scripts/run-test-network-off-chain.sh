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

# Run off-chain data TypeScript application
export CHAINCODE_NAME=ts_off_chain_data
deployChaincode
print "Initializing Typescript off-chain data application"
pushd ../off_chain_data/application-typescript
rm -f checkpoint.json store.log
npm install
print "Running the Typescript app"
SIMULATED_FAILURE_COUNT=1 npm start getAllAssets transact getAllAssets listen
SIMULATED_FAILURE_COUNT=1 npm start listen
popd

# Run off-chain data Go application
export CHAINCODE_NAME=go_off_chain_data
deployChaincode
print "Initializing Go off-chain data application"
pushd ../off_chain_data/application-go
rm -f checkpoint.json store.log
print "Running the Go app"
SIMULATED_FAILURE_COUNT=1 go run . getAllAssets transact getAllAssets listen
SIMULATED_FAILURE_COUNT=1 go run . listen
popd

# Run off-chain data Java application
#createNetwork
export CHAINCODE_NAME=off_chain_data
deployChaincode
print "Initializing off-chain data application"
pushd ../off_chain_data/application-java
rm -f app/checkpoint.json app/store.log
print "Running the Gradle app"
SIMULATED_FAILURE_COUNT=1 ./gradlew run --quiet --args='getAllAssets transact getAllAssets listen'
SIMULATED_FAILURE_COUNT=1 ./gradlew run --quiet --args=listen
pushd app
rm -f checkpoint.json store.log
print "Executing Maven application"
SIMULATED_FAILURE_COUNT=1 mvn --batch-mode --no-transfer-progress compile exec:java -Dexec.mainClass=App -Dexec.args='getAllAssets transact getAllAssets listen'
SIMULATED_FAILURE_COUNT=1 mvn --batch-mode --no-transfer-progress compile exec:java -Dexec.mainClass=App -Dexec.args=listen
popd
popd

stopNetwork
