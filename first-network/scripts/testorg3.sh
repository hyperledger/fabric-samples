#!/bin/bash
#
# Copyright IBM Corp. All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0
#

# This script is designed to be run in the org3cli container as the
# final step of the EYFN tutorial. It simply issues a couple of
# chaincode requests through the org3 peers to check that org3 was
# properly added to the network previously setup in the BYFN tutorial.
#

echo
echo " ____    _____      _      ____    _____ "
echo "/ ___|  |_   _|    / \    |  _ \  |_   _|"
echo "\___ \    | |     / _ \   | |_) |   | |  "
echo " ___) |   | |    / ___ \  |  _ <    | |  "
echo "|____/    |_|   /_/   \_\ |_| \_\   |_|  "
echo
echo "Extend your first network (EYFN) test"
echo
CHANNEL_NAME="$1"
DELAY="$2"
LANGUAGE="$3"
TIMEOUT="$4"
VERBOSE="$5"
: ${CHANNEL_NAME:="mychannel"}
: ${TIMEOUT:="10"}
: ${LANGUAGE:="golang"}
: ${VERBOSE:="false"}
LANGUAGE=`echo "$LANGUAGE" | tr [:upper:] [:lower:]`
COUNTER=1
MAX_RETRY=5

CC_SRC_PATH="github.com/chaincode/chaincode_example02/go/"
if [ "$LANGUAGE" = "node" ]; then
	CC_SRC_PATH="/opt/gopath/src/github.com/chaincode/chaincode_example02/node/"
fi

echo "Channel name : "$CHANNEL_NAME

# import functions
. scripts/utils.sh

# Query chaincode on peer0.org3, check if the result is 90
echo "Querying chaincode on peer0.org3..."
chaincodeQuery 0 3 90

# Invoke chaincode on peer0.org1, peer0.org2, and peer0.org3
echo "Sending invoke transaction on peer0.org1 peer0.org2 peer0.org3..."
chaincodeInvoke 0 1 0 2 0 3

# Query on chaincode on peer0.org3, peer0.org2, peer0.org1 check if the result is 80
# We query a peer in each organization, to ensure peers from all organizations are in sync
# and there is no state fork between organizations.
echo "Querying chaincode on peer0.org3..."
chaincodeQuery 0 3 80

echo "Querying chaincode on peer0.org2..."
chaincodeQuery 0 2 80

echo "Querying chaincode on peer0.org1..."
chaincodeQuery 0 1 80


echo
echo "========= All GOOD, EYFN test execution completed =========== "
echo

echo
echo " _____   _   _   ____   "
echo "| ____| | \ | | |  _ \  "
echo "|  _|   |  \| | | | | | "
echo "| |___  | |\  | | |_| | "
echo "|_____| |_| \_| |____/  "
echo

exit 0
