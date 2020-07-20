#!/bin/bash


CHANNEL_NAME="$1"
DELAY="$2"
MAX_RETRY="$3"
VERBOSE="$4"
: ${CHANNEL_NAME:="mychannel"}
: ${DELAY:="3"}
: ${MAX_RETRY:="5"}
: ${VERBOSE:="false"}

# import utils
. scripts/envVar.sh

# execute - Prints and executes the command
function execute() {
  echo -e "\033[0;32mCommand\033[0m: ${*}"
  echo -e "\033[0;32mOutput\033[0m:"
  "${@}"
}

function info() {
  echo -e "\033[0;33mINFO\033[0m: ${1}"
}

if [ ! -d "channel-artifacts" ]; then
	mkdir channel-artifacts
fi

createChannelTx() {
	execute configtxgen -profile TwoOrgsChannel -outputCreateChannelTx ./channel-artifacts/${CHANNEL_NAME}.tx -channelID $CHANNEL_NAME
	res=$?
	if [ $res -ne 0 ]; then
		echo "Failed to generate channel configuration transaction..."
		exit 1
	fi
	echo
}

createAncorPeerTx() {
	for orgmsp in Org1MSP Org2MSP; do
    info "Generating anchor peer update transaction for ${orgmsp}"
    execute configtxgen -profile TwoOrgsChannel -outputAnchorPeersUpdate ./channel-artifacts/${orgmsp}anchors.tx -channelID $CHANNEL_NAME -asOrg ${orgmsp}
    res=$?
    if [ $res -ne 0 ]; then
      echo "Failed to generate anchor peer update transaction for ${orgmsp}..."
      exit 1
    fi
    echo
	done
}

createChannel() {
	setGlobals 1
	# Poll in case the raft leader is not set yet
	local rc=1
	local COUNTER=1
	while [ $rc -ne 0 -a $COUNTER -lt $MAX_RETRY ] ; do
		sleep $DELAY
		execute peer channel create -o localhost:7050 -c $CHANNEL_NAME --ordererTLSHostnameOverride orderer.example.com -f ./channel-artifacts/${CHANNEL_NAME}.tx --outputBlock ./channel-artifacts/${CHANNEL_NAME}.block --tls --cafile $ORDERER_CA >&log.txt
		res=$?
		let rc=$res
		COUNTER=$(expr $COUNTER + 1)
	done
	cat log.txt
	verifyResult $res "Channel creation failed"
	info "Channel ${CHANNEL_NAME} created"
	echo
}

# queryCommitted ORG
joinChannel() {
  ORG=$1
  setGlobals $ORG
	local rc=1
	local COUNTER=1
	## Sometimes Join takes time, hence retry
	while [ $rc -ne 0 ] && [ $COUNTER -lt $MAX_RETRY ] ; do
    sleep $DELAY
    execute peer channel join -b ./channel-artifacts/$CHANNEL_NAME.block >&log.txt
    res=$?
		let rc=$res
		COUNTER=$((COUNTER + 1))
	done
	cat log.txt
	verifyResult $res "After $MAX_RETRY attempts, peer0.org${ORG} has failed to join channel '$CHANNEL_NAME' "
	echo
}

updateAnchorPeers() {
  ORG=$1
  setGlobals $ORG
	local rc=1
	local COUNTER=1
	## Sometimes Join takes time, hence retry
	while [ $rc -ne 0 -a $COUNTER -lt $MAX_RETRY ] ; do
    sleep $DELAY
		execute peer channel update -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com -c $CHANNEL_NAME -f ./channel-artifacts/${CORE_PEER_LOCALMSPID}anchors.tx --tls --cafile $ORDERER_CA >&log.txt
    res=$?
		let rc=$res
		COUNTER=$(expr $COUNTER + 1)
	done
	cat log.txt
  verifyResult $res "Anchor peer update failed"
  info "Anchor peers updated for org ${CORE_PEER_LOCALMSPID} on channel ${CHANNEL_NAME}"
  sleep $DELAY
  echo
}

verifyResult() {
  if [ $1 -ne 0 ]; then
    echo "!!!!!!!!!!!!!!! ${2} !!!!!!!!!!!!!!!!"
    echo
    exit 1
  fi
}

FABRIC_CFG_PATH=${PWD}/configtx

## Create channeltx
echo
info "Generating channel create transaction ${CHANNEL_NAME}.tx"
createChannelTx

## Create anchorpeertx
info "Generating anchor peer update transactions"
createAncorPeerTx

FABRIC_CFG_PATH=$PWD/../config/

## Create channel
info "Creating channel ${CHANNEL_NAME}"
createChannel

## Join all the peers to the channel
info "Join Org1 peers to the channel"
joinChannel 1
info "Join Org2 peers to the channel"
joinChannel 2

## Set the anchor peers for each org in the channel
info "Updating anchor peers for org1"
updateAnchorPeers 1
info "Updating anchor peers for org2"
updateAnchorPeers 2

info "Channel successfully joined"
