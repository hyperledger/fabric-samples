set -euo pipefail

CHAINCODE_LANGUAGE=${CHAINCODE_LANGUAGE:-go}
CHAINCODE_NAME=${CHAINCODE_NAME:-basic}
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
  print "Deploying ${CHAINCODE_NAME} chaincode"
  ./network.sh deployCC -ccn "${CHAINCODE_NAME}" -ccp "${CHAINCODE_PATH}/chaincode-${CHAINCODE_LANGUAGE}" -ccv 1 -ccs 1 -ccl "${CHAINCODE_LANGUAGE}"
}

function stopNetwork() {
  print "Stopping network"
  ./network.sh down
}

# Run Go application
createNetwork
print "Initializing Go application"
pushd ../asset-transfer-basic/application-go
print "Executing AssetTransfer.go"
go run .
popd
stopNetwork

# Run Java application
createNetwork
print "Initializing Java application"
pushd ../asset-transfer-basic/application-java
print "Executing Gradle Run"
gradle run
popd
stopNetwork

# Run Java application using gateway
createNetwork
print "Initializing Java application"
pushd ../asset-transfer-basic/application-gateway-java
print "Executing Gradle Run"
./gradlew run
popd
stopNetwork

# Run Javascript application
createNetwork
print "Initializing Javascript application"
pushd ../asset-transfer-basic/application-javascript
npm install
print "Executing app.js"
node app.js
popd
stopNetwork

# Run typescript application
createNetwork
print "Initializing Typescript application"
pushd ../asset-transfer-basic/application-typescript
npm install
print "Building app.ts"
npm run build
print "Running the output app"
node dist/app.js
popd
stopNetwork

# Run gateway typescript application
createNetwork
print "Initializing Typescript gateway application"
pushd ../asset-transfer-basic/application-gateway-typescript
npm install
print "Building app.ts"
npm run build
print "Running the output app"
node dist/app.js
popd
stopNetwork

# Run typescript HSM application
createNetwork
print "Initializing Typescript HSM application"
pushd ../asset-transfer-basic/application-typescript-hsm
print "Setup SoftHSM"
export SOFTHSM2_CONF=$PWD/softhsm2.conf
print "install dependencies"
npm install
print "Building app.ts"
npm run build
print "Running the output app"
node dist/app.js
popd
stopNetwork

# Run Typescript HSM gateway application
echo 'Delete fabric-ca-client from samples bin'
rm ../bin/fabric-ca-client
echo 'go install pkcs11 enabled fabric-ca-client'
go install -tags pkcs11 github.com/hyperledger/fabric-ca/cmd/fabric-ca-client@latest
createNetwork
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
stopNetwork

# Run Go HSM gateway application
createNetwork
print "Initializing Go HSM gateway application"
pushd ../hardware-security-module/scripts/
print "Register and enroll user in HSM"
./generate-hsm-user.sh HSMUser
pushd ../application-go
print "Running the output app"
go run -tags pkcs11 .
popd
popd
stopNetwork

# Run Go gateway application
createNetwork
print "Initializing Go gateway application"
pushd ../asset-transfer-basic/application-gateway-go
print "Executing AssetTransfer.go"
go run .
popd
stopNetwork

# Run off-chain data TypeScript application
createNetwork
print "Initializing Typescript off-chain data application"
pushd ../off_chain_data/application-typescript
rm -f checkpoint.json store.log
npm install
print "Running the output app"
SIMULATED_FAILURE_COUNT=1 npm start getAllAssets transact getAllAssets listen
SIMULATED_FAILURE_COUNT=1 npm start listen
popd
stopNetwork

# Run off-chain data Java application
createNetwork
print "Initializing Typescript off-chain data application"
pushd ../off_chain_data/application-java
rm -f app/checkpoint.json app/store.log
print "Running the output app"
SIMULATED_FAILURE_COUNT=1 ./gradlew run --quiet --args='getAllAssets transact getAllAssets listen'
SIMULATED_FAILURE_COUNT=1 ./gradlew run --quiet --args=listen
popd
stopNetwork
