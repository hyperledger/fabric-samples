#!/bin/bash
#
# Copyright IBM Corp. All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0
#

# This script is designed to be run in the org3cli container as the
# second step of the EYFN tutorial. It joins the org3 peers to the
# channel previously setup in the BYFN tutorial and install the
# chaincode as version 2.0 on peer0.org3.
#

echo
echo "========= Getting Org3 on to your first network ========= "
echo
CHANNEL_NAME="$1"
DELAY="$2"
CC_SRC_LANGUAGE="$3"
TIMEOUT="$4"
VERBOSE="$5"
: ${CHANNEL_NAME:="mychannel"}
: ${DELAY:="3"}
: ${CC_SRC_LANGUAGE:="go"}
: ${TIMEOUT:="10"}
: ${VERBOSE:="false"}
CC_SRC_LANGUAGE=`echo "$CC_SRC_LANGUAGE" | tr [:upper:] [:lower:]`
COUNTER=1
MAX_RETRY=5
PACKAGE_ID=""

if [ "$CC_SRC_LANGUAGE" = "go" -o "$CC_SRC_LANGUAGE" = "golang" ]; then
	CC_RUNTIME_LANGUAGE=golang
	CC_SRC_PATH="github.com/hyperledger/fabric-samples/chaincode/abstore/go/"
elif [ "$CC_SRC_LANGUAGE" = "javascript" ]; then
	CC_RUNTIME_LANGUAGE=node # chaincode runtime language is node.js
	CC_SRC_PATH="/opt/gopath/src/github.com/hyperledger/fabric-samples/chaincode/abstore/javascript/"
elif [ "$CC_SRC_LANGUAGE" = "java" ]; then
	CC_RUNTIME_LANGUAGE=java
	CC_SRC_PATH="/opt/gopath/src/github.com/hyperledger/fabric-samples/chaincode/abstore/java/"
else
	echo The chaincode language ${CC_SRC_LANGUAGE} is not supported by this script
	echo Supported chaincode languages are: go, javascript, java
	exit 1
fi

# import utils
. scripts/utils.sh

echo "Fetching channel config block from orderer..."
set -x
peer channel fetch 0 $CHANNEL_NAME.block -o orderer.example.com:7050 -c $CHANNEL_NAME --tls --cafile $ORDERER_CA >&log.txt
res=$?
set +x
cat log.txt
verifyResult $res "Fetching config block from orderer has Failed"

joinChannelWithRetry 0 3
echo "===================== peer0.org3 joined channel '$CHANNEL_NAME' ===================== "
joinChannelWithRetry 1 3
echo "===================== peer1.org3 joined channel '$CHANNEL_NAME' ===================== "

## at first we package the chaincode
packageChaincode 1 0 3

echo "Installing chaincode on peer0.org3..."
installChaincode 0 3

## query whether the chaincode is installed
queryInstalled 0 3

## sanity check: expect the chaincode to be already committed
queryCommitted 1 0 3

## approve it for our org, so that our peers know what package to invoke
approveForMyOrg 1 0 3

# Query on chaincode on peer0.org3, check if the result is 90
echo "Querying chaincode on peer0.org3..."
chaincodeQuery 0 3 90

echo
echo "========= Finished adding Org3 to your first network! ========= "
echo

exit 0
