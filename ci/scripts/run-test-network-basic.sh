set -euo pipefail

FABRIC_VERSION=${FABRIC_VERSION:-2.2}
CHAINCODE_LANGUAGE=${CHAINCODE_LANGUAGE:-go}
CHAINCODE_NAME=${CHAINCODE_NAME:-basic}

function print() {
	GREEN='\033[0;32m'
  NC='\033[0m'
  echo
	echo -e "${GREEN}${1}${NC}"
}

print "Creating network"
./network.sh up createChannel -ca -s couchdb -i "${FABRIC_VERSION}"
print "Deploying ${CHAINCODE_NAME} chaincode"
./network.sh deployCC -ccn "${CHAINCODE_NAME}" -ccv 1 -ccs 1 -ccl "${CHAINCODE_LANGUAGE}"


# Run Javascript application
print "Initializing Javascript application"
pushd ../asset-transfer-basic/application-javascript
npm install
print "Executing app.js"
node app.js
popd

print "Stopping network"
./network.sh down
