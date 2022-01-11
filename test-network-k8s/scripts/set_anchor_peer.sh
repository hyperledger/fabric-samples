#!/bin/bash
#
# Copyright IBM Corp. All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0
#

function fetch_channel_config() {
  local output=$1

  echo "Fetching the most recent configuration block for channel ${CHANNEL_NAME}"
  peer channel \
    fetch config config_block.pb \
    -o org0-orderer1:6050 \
    -c ${CHANNEL_NAME} \
    --tls --cafile ${ORDERER_TLS_CA_FILE}

  echo "Decoding config block to JSON and isolating config to ${output}"
  configtxlator proto_decode \
    --input config_block.pb \
    --type common.Block \
    | jq .data.data[0].payload.data.config > ${output}
}

verify_result() {
  if [ $1 -ne 0 ]; then
    echo $2
    exit $1
  fi
}

function create_config_update() {
  local original=$1
  local modified=$2
  local output=$3

  configtxlator proto_encode --input "${original}" --type common.Config --output original_config.pb
  configtxlator proto_encode --input "${modified}" --type common.Config --output modified_config.pb

  # returns non-zero if no updates were detected between current and new config
  configtxlator compute_update --channel_id "${CHANNEL_NAME}" --original original_config.pb --updated modified_config.pb --output config_update.pb
  if [ $? -ne 0 ]; then
    echo "Anchor peer has already been set to ${ANCHOR_PEER_HOST}:${ANCHOR_PEER_PORT} - no update required."
    return 1
  fi

  configtxlator proto_decode --input config_update.pb --type common.ConfigUpdate --output config_update.json
  echo '{"payload":{"header":{"channel_header":{"channel_id":"'${CHANNEL_NAME}'", "type":2}},"data":{"config_update":'$(cat config_update.json)'}}}' | jq . > config_update_in_envelope.json
  configtxlator proto_encode --input config_update_in_envelope.json --type common.Envelope --output ${output}

  return 0
}

function create_anchor_peer_update() {
  echo "Generating anchor peer update transaction for Org${ORG_NUM} on channel ${CHANNEL_NAME}"
  fetch_channel_config config.json

  set -x
  # Modify the configuration to append the anchor peer
  jq '.channel_group.groups.Application.groups.'${CORE_PEER_LOCALMSPID}'.values += {"AnchorPeers":{"mod_policy": "Admins","value":{"anchor_peers": [{"host": "'${ANCHOR_PEER_HOST}'","port": '${ANCHOR_PEER_PORT}'}]},"version": "0"}}' config.json > modified_config.json
  { set +x; } 2>/dev/null

  # Compute a config update, based on the differences between
  # config.json and modified_config.json, write
  # it as a transaction to anchors.tx
  create_config_update config.json modified_config.json anchors.tx
  return $?
}

function update_anchor_peer() {
  peer channel \
    update -f anchors.tx \
    -o org0-orderer1:6050 \
    -c ${CHANNEL_NAME} \
    --tls --cafile ${ORDERER_TLS_CA_FILE} >& log.txt

  res=$?
  cat log.txt

  verify_result $res "Anchor peer update failed"

  echo "Anchor peer set for org ${ORG_NAME} on channel ${CHANNEL_NAME} to ${ANCHOR_PEER_HOST}:${ANCHOR_PEER_PORT}"
}

function set_anchor_peer() {
  echo "Updating org ${ORG_NUM} anchor peer for channel ${CHANNEL_NAME} to ${ANCHOR_PEER_HOST}:${ANCHOR_PEER_PORT}"

  create_anchor_peer_update
  res=$?

  if [ $res -eq 0 ]; then
    update_anchor_peer
  fi
}

set -x

ORG_NUM=$1
CHANNEL_NAME=$2
PEER_NAME=$3
ORG_NAME="org${ORG_NUM}"
ANCHOR_PEER_HOST=${ORG_NAME}-${PEER_NAME}
ANCHOR_PEER_PORT=7051
ORDERER_TLS_CA_FILE=/var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/msp/tlscacerts/org0-tls-ca.pem

export CORE_PEER_LOCALMSPID="Org${ORG_NUM}MSP"

set_anchor_peer

{ set +x; } 2>/dev/null
