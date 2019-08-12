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

packageChaincode() {
		CORE_PEER_LOCALMSPID=partya
		CORE_PEER_ADDRESS=irs-partya:7051
		CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/partya.example.com/users/Admin@partya.example.com/msp
		echo "===================== Creating chaincode package ===================== "
		peer lifecycle chaincode package irscc.tar.gz --path ${CC_SRC_PATH} --lang golang --label irscc_1
		echo "===================== Chaincode packaged ===================== "
}

installChaincode() {
	for org in partya partyb partyc auditor rrprovider
	do
		CORE_PEER_LOCALMSPID=$org
		CORE_PEER_ADDRESS=irs-$org:7051
		CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/$org.example.com/users/Admin@$org.example.com/msp
		echo "===================== Org $org installing chaincode ===================== "
		peer lifecycle chaincode install irscc.tar.gz
		echo "===================== Org $org chaincode installed ===================== "
	done
}

queryPackage() {
		CORE_PEER_LOCALMSPID=partya
		CORE_PEER_ADDRESS=irs-partya:7051
		CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/partya.example.com/users/Admin@partya.example.com/msp
		echo "===================== Query chaincode package ID ===================== "
		peer lifecycle chaincode queryinstalled >&log.txt
		export PACKAGE_ID=`sed -n '/Package/{s/^Package ID: //; s/, Label:.*$//; p;}' log.txt`
		echo "packgeID=$PACKAGE_ID"
		echo "===================== Query successfull  ===================== "
}

approveChaincode() {
	for org in partya partyb partyc auditor rrprovider
	do
		CORE_PEER_LOCALMSPID=$org
		CORE_PEER_ADDRESS=irs-$org:7051
		CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/$org.example.com/users/Admin@$org.example.com/msp
		echo "===================== Approving chaincode definition for $org ===================== "
		peer lifecycle chaincode approveformyorg -o irs-orderer:7050 --channelID irs --signature-policy "AND(OR('partya.peer','partyb.peer','partyc.peer'), 'auditor.peer')" --name irscc --version 1 --init-required --sequence 1 --package-id ${PACKAGE_ID} --waitForEvent
		echo "===================== Chaincode definition approved ===================== "
	done
}

checkCommitReadiness() {
	for org in partya partyb partyc auditor rrprovider
	do
		export CORE_PEER_LOCALMSPID=$org
		export CORE_PEER_ADDRESS=irs-$org:7051
		export CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/$org.example.com/users/Admin@$org.example.com/msp
		checkCommitReadiness "\"partya\": true" "\"partyb\": true" "\"partyc\": true" "\"auditor\": true" "\"rrprovider\": true"
	done
}

commitChaincode() {
	CORE_PEER_LOCALMSPID=partya
	CORE_PEER_ADDRESS=irs-partya:7051
	CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/partya.example.com/users/Admin@partya.example.com/msp
		echo "===================== Commiting chaincode definition to channel ===================== "
		peer lifecycle chaincode commit -o irs-orderer:7050 --channelID irs --signature-policy "AND(OR('partya.peer','partyb.peer','partyc.peer'), 'auditor.peer')" --name irscc --version 1 --init-required --sequence 1 --peerAddresses irs-partya:7051 --peerAddresses irs-partyb:7051 --peerAddresses irs-partyc:7051 --peerAddresses irs-auditor:7051 --peerAddresses irs-rrprovider:7051 --waitForEvent
		echo "===================== Chaincode definition committed ===================== "
}

initChaincode() {
	CORE_PEER_LOCALMSPID=partya
	CORE_PEER_ADDRESS=irs-partya:7051
	CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/partya.example.com/users/Admin@partya.example.com/msp
		echo "===================== Initializing chaincode ===================== "
		peer chaincode invoke -o irs-orderer:7050 --isInit -C irs --waitForEvent -n irscc --peerAddresses irs-rrprovider:7051 --peerAddresses irs-partya:7051 --peerAddresses irs-partyb:7051 --peerAddresses irs-partyc:7051 --peerAddresses irs-auditor:7051 -c '{"Args":["init","auditor","1000000","rrprovider","myrr"]}'
		echo "===================== Chaincode initialized ===================== "
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
	peer chaincode invoke -o irs-orderer:7050 -C irs --waitForEvent -n irscc --peerAddresses irs-partya:7051 --peerAddresses irs-partyb:7051 --peerAddresses irs-auditor:7051 -c '{"Args":["createSwap","myswap","{\"StartDate\":\"2018-09-27T15:04:05Z\",\"EndDate\":\"2018-09-30T15:04:05Z\",\"PaymentInterval\":395,\"PrincipalAmount\":100000,\"FixedRate\":400,\"FloatingRate\":500,\"ReferenceRate\":\"myrr\"}", "partya", "partyb"]}'
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

## Package the chaincode
echo "packaging chaincode..."
packageChaincode

## Query chaincode packageID
echo "Querying packageID..."
installChaincode

## Install chaincode on all peers
echo "Installing chaincode..."
queryPackage

# Approve chaincode definition
echo "Approving chaincode..."
approveChaincode

. scripts/check-commit-readiness.sh

# Check the commit readiness of the chaincode definition
echo "Checking the commit readiness of the chaincode definition..."
checkCommitReadiness

# Commit chaincode definition
echo "Committing chaincode definition..."
commitChaincode

# Init chaincode
echo "Initialize chaincode..."
initChaincode

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
