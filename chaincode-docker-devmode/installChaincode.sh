#!/bin/bash
CHAINCODE_PACKAGE_NAME=$1
CHAINCODE_NAME=$2
SEQUENCE=$3
VERSION=$4

set -e

echo "========= Installing the newest version of chaincode ================"
echo "Chaincode name: $CHAINCODE_NAME"
peer lifecycle chaincode package "chaincode/$CHAINCODE_NAME/go/$CHAINCODE_PACKAGE_NAME" -p "chaincode/$CHAINCODE_NAME/go" --label $CHAINCODE_NAME -l node
peer lifecycle chaincode install "chaincode/$CHAINCODE_NAME/go/$CHAINCODE_PACKAGE_NAME"
CC_PACKAGE_ID=$(peer lifecycle chaincode queryinstalled | tail -n 1 |awk '{print $3}' | tr -d ",")

echo "========= Approving chaincode definition ================="
peer lifecycle chaincode approveformyorg -o orderer:7050 --channelID myc --name $CHAINCODE_NAME --version $VERSION --sequence $SEQUENCE --package-id $CC_PACKAGE_ID

echo "========= Committing chaincode definition ================"
peer lifecycle chaincode commit -o orderer:7050 --channelID myc --name $CHAINCODE_NAME --version $VERSION --sequence $SEQUENCE

echo "========= Finished Committing chaincode definition ======="
echo "Use peer invoke to invoke init transactions:"
echo "peer chaincode invoke -o orderer:7050 -C myc -n $CHAINCODE_NAME -c '{\"Args\":[\"init\",\"a\",\"100\",\"b\",\"200\"]}'"
echo "========= CHAINCODE ID ==================================="
echo $CC_PACKAGE_ID
echo "Use this chaincode id to start chaincode container"


