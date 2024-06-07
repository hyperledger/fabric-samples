#!/bin/bash
#
# Copyright IBM Corp. All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0
#

# import utils
# test network home var targets to test network folder
# the reason we use a var here is considering with huller specific folder
# when invoking this for huller as test-network/scripts/huller-scripts
# the value is changed from default as $PWD(test-network)
# to .. as relative path to make the import works
TEST_NETWORK_HOME=${TEST_NETWORK_HOME:-${PWD}}
. ${TEST_NETWORK_HOME}/scripts/configUpdate.sh


# NOTE: This requires jq and configtxlator for execution.
createAnchorPeerUpdate() {
  infoln "Fetching channel config for channel $CHANNEL_NAME"
  fetchChannelConfig $ORG $CHANNEL_NAME ${TEST_NETWORK_HOME}/channel-artifacts/${CORE_PEER_LOCALMSPID}config.json

  infoln "Generating anchor peer update transaction for ${ORG} on channel $CHANNEL_NAME"

  if [ $ORG == "farmer" ]; then
    HOST="peer0.farmer.varion.com"
    PORT=7051
  elif [ $ORG == "pulper" ]; then
    HOST="peer0.pulper.varion.com"
    PORT=9051
  elif [ $ORG == "huller" ]; then
    HOST="peer0.huller.varion.com"
    PORT=11051
  elif [ $ORG == "export" ]; then
    HOST="peer0.export.varion.com"
    PORT=12051
  else
    errorln "Org${ORG} unknown"
  fi

  set -x
  # Modify the configuration to append the anchor peer 
  jq '.channel_group.groups.Application.groups.'${CORE_PEER_LOCALMSPID}'.values += {"AnchorPeers":{"mod_policy": "Admins","value":{"anchor_peers": [{"host": "'$HOST'","port": '$PORT'}]},"version": "0"}}' ${TEST_NETWORK_HOME}/channel-artifacts/${CORE_PEER_LOCALMSPID}config.json > ${TEST_NETWORK_HOME}/channel-artifacts/${CORE_PEER_LOCALMSPID}modified_config.json
  res=$?
  { set +x; } 2>/dev/null
  verifyResult $res "Channel configuration update for anchor peer failed, make sure you have jq installed"
  

  # Compute a config update, based on the differences between 
  # {orgmsp}config.json and {orgmsp}modified_config.json, write
  # it as a transaction to {orgmsp}anchors.tx
  createConfigUpdate ${CHANNEL_NAME} ${TEST_NETWORK_HOME}/channel-artifacts/${CORE_PEER_LOCALMSPID}config.json ${TEST_NETWORK_HOME}/channel-artifacts/${CORE_PEER_LOCALMSPID}modified_config.json ${TEST_NETWORK_HOME}/channel-artifacts/${CORE_PEER_LOCALMSPID}anchors.tx
}

updateAnchorPeer() {
  peer channel update -o localhost:7050 --ordererTLSHostnameOverride orderer.varion.com -c $CHANNEL_NAME -f ${TEST_NETWORK_HOME}/channel-artifacts/${CORE_PEER_LOCALMSPID}anchors.tx --tls --cafile "$ORDERER_CA" >&log.txt
  res=$?
  cat log.txt
  verifyResult $res "Anchor peer update failed"
  successln "Anchor peer set for org '$CORE_PEER_LOCALMSPID' on channel '$CHANNEL_NAME'"
}

ORG=$1
CHANNEL_NAME=$2

setGlobals $ORG

createAnchorPeerUpdate 

updateAnchorPeer 
