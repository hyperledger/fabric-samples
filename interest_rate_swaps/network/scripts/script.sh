#!/bin/bash

DELAY="3"
TIMEOUT="10"
VERBOSE="false"
COUNTER=1
MAX_RETRY=5

CC_SRC_PATH="irscc/"

createChannel() {
	CORE_PEER_LOCALMSPID=partya
	CORE_PEER_ADDRESS=irs-partya:7051
	CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/partya.example.com/users/Admin@partya.example.com/msp
	echo "===================== Creating channel ===================== "
	peer channel create -o irs-orderer:7050 -c irs -f ./channel-artifacts/channel.tx
	echo "===================== Channel created ===================== "
}

joinChannel () {
	for org in partya partyb partyc auditor rrprovider
	do
		CORE_PEER_LOCALMSPID=$org
		CORE_PEER_ADDRESS=irs-$org:7051
		CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/$org.example.com/users/Admin@$org.example.com/msp
		echo "===================== Org $org joining channel ===================== "
		peer channel join -b irs.block -o irs-orderer:7050
		echo "===================== Channel joined ===================== "
	done
}

installChaincode() {
	for org in partya partyb partyc auditor rrprovider
	do
		CORE_PEER_LOCALMSPID=$org
		CORE_PEER_ADDRESS=irs-$org:7051
		CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/$org.example.com/users/Admin@$org.example.com/msp
		echo "===================== Org $org installing chaincode ===================== "
		peer chaincode install -n irscc -v 0 -l golang -p  ${CC_SRC_PATH}
		echo "===================== Org $org chaincode installed ===================== "
	done
}

instantiateChaincode() {
	CORE_PEER_LOCALMSPID=partya
	CORE_PEER_ADDRESS=irs-partya:7051
	CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/partya.example.com/users/Admin@partya.example.com/msp
	echo "===================== Instantiating chaincode ===================== "
	peer chaincode instantiate -o irs-orderer:7050 -C irs -n irscc -l golang -v 0 -c '{"Args":["init","auditor","100000","rrprovider","myrr"]}' -P "AND(OR('partya.peer','partyb.peer','partyc.peer'), 'auditor.peer')"
	echo "===================== Chaincode instantiated ===================== "
}

setReferenceRate() {
	CORE_PEER_LOCALMSPID=rrprovider
	CORE_PEER_ADDRESS=irs-rrprovider:7051
	CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/rrprovider.example.com/users/User1@rrprovider.example.com/msp
	echo "===================== Invoking chaincode ===================== "
	peer chaincode invoke -o irs-orderer:7050 -C irs --waitForEvent -n irscc --peerAddresses irs-rrprovider:7051 -c '{"Args":["setReferenceRate","myrr","300"]}'
	echo "===================== Chaincode invoked ===================== "
}

createSwap() {
	CORE_PEER_LOCALMSPID=partya
	CORE_PEER_ADDRESS=irs-partya:7051
	CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/partya.example.com/users/User1@partya.example.com/msp
	echo "===================== Invoking chaincode ===================== "
	peer chaincode invoke -o irs-orderer:7050 -C irs --waitForEvent -n irscc --peerAddresses irs-partya:7051 --peerAddresses irs-partyb:7051 --peerAddresses irs-auditor:7051 -c '{"Args":["createSwap","myswap","{\"StartDate\":\"2018-09-27T15:04:05Z\",\"EndDate\":\"2018-09-30T15:04:05Z\",\"PaymentInterval\":395,\"PrincipalAmount\":10,\"FixedRate\":400,\"FloatingRate\":500,\"ReferenceRate\":\"myrr\"}", "partya", "partyb"]}'
	echo "===================== Chaincode invoked ===================== "
}

calculatePayment() {
	CORE_PEER_LOCALMSPID=partya
	CORE_PEER_ADDRESS=irs-partya:7051
	CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/partya.example.com/users/User1@partya.example.com/msp
	echo "===================== Invoking chaincode ===================== "
	peer chaincode invoke -o irs-orderer:7050 -C irs --waitForEvent -n irscc --peerAddresses irs-partya:7051 --peerAddresses irs-partyb:7051 -c '{"Args":["calculatePayment","myswap"]}'
	echo "===================== Chaincode invoked ===================== "
}

settlePayment() {
	CORE_PEER_LOCALMSPID=partyb
	CORE_PEER_ADDRESS=irs-partyb:7051
	CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/partyb.example.com/users/User1@partyb.example.com/msp
	echo "===================== Invoking chaincode ===================== "
	peer chaincode invoke -o irs-orderer:7050 -C irs --waitForEvent -n irscc --peerAddresses irs-partya:7051 --peerAddresses irs-partyb:7051 -c '{"Args":["settlePayment","myswap"]}'
	echo "===================== Chaincode invoked ===================== "
}

## Create channel
sleep 1
echo "Creating channel..."
createChannel

## Join all the peers to the channel
echo "Having all peers join the channel..."
joinChannel

## Install chaincode on all peers
echo "Installing chaincode..."
installChaincode

# Instantiate chaincode
echo "Instantiating chaincode..."
instantiateChaincode

echo "Setting myrr reference rate"
sleep 3
setReferenceRate

echo "Creating swap between A and B"
createSwap

echo "Calculate payment information"
calculatePayment

echo "Mark payment settled"
settlePayment

echo
echo "========= IRS network sample setup completed =========== "
echo

exit 0
