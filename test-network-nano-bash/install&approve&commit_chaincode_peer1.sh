#!/usr/bin/env sh
#
# SPDX-License-Identifier: Apache-2.0
#

# look for binaries in local dev environment /build/bin directory and then in local samples /bin directory
export PATH="${PWD}"/../../fabric/build/bin:"${PWD}"/../bin:"$PATH"


. peer1admin.sh

# Install Chaincode on Peer1
peer lifecycle chaincode package basic.tar.gz --path ../asset-transfer-basic/chaincode-go --lang golang --label basic_1 >> ./logs/install\&approve\&commit_chaincode_peer_1.log 2>&1
peer lifecycle chaincode install basic.tar.gz >> ./logs/install\&approve\&commit_chaincode_peer_1.log 2>&1

# Set the CHAINCODE_ID from the created chaincode package
CHAINCODE_ID=$(peer lifecycle chaincode calculatepackageid basic.tar.gz)
export CHAINCODE_ID

# Approve the chaincode using Peer1Admin
peer lifecycle chaincode approveformyorg -o 127.0.0.1:6050 --channelID mychannel --name basic --version 1 --package-id "${CHAINCODE_ID}" --sequence 1 --tls --cafile "${PWD}"/crypto-config/ordererOrganizations/example.com/orderers/orderer.example.com/tls/ca.crt >> ./logs/install\&approve\&commit_chaincode_peer_1.log 2>&1
peer lifecycle chaincode commit -o 127.0.0.1:6050 --channelID mychannel --name basic --version 1 --sequence 1 --tls --cafile "${PWD}"/crypto-config/ordererOrganizations/example.com/orderers/orderer.example.com/tls/ca.crt >> ./logs/install\&approve\&commit_chaincode_peer_1.log 2>&1
