#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#
# Exit on first error
set -e

# don't rewrite paths for Windows Git Bash users
export MSYS_NO_PATHCONV=1
starttime=$(date +%s)
export TIMEOUT=10
export DELAY=3

# launch network; create channel and join peer to channel
pushd ../test-network
./network.sh down

echo "Bring up test network"
./network.sh up createChannel -ca
./network.sh deployCC -ccn bigdatacc -ccp ../high-throughput/chaincode-go/ -ccep "OR('Org1MSP.peer','Org2MSP.peer')" -cci Init
popd
cat <<EOF

Total setup execution time : $(($(date +%s) - starttime)) secs ...

EOF
