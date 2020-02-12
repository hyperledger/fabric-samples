#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#
# Exit on first error
set -e pipefail

# don't rewrite paths for Windows Git Bash users
export MSYS_NO_PATHCONV=1
starttime=$(date +%s)
CC_SRC_LANGUAGE=golang
CC_RUNTIME_LANGUAGE=golang
CC_SRC_PATH=../chaincode/marbles02/go

echo Vendoring Go dependencies ...
pushd ../chaincode/marbles02/go
GO111MODULE=on go mod vendor
popd
echo Finished vendoring Go dependencies

# launch network; create channel and join peer to channel
pushd ../test-network
./network.sh down
./network.sh up createChannel -ca -s couchdb

export PATH=${PWD}/../bin:${PWD}:$PATH
export FABRIC_CFG_PATH=${PWD}/../config

# import environment variables
. scripts/envVar.sh

echo "Packaging the marbles smart contract"

setGlobals 1

peer lifecycle chaincode package marbles.tar.gz  \
  --path $CC_SRC_PATH \
  --lang $CC_RUNTIME_LANGUAGE \
  --label marblesv1

echo "Installing smart contract on peer0.org1.example.com"

peer lifecycle chaincode install marbles.tar.gz


echo "Installing smart contract on peer0.org2.example.com"

setGlobals 2

peer lifecycle chaincode install marbles.tar.gz


echo "Query the chaincode package id"

setGlobals 1

peer lifecycle chaincode queryinstalled >&log.txt

PACKAGE_ID=$(sed -n "/marblesv1/{s/^Package ID: //; s/, Label:.*$//; p;}" log.txt)

echo "Approving the chaincode definition for org1.example.com"

peer lifecycle chaincode approveformyorg \
    -o localhost:7050 \
    --ordererTLSHostnameOverride orderer.example.com \
    --channelID mychannel \
    --name marbles \
    --version 1.0 \
    --init-required \
    --signature-policy AND"('Org1MSP.member','Org2MSP.member')" \
    --sequence 1 \
    --package-id $PACKAGE_ID \
    --tls \
    --cafile ${ORDERER_CA}

echo "Approving the chaincode definition for org2.example.com"

setGlobals 2

peer lifecycle chaincode approveformyorg \
    -o localhost:7050 \
    --ordererTLSHostnameOverride orderer.example.com \
    --channelID mychannel \
    --name marbles \
    --version 1.0 \
    --init-required \
    --signature-policy AND"('Org1MSP.member','Org2MSP.member')" \
    --sequence 1 \
    --package-id $PACKAGE_ID \
    --tls \
    --cafile ${ORDERER_CA}

echo "Checking if the chaincode definition is ready to commit"

peer lifecycle chaincode checkcommitreadiness \
    --channelID mychannel \
    --name marbles \
    --version 1.0 \
    --sequence 1 \
    --output json \
    --init-required \
    --signature-policy AND"('Org1MSP.member','Org2MSP.member')" >&log.txt

rc=0
for var in "\"Org1MSP\": true" "\"Org2MSP\": true"
do
  grep "$var" log.txt &>/dev/null || let rc=1
done

if test $rc -eq 0; then
    echo "Chaincode definition is ready to commit"
else
  sleep 10
fi

parsePeerConnectionParameters 1 2

echo "Commit the chaincode definition to the channel"

peer lifecycle chaincode commit \
    -o localhost:7050 \
    --ordererTLSHostnameOverride orderer.example.com \
    --channelID mychannel \
    --name marbles \
    --version 1.0 \
    --init-required \
    --signature-policy AND"('Org1MSP.member','Org2MSP.member')" \
    --sequence 1 \
    --tls \
    --cafile ${ORDERER_CA} \
    $PEER_CONN_PARMS

echo "Check if the chaincode has been committed to the channel ..."

peer lifecycle chaincode querycommitted \
  --channelID mychannel \
  --name marbles >&log.txt

EXPECTED_RESULT="Version: 1.0, Sequence: 1, Endorsement Plugin: escc, Validation Plugin: vscc"
VALUE=$(grep -o "Version: 1.0, Sequence: 1, Endorsement Plugin: escc, Validation Plugin: vscc" log.txt)
echo "$VALUE"

if [ "$VALUE" = "Version: 1.0, Sequence: 1, Endorsement Plugin: escc, Validation Plugin: vscc" ] ; then
  echo "chaincode has been committed"
else
  sleep 10
fi

echo "invoke the marbles chaincode init function ... "

peer chaincode invoke \
        -o localhost:7050 \
        --ordererTLSHostnameOverride orderer.example.com \
        -C mychannel \
        -n marbles \
        --isInit \
        -c '{"Args":["Init"]}' \
        --tls \
        --cafile ${ORDERER_CA} \
        $PEER_CONN_PARMS

rm log.txt

popd

cat <<EOF

Total setup execution time : $(($(date +%s) - starttime)) secs ...

EOF
