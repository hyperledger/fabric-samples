#!/bin/bash
#
# This script generates crypto, starts Fabric, deploys the chaincode and starts the token nodes.

set -Eeuo pipefail

# Checks
ls scripts/up.sh || { echo "run this script from the root directory: ./scripts/up.sh"; exit 1; }
docker-compose version
git --version
go version
tokengen version || { echo "install tokengen (see readme)"; exit 1; }

[ ! -f data/auditor/kvs ] || { echo "delete existing crypto before running ./scripts/up.sh:

# ./scripts/down.sh"; exit 1; }
TEST_NETWORK_HOME="${TEST_NETWORK_HOME:-$(pwd)/../test-network}"
ls "$TEST_NETWORK_HOME/network.sh" 1> /dev/null || { echo "Set the TEST_NETWORK_HOME environment variable to the directory of your fabric-samples/test-network; e.g.:

export TEST_NETWORK_HOME=\"$TEST_NETWORK_HOME\"
"; exit 1; }

# Generate identities for the nodes, issuer, auditor and owner
mkdir -p keys/ca
docker-compose -f compose-ca.yaml up -d
while ! fabric-ca-client getcainfo -u localhost:27054 2>/dev/null; do echo "waiting for CA to start..." && sleep 1; done
./scripts/enroll-users.sh
# generate the parameters needed for the tokenchaincode
tokengen gen dlog --base 300 --exponent 5 --issuers keys/issuer/iss/msp --idemix keys/owner1/wallet/alice --auditors keys/auditor/aud/msp --output tokenchaincode


# Start Fabric network
bash "$TEST_NETWORK_HOME/network.sh" up createChannel
# copy the keys and certs of the peers, orderer and the client user
mkdir -p keys/fabric
cp -r "$TEST_NETWORK_HOME/organizations" keys/fabric/

# Install and start tokenchaincode as a service
INIT_REQUIRED="--init-required" "$TEST_NETWORK_HOME/network.sh" deployCCAAS  -ccn tokenchaincode -ccp "$(pwd)/tokenchaincode" -cci "init" -ccs 1

# Start token nodes
mkdir -p data/auditor data/issuer data/owner1 data/owner2
docker-compose up -d
echo "
Ready!

Visit http://localhost:8080 in your browser to view the API documentation and try some transactions.
"
