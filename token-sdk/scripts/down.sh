#!/bin/bash
#
# This script fully tears down and deletes all artifacts from the sample network that was started with ./scripts/up.sh.


ls scripts/down.sh || { echo "run this script from the root directory: ./scripts/down.sh"; exit 1; }
TEST_NETWORK_HOME="${TEST_NETWORK_HOME:-$(pwd)/../test-network}"
ls "$TEST_NETWORK_HOME/network.sh" 1> /dev/null || { echo "Set the TEST_NETWORK_HOME environment variable to the directory of your fabric-samples/test-network; e.g.:

export TEST_NETWORK_HOME=\"$TEST_NETWORK_HOME\"
"; exit 1; }

docker-compose down
docker-compose -f compose-ca.yaml down

docker stop peer0org1_tokenchaincode_ccaas peer0org2_tokenchaincode_ccaas
bash "$TEST_NETWORK_HOME"/network.sh down

rm -rf keys
rm -rf data
rm tokenchaincode/zkatdlog_pp.json
