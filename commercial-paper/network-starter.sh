#!/bin/bash
#
# SPDX-License-Identifier: Apache-2.0

function _exit(){
    printf "Exiting:%s\n" "$1"
    exit -1
}

# Exit on first error, print all commands.
set -ev
set -o pipefail

# Where am I?
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

export FABRIC_CFG_PATH="${DIR}/../config"

cd "${DIR}/../test-network/"

docker kill cliDigiBank cliMagnetoCorp logspout || true
./network.sh down
./network.sh up createChannel -s couchdb -i 2.0.0

# Copy the connection profiles so they are in the correct organizations. 
cp "${DIR}/../test-network/organizations/peerOrganizations/org1.example.com/connection-org1.yaml" "${DIR}/organization/digibank/gateway/"
cp "${DIR}/../test-network/organizations/peerOrganizations/org2.example.com/connection-org2.yaml" "${DIR}/organization/magnetocorp/gateway/"

echo Suggest that you monitor the docker containers by running
echo "./organization/magnetocorp/configuration/cli/monitordocker.sh net_test"
