#!/bin/bash
export CHANNEL_NAME=mychannel
export ORDERER_CA=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.example.com/peers/orderer0.org1.example.com/msp/tlscacerts/tlsca.org1.example.com-cert.pem

## Create channel
echo "Creating channel..."
peer channel create -o orderer0.org1.example.com:7050 -c $CHANNEL_NAME -f ./channel-artifacts/channel.tx --tls $CORE_PEER_TLS_ENABLED --cafile $ORDERER_CA
sleep 2
## Join all the peers to the channel
echo "peer0.org1 join the channel..."
peer channel join -b $CHANNEL_NAME.block
## Set the anchor peers for each org in the channel
echo "Updating anchor peers for org1..."
peer channel update -o orderer0.org1.example.com:7050 -c $CHANNEL_NAME -f ./channel-artifacts/Org1MSPanchors.tx --tls $CORE_PEER_TLS_ENABLED --cafile $ORDERER_CA
sleep 2
## Install chaincode on peer0.org1
echo "Installing chaincode on peer0.org1..."
peer chaincode install -n mycc -v 1.0 -p github.com/chaincode/chaincode_example02/go
# Instantiate chaincode on peer0.org1
echo "Instantiating chaincode on peer0.org1..."
peer chaincode instantiate -o orderer0.org1.example.com:7050 --tls $CORE_PEER_TLS_ENABLED --cafile $ORDERER_CA -C $CHANNEL_NAME -n mycc -v 1.0 -c '{"Args":["init","a", "100", "b","200"]}' -P "OR ('Org1MSP.member')"
# Query chaincode on peer0.org1
echo "Querying chaincode on peer0.org1..."
sleep 5
peer chaincode query -C $CHANNEL_NAME -n mycc -c '{"Args":["query","a"]}'
# Invoke chaincode on peer0.org1
echo "Sending invoke transaction on peer0.org1"
peer chaincode invoke -o orderer0.org1.example.com:7050 --tls $CORE_PEER_TLS_ENABLED --cafile $ORDERER_CA -C $CHANNEL_NAME -n mycc  -c '{"Args":["invoke","a","b","10"]}'
# Query chaincode on peer0.org1
sleep 5
echo "Querying chaincode on peer0.org1..."
peer chaincode query -C $CHANNEL_NAME -n mycc -c '{"Args":["query","a"]}'
