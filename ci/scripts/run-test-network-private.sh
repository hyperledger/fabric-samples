set -euo pipefail

CHAINCODE_LANGUAGE=${CHAINCODE_LANGUAGE:-go}
CHAINCODE_NAME=${CHAINCODE_NAME:-private}
CHAINCODE_PATH=${CHAINCODE_PATH:-../asset-transfer-private-data}

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
  ./network.sh deployCC -ccn "${CHAINCODE_NAME}" -ccp "${CHAINCODE_PATH}/chaincode-${CHAINCODE_LANGUAGE}" -ccv 1 -ccs 1 -ccl "${CHAINCODE_LANGUAGE}" -ccep "OR('Org1MSP.peer','Org2MSP.peer')" -cccg ../asset-transfer-private-data/chaincode-go/collections_config.json
}

function stopNetwork() {
  print "Stopping network"
  ./network.sh down
}

# Run Javascript application
createNetwork
print "Initializing Javascript application"
pushd ../asset-transfer-private-data/application-javascript
npm install
print "Executing app.js"
node app.js
popd
stopNetwork


# Run typescript gateway application
createNetwork
print "Initializing typescript application"
pushd ../asset-transfer-private-data/application-gateway-typescript
npm install
print "Build typescript app"
npm run build
print "Executing app.js"
npm start
popd
stopNetwork
