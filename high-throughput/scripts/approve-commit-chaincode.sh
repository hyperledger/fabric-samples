#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

export CORE_PEER_TLS_ENABLED=true

echo "========== Query chaincode package ID =========="
export CORE_PEER_MSPCONFIGPATH=../test-network/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp
export CORE_PEER_ADDRESS=localhost:7051
export CORE_PEER_LOCALMSPID="Org1MSP"
export CORE_PEER_TLS_ROOTCERT_FILE=../test-network/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt
peer lifecycle chaincode queryinstalled >&log.txt
export PACKAGE_ID=`sed -n '/Package/{s/^Package ID: //; s/, Label:.*$//; p;}' log.txt`

echo "========== Approve definition for Org1 =========="
export CORE_PEER_MSPCONFIGPATH=../test-network/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp
export CORE_PEER_ADDRESS=localhost:7051
export CORE_PEER_LOCALMSPID="Org1MSP"
export CORE_PEER_TLS_ROOTCERT_FILE=../test-network/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt
peer lifecycle chaincode install bigdatacc.tar.gz

peer lifecycle chaincode approveformyorg -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ../test-network/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem --channelID mychannel --signature-policy "OR('Org1MSP.peer', 'Org2MSP.peer')" --name bigdatacc --version 0 --init-required --package-id ${PACKAGE_ID} --sequence 1

echo "========== Approve definition for Org2 =========="
export CORE_PEER_MSPCONFIGPATH=../test-network/organizations/peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp
export CORE_PEER_ADDRESS=localhost:9051
export CORE_PEER_LOCALMSPID="Org2MSP"
export CORE_PEER_TLS_ROOTCERT_FILE=../test-network/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt
peer lifecycle chaincode approveformyorg -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ../test-network/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem --channelID mychannel --signature-policy "OR('Org1MSP.peer', 'Org2MSP.peer')" --name bigdatacc --version 0 --init-required --package-id ${PACKAGE_ID} --sequence 1

. scripts/check-commit-readiness.sh

checkCommitReadiness 1 "\"Org1MSP\": true" "\"Org2MSP\": true"
checkCommitReadiness 2 "\"Org1MSP\": true" "\"Org2MSP\": true"


echo "========== Commit the definition the mychannel =========="
peer lifecycle chaincode commit -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ../test-network/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem --channelID mychannel --signature-policy "OR('Org1MSP.peer', 'Org2MSP.peer')" --name bigdatacc --version 0 --init-required --sequence 1 --waitForEvent --peerAddresses localhost:7051 --tlsRootCertFiles ../test-network/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt --peerAddresses localhost:9051 --tlsRootCertFiles ../test-network/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt


echo "========== Invoke the Init function =========="
peer chaincode invoke -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ../test-network/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem  -C mychannel -n bigdatacc --isInit -c '{"Args":["Init"]}'
