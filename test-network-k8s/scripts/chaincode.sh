#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

# Convenience routine to "do everything" required to bring up a sample CC.
function deploy_chaincode() {
  local cc_folder=$1
  local build_folder=${cc_folder}/build
  local cc_package=${build_folder}/chaincode.tgz

  build_chaincode_image   ${cc_folder}

  mkdir -p ${build_folder}

  package_chaincode       ${cc_folder}/ccpackage ${cc_package}
  extract_chaincode_image ${cc_package}
  extract_chaincode_name  ${cc_package}

  launch_chaincode        ${cc_package}

  activate_chaincode      ${CHAINCODE_NAME} ${cc_package}
}

# Convenience routine to "do everything other than package and launch" a sample CC.
# This is useful in local debugging scenarios, where
function activate_chaincode() {
  local cc_name=$1
  local cc_package=$2

  set_chaincode_id    ${cc_package}

  install_chaincode   ${cc_package}
  approve_chaincode   ${cc_name} ${CHAINCODE_ID}
  commit_chaincode    ${cc_name}
}

function query_chaincode() {
  local cc_name=$1
  shift

  set -x

  export_peer_context org1 peer1

  peer chaincode query \
    -n  $cc_name \
    -C  $CHANNEL_NAME \
    -c  $@
}

function query_chaincode_metadata() {
  local cc_name=$1
  shift

  set -x
  local args='{"Args":["org.hyperledger.fabric:GetMetadata"]}'

  log ''
  log 'Org1-Peer1:'
  export_peer_context org1 peer1
  peer chaincode query -n $cc_name -C $CHANNEL_NAME -c $args

  log ''
  log 'Org1-Peer2:'
  export_peer_context org1 peer2
  peer chaincode query -n $cc_name -C $CHANNEL_NAME -c $args
}

function invoke_chaincode() {
  local cc_name=$1
  shift

  # set -x

  export_peer_context org1 peer1

  peer chaincode invoke \
    -n              $cc_name \
    -C              $CHANNEL_NAME \
    -c              $@ \
    --orderer       org0-orderer1.${DOMAIN}:443 \
    --tls --cafile  ${TEMP_DIR}/channel-msp/ordererOrganizations/org0/orderers/org0-orderer1/tls/signcerts/tls-cert.pem

  sleep 2
}

function build_chaincode_image() {
  local cc_folder=$1
  local cc_image=$(jq -r .image ${cc_folder}/ccpackage/ccaas.json)

  push_fn "Building chaincode image ${cc_image}"

  docker build -t ${cc_image} ${cc_folder}

  kind load docker-image ${cc_image}

  pop_fn
}

