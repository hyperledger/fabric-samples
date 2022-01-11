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

# Copy the chaincode archive from the local host to the org admin
function transfer_chaincode_archive_for() {
  local org=$1
  local cc_archive="build/chaincode/${CHAINCODE_NAME}.tgz"
  push_fn "Transferring chaincode archive to ${org}"

  # Like kubectl cp, but targeted to a deployment rather than an individual pod.
  tar cf - ${cc_archive} | kubectl -n $NS exec -i deploy/${org}-admin-cli -c main -- tar xvf -

  pop_fn
}

function install_chaincode_for() {
  local org=$1
  local peer=$2
  push_fn "Installing chaincode for org ${org}  peer ${peer}"

  # Install the chaincode
  echo 'set -x
  export CORE_PEER_ADDRESS='${org}'-'${peer}':7051
  peer lifecycle chaincode install build/chaincode/'${CHAINCODE_NAME}'.tgz
  ' | exec kubectl -n $NS exec deploy/${org}-admin-cli -c main -i -- /bin/bash

  pop_fn
}

function launch_chaincode_service() {
  local org=$1
  local cc_id=$2
  local cc_image=$3
  local peer=$4
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
  local cc_id=$2
  push_fn "Activating chaincode ${CHAINCODE_ID}"

  echo 'set -x 
  export CORE_PEER_ADDRESS='${org}'-peer1:7051
  
  peer lifecycle \
    chaincode approveformyorg \
    --channelID '${CHANNEL_NAME}' \
    --name '${CHAINCODE_NAME}' \
    --version 1 \
    --package-id '${cc_id}' \
    --sequence 1 \
    -o org0-orderer1:6050 \
    --tls --cafile /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/msp/tlscacerts/org0-tls-ca.pem
  
  peer lifecycle \
    chaincode commit \
    --channelID '${CHANNEL_NAME}' \
    --name '${CHAINCODE_NAME}' \
    --version 1 \
    --sequence 1 \
    -o org0-orderer1:6050 \
    --tls --cafile /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/msp/tlscacerts/org0-tls-ca.pem
  ' | exec kubectl -n $NS exec deploy/${org}-admin-cli -c main -i -- /bin/bash

  pop_fn
}

function query_chaincode() {
  set -x
  # todo: mangle additional $@ parameters with bash escape quotations
  echo '
  export CORE_PEER_ADDRESS=org1-peer1:7051
  peer chaincode query -n '${CHAINCODE_NAME}' -C '${CHANNEL_NAME}' -c '"'$@'"'
  ' | exec kubectl -n $NS exec deploy/org1-admin-cli -c main -i -- /bin/bash
}

function query_chaincode_metadata() {
  set -x
  local args='{"Args":["org.hyperledger.fabric:GetMetadata"]}'
  # todo: mangle additional $@ parameters with bash escape quotations
  log 'Org1-Peer1:'
  echo '
  export CORE_PEER_ADDRESS=org1-peer1:7051
  peer chaincode query -n '${CHAINCODE_NAME}' -C '${CHANNEL_NAME}' -c '"'$args'"'
  ' | exec kubectl -n $NS exec deploy/org1-admin-cli -c main -i -- /bin/bash

  log ''
  log 'Org1-Peer2:'
  echo '
  export CORE_PEER_ADDRESS=org1-peer2:7051
  peer chaincode query -n '${CHAINCODE_NAME}' -C '${CHANNEL_NAME}' -c '"'$args'"'
  ' | exec kubectl -n $NS exec deploy/org1-admin-cli -c main -i -- /bin/bash

}

function invoke_chaincode() {
  # set -x
  # todo: mangle additional $@ parameters with bash escape quotations
  echo '
  export CORE_PEER_ADDRESS=org1-peer1:7051
  peer chaincode \
    invoke \
    -o org0-orderer1:6050 \
    --tls --cafile /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/msp/tlscacerts/org0-tls-ca.pem \
    -n '${CHAINCODE_NAME}' \
    -C '${CHANNEL_NAME}' \
    -c '"'$@'"'
  ' | exec kubectl -n $NS exec deploy/org1-admin-cli -c main -i -- /bin/bash

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
  transfer_chaincode_archive_for ${org}
  install_chaincode_for ${org} peer1
  install_chaincode_for ${org} peer2

  set_chaincode_id
}

# Activate the installed chaincode but do not package/install a new archive.
function activate_chaincode() {
  set -x

  set_chaincode_id
  activate_chaincode_for org1 $CHAINCODE_ID
}

# Install, launch, and activate the chaincode
function deploy_chaincode() {
  set -x

  install_chaincode
  launch_chaincode_service org1 $CHAINCODE_ID $CHAINCODE_IMAGE peer1
  launch_chaincode_service org1 $CHAINCODE_ID $CHAINCODE_IMAGE peer2
  activate_chaincode
}

