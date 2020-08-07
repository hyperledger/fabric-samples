set -euo pipefail

FABRIC_VERSION=${FABRIC_VERSION:-2.2}
CHAINCODE_NAME=${CHAINCODE_NAME:-ledger}

function print() {
	GREEN='\033[0;32m'
  NC='\033[0m'
  echo
	echo -e "${GREEN}${1}${NC}"
}

print "Creating network"
./network.sh up createChannel -ca -s couchdb -i "${FABRIC_VERSION}"

print "Deploying ${CHAINCODE_NAME} go chaincode"
./network.sh deployCC -ccn "${CHAINCODE_NAME}" -ccv 1.0 -ccs 1 -ccl go

# Run Javascript application against the go chaincode
print "Initializing Javascript application"
pushd ../asset-transfer-ledger-queries/application-javascript
npm install
print "Executing app.js"
node app.js
popd

print "Deploying ${CHAINCODE_NAME} javascript chaincode"
./network.sh deployCC -ccn "${CHAINCODE_NAME}" -ccv 2.0 -ccs 2 -ccl javascript

# Run Javascript application against the javascript chaincode
print "Initializing Javascript application"
pushd ../asset-transfer-ledger-queries/application-javascript
npm install
print "Executing app.js"
node app.js skipInit
popd

print "Stopping network"
./network.sh down
rm -R ../asset-transfer-ledger-queries/application-javascript/wallet