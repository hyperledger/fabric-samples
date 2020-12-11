set -euo pipefail

FABRIC_VERSION=${FABRIC_VERSION:-2.2}
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
  print "Creating network"
  ./network.sh up createChannel -ca -s couchdb -i "${FABRIC_VERSION}"
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
