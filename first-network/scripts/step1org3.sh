#!/bin/bash
#
# Copyright IBM Corp. All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0
#

# This script is designed to be run in the org3cli container as the
# first step of the EYFN tutorial.  It creates and submits a
# configuration transaction to add org3 to the network previously
# setup in the BYFN tutorial.
#

CHANNEL_NAME="$1"
DELAY="$2"
LANGUAGE="$3"
TIMEOUT="$4"
: ${CHANNEL_NAME:="mychannel"}
: ${DELAY:="3"}
: ${LANGUAGE:="golang"}
: ${TIMEOUT:="10"}
LANGUAGE=`echo "$LANGUAGE" | tr [:upper:] [:lower:]`
COUNTER=1
MAX_RETRY=5
ORDERER_CA=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem

CC_SRC_PATH="github.com/chaincode/chaincode_example02/go/"
if [ "$LANGUAGE" = "node" ]; then
	CC_SRC_PATH="/opt/gopath/src/github.com/chaincode/chaincode_example02/node/"
fi

echo
echo "========= Creating config transaction to add org3 to network =========== "
echo

echo "Installing jq"
apt-get -y update && apt-get -y install jq

echo "Fetching the most recent configuration block for the channel"
peer channel fetch config config_block.pb -o orderer.example.com:7050 -c ${CHANNEL_NAME} --tls --cafile ${ORDERER_CA}

echo "Creating config transaction adding org3 to the network"
# translate channel configuration block into JSON format
configtxlator proto_decode --input config_block.pb --type common.Block --output config_block.json

# strip away all of the encapsulating wrappers
jq .data.data[0].payload.data.config config_block.json > config.json

# append new org to the configuration
jq -s '.[0] * {"channel_group":{"groups":{"Application":{"groups": {"Org3MSP":.[1]}}}}}' config.json ./channel-artifacts/org3.json > modified_config.json

# translate json config files back to protobuf
configtxlator proto_encode --input config.json --type common.Config --output config.pb
configtxlator proto_encode --input modified_config.json --type common.Config --output modified_config.pb

# get delta between old and new configs
configtxlator compute_update --channel_id ${CHANNEL_NAME} --original config.pb --updated modified_config.pb --output org3_update.pb

# translate protobuf delta to json
configtxlator proto_decode --input org3_update.pb --type common.ConfigUpdate --output org3_update.json

# wrap delta in an envelope message
echo '{"payload":{"header":{"channel_header":{"channel_id":"'${CHANNEL_NAME}'", "type":2}},"data":{"config_update":'$(cat org3_update.json)'}}}' | jq . > org3_update_in_envelope.json

# translate json back to protobuf
configtxlator proto_encode --input org3_update_in_envelope.json --type common.Envelope --output org3_update_in_envelope.pb

echo
echo "========= Config transaction to add org3 to network created ===== "
echo

echo "Signing config transaction"
echo
peer channel signconfigtx -f org3_update_in_envelope.pb

echo
echo "========= Submitting transaction from a different peer (peer0.org2) which also signs it ========= "
echo
export CORE_PEER_LOCALMSPID="Org2MSP"
export CORE_PEER_TLS_ROOTCERT_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt
export CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp
export CORE_PEER_ADDRESS=peer0.org2.example.com:7051
peer channel update -f org3_update_in_envelope.pb -c ${CHANNEL_NAME} -o orderer.example.com:7050 --tls --cafile ${ORDERER_CA}

echo
echo "========= Config transaction to add org3 to network submitted! =========== "
echo

exit 0
