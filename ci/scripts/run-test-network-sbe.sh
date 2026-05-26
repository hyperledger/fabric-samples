set -euo pipefail

CHAINCODE_LANGUAGE=${CHAINCODE_LANGUAGE:-typescript}
CHAINCODE_NAME=${CHAINCODE_NAME:-sbe}
CHAINCODE_PATH=${CHAINCODE_PATH:-../asset-transfer-sbe}

function print() {
	GREEN='\033[0;32m'
  NC='\033[0m'
  echo
	echo -e "${GREEN}${1}${NC}"
}

function createNetwork() {
  print "Creating network"
  ./network.sh up createChannel -ca -s couchdb
  print "Deploying ${CHAINCODE_NAME} chaincode"
  ./network.sh deployCC -ccn "${CHAINCODE_NAME}" -ccp "${CHAINCODE_PATH}/chaincode-${CHAINCODE_LANGUAGE}" -ccv 1 -ccs 1 -ccl "${CHAINCODE_LANGUAGE}"
}

function stopNetwork() {
  print "Stopping network"
  ./network.sh down
}

# Run Javascript application
createNetwork
print "Initializing Javascript application"
pushd ../asset-transfer-sbe/application-javascript
npm install
print "Executing app.js"
node app.js
popd
stopNetwork
