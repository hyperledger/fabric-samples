set -euo pipefail

FABRIC_VERSION=${FABRIC_VERSION:-2.2}
CHAINCODE_LANGUAGE=${CHAINCODE_LANGUAGE:-javascript}
CHAINCODE_NAME=${CHAINCODE_NAME:-events}
CHAINCODE_PATH=${CHAINCODE_PATH:-../asset-transfer-events}

function print() {
	GREEN='\033[0;32m'
  NC='\033[0m'
  echo
	echo -e "${GREEN}${1}${NC}"
}

function createNetwork() {
  print "Creating network"
  ./network.sh up createChannel -ca
  print "Deploying ${CHAINCODE_NAME} chaincode"
  ./network.sh deployCC -ccn "${CHAINCODE_NAME}" -ccp "${CHAINCODE_PATH}/chaincode-${CHAINCODE_LANGUAGE}" -ccl "${CHAINCODE_LANGUAGE}" -ccep "OR('Org1MSP.peer','Org2MSP.peer')"
}

function stopNetwork() {
  print "Stopping network"
  ./network.sh down
}

# Run Javascript application
createNetwork
print "Initializing Javascript application"
pushd ../asset-transfer-events/application-javascript
npm install
print "Executing app.js"
node app.js
popd
stopNetwork
print "Remove wallet storage"
rm -R ../asset-transfer-events/application-javascript/wallet
