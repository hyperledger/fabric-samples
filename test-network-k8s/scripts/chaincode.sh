#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

function package_chaincode_for() {
  local org=$1
  local cc_folder="chaincode/${CHAINCODE_NAME}"
  local build_folder="build/chaincode"
  local cc_archive="${build_folder}/${CHAINCODE_NAME}.tgz"
  push_fn "Packaging chaincode folder ${cc_folder}"

  mkdir -p ${build_folder}

  tar -C ${cc_folder} -zcf ${cc_folder}/code.tar.gz connection.json
  tar -C ${cc_folder} -zcf ${cc_archive} code.tar.gz metadata.json

  rm ${cc_folder}/code.tar.gz

  pop_fn
}

function install_chaincode_for() {
  local org=$1
  local peer=$2
  push_fn "Installing chaincode for org ${org} peer ${peer}"

  export_peer_context $org $peer

  peer lifecycle chaincode install build/chaincode/${CHAINCODE_NAME}.tgz

  pop_fn
}

function launch_chaincode_service() {
  local org=$1
  local peer=$2
  local cc_id=$3
  local cc_image=$4
  push_fn "Launching chaincode container \"${cc_image}\""

  # The chaincode endpoint needs to have the generated chaincode ID available in the environment.
  # This could be from a config map, a secret, or by directly editing the deployment spec.  Here we'll keep
  # things simple by using sed to substitute script variables into a yaml template.
  cat kube/${org}/${org}-cc-template.yaml \
    | sed 's,{{CHAINCODE_NAME}},'${CHAINCODE_NAME}',g' \
    | sed 's,{{CHAINCODE_ID}},'${cc_id}',g' \
    | sed 's,{{CHAINCODE_IMAGE}},'${cc_image}',g' \
    | sed 's,{{PEER_NAME}},'${peer}',g' \
    | exec kubectl -n $NS apply -f -

  kubectl -n $NS rollout status deploy/${org}${peer}-cc-${CHAINCODE_NAME}

  pop_fn
}

function activate_chaincode_for() {
  local org=$1
  local peer=$2
  local cc_id=$3
  push_fn "Activating $org chaincode ${CHAINCODE_ID}"

  export_peer_context $org $peer

  peer lifecycle \
    chaincode approveformyorg \
    --channelID     ${CHANNEL_NAME} \
    --name          ${CHAINCODE_NAME} \
    --version       1 \
    --package-id    ${cc_id} \
    --sequence      1 \
    --orderer       org0-orderer1.${DOMAIN}:443 \
    --tls --cafile  ${TEMP_DIR}/channel-msp/ordererOrganizations/org0/orderers/org0-orderer1/tls/signcerts/tls-cert.pem

  peer lifecycle \
    chaincode commit \
    --channelID     ${CHANNEL_NAME} \
    --name          ${CHAINCODE_NAME} \
    --version       1 \
    --sequence      1 \
    --orderer       org0-orderer1.${DOMAIN}:443 \
    --tls --cafile  ${TEMP_DIR}/channel-msp/ordererOrganizations/org0/orderers/org0-orderer1/tls/signcerts/tls-cert.pem

  pop_fn
}

function query_chaincode() {
  set -x

  export_peer_context org1 peer1

  peer chaincode query \
    -n  $CHAINCODE_NAME \
    -C  $CHANNEL_NAME \
    -c  $@
}

function query_chaincode_metadata() {
  set -x
  local args='{"Args":["org.hyperledger.fabric:GetMetadata"]}'

  log ''
  log 'Org1-Peer1:'
  export_peer_context org1 peer1
  peer chaincode query -n $CHAINCODE_NAME -C $CHANNEL_NAME -c $args

  log ''
  log 'Org1-Peer2:'
  export_peer_context org1 peer2
  peer chaincode query -n $CHAINCODE_NAME -C $CHANNEL_NAME -c $args
}

function invoke_chaincode() {
  # set -x
  # todo: mangle additional $@ parameters with bash escape quotations

  export_peer_context org1 peer1

  peer chaincode invoke \
    -n              $CHAINCODE_NAME \
    -C              $CHANNEL_NAME \
    -c              $@ \
    --orderer       org0-orderer1.${DOMAIN}:443 \
    --tls --cafile  ${TEMP_DIR}/channel-msp/ordererOrganizations/org0/orderers/org0-orderer1/tls/signcerts/tls-cert.pem

  sleep 2
}

# Normally the chaincode ID is emitted by the peer install command.  In this case, we'll generate the
# package ID as the sha-256 checksum of the chaincode archive.
function set_chaincode_id() {
  local cc_package=build/chaincode/${CHAINCODE_NAME}.tgz
  cc_sha256=$(shasum -a 256 ${cc_package} | tr -s ' ' | cut -d ' ' -f 1)

  label=$( jq -r '.label' chaincode/${CHAINCODE_NAME}/metadata.json)

  CHAINCODE_ID=${label}:${cc_sha256}
}

# Package and install the chaincode, but do not activate.
function install_chaincode() {
  local org=org1

  package_chaincode_for ${org}

  install_chaincode_for ${org} peer1
  install_chaincode_for ${org} peer2

  set_chaincode_id
}

# Activate the installed chaincode but do not package/install a new archive.
function activate_chaincode() {
  set -x

  set_chaincode_id
  activate_chaincode_for org1 peer1 $CHAINCODE_ID

  # jdk: does activation on a single peer apply to all peers in the org?  This is an error:
#  activate_chaincode_for org1 peer1 $CHAINCODE_ID

}

# Install, launch, and activate the chaincode
function deploy_chaincode() {
  set -x

  install_chaincode
  launch_chaincode_service org1 peer1 $CHAINCODE_ID $CHAINCODE_IMAGE
  launch_chaincode_service org1 peer2 $CHAINCODE_ID $CHAINCODE_IMAGE
  activate_chaincode
}