function package_chaincode() {
  local cc_folder=$1
  local cc_archive=$2
  local archive_name=$(basename $cc_archive)
  push_fn "Packaging chaincode ${archive_name}"

  tar -C ${cc_folder} -zcf ${cc_folder}/code.tar.gz connection.json ccaas.json
  tar -C ${cc_folder} -zcf ${cc_archive} code.tar.gz metadata.json

  rm ${cc_folder}/code.tar.gz

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

function install_chaincode_for() {
  local org=$1
  local peer=$2
  local cc_package=$3
  push_fn "Installing chaincode for org ${org} peer ${peer}"

  export_peer_context $org $peer

  peer lifecycle chaincode install $cc_package

  pop_fn
}

# Package and install the chaincode, but do not activate.
function install_chaincode() {
  local org=org1
  local cc_package=$1

  install_chaincode_for ${org} peer1 ${cc_package}
  install_chaincode_for ${org} peer2 ${cc_package}
}

# approve the chaincode package for an org and assign a name
function approve_chaincode() {
  local org=org1
  local peer=peer1
  local cc_name=$1
  local cc_id=$2
  push_fn "Approving chaincode ${cc_name} with ID ${cc_id}"

  export_peer_context $org $peer

  peer lifecycle \
    chaincode approveformyorg \
    --channelID     ${CHANNEL_NAME} \
    --name          ${cc_name} \
    --version       1 \
    --package-id    ${cc_id} \
    --sequence      1 \
    --orderer       org0-orderer1.${DOMAIN}:443 \
    --tls --cafile  ${TEMP_DIR}/channel-msp/ordererOrganizations/org0/orderers/org0-orderer1/tls/signcerts/tls-cert.pem

  pop_fn
}

# commit the named chaincode for an org
function commit_chaincode() {
  local org=org1
  local peer=peer1
  local cc_name=$1
  push_fn "Committing chaincode ${cc_name}"

  export_peer_context $org $peer

  peer lifecycle \
    chaincode commit \
    --channelID     ${CHANNEL_NAME} \
    --name          ${cc_name} \
    --version       1 \
    --sequence      1 \
    --orderer       org0-orderer1.${DOMAIN}:443 \
    --tls --cafile  ${TEMP_DIR}/channel-msp/ordererOrganizations/org0/orderers/org0-orderer1/tls/signcerts/tls-cert.pem

  pop_fn
}

# The chaincode docker image is stored in the code.tar.gz ccaas.json
function extract_chaincode_image() {
  CHAINCODE_IMAGE=$(tar zxfO $1 code.tar.gz | tar zxfO - ccaas.json | jq -r .image)
}

function extract_chaincode_name() {
  CHAINCODE_NAME=$(tar zxfO $1 code.tar.gz | tar zxfO - ccaas.json | jq -r .name)
}

function launch_chaincode() {
  local cc_package=$1

  set_chaincode_id ${cc_package}

  extract_chaincode_image ${cc_package}
  extract_chaincode_name ${cc_package}

  launch_chaincode_service org1 $CHAINCODE_ID $CHAINCODE_IMAGE peer1
  launch_chaincode_service org1 $CHAINCODE_ID $CHAINCODE_IMAGE peer2
}

function set_chaincode_id() {
  local cc_package=$1

  cc_sha256=$(shasum -a 256 ${cc_package} | tr -s ' ' | cut -d ' ' -f 1)
  cc_label=$(tar zxfO ${cc_package} metadata.json | jq -r '.label')

  CHAINCODE_ID=${cc_label}:${cc_sha256}
}

# chaincode "group" commands.  Like "main" for chaincode sub-command group.
function chaincode_command_group() {
  #set -x

  COMMAND=$1
  shift

  if [ "${COMMAND}" == "deploy" ]; then
    log "Deploying chaincode"
    deploy_chaincode $@
    log "🏁 - Chaincode is ready."

  elif [ "${COMMAND}" == "activate" ]; then
    log "Activating chaincode"
    activate_chaincode $@
    log "🏁 - Chaincode is ready."

  elif [ "${COMMAND}" == "package" ]; then
    log "Packaging chaincode"
    package_chaincode $@
    log "🏁 - Chaincode package is ready."

  elif [ "${COMMAND}" == "id" ]; then
    set_chaincode_id $@
    log $CHAINCODE_ID

  elif [ "${COMMAND}" == "launch" ]; then
    log "Launching chaincode services"
    launch_chaincode $@
    log "🏁 - Chaincode services are ready"

  elif [ "${COMMAND}" == "install" ]; then
    log "Installing chaincode for org1"
    install_chaincode $@
    log "🏁 - Chaincode is installed"

  elif [ "${COMMAND}" == "approve" ]; then
    log "Approving chaincode for org1"
    approve_chaincode $@
    log "🏁 - Chaincode is approved"

  elif [ "${COMMAND}" == "commit" ]; then
    log "Committing chaincode for org1"
    commit_chaincode $@
    log "🏁 - Chaincode is committed"

  elif [ "${COMMAND}" == "invoke" ]; then
    invoke_chaincode $@ 2>> ${LOG_FILE}

  elif [ "${COMMAND}" == "query" ]; then
    query_chaincode $@ >> ${LOG_FILE}

  elif [ "${COMMAND}" == "metadata" ]; then
    query_chaincode_metadata $@ >> ${LOG_FILE}

  else
    print_help
    exit 1
  fi
}
