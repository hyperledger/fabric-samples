#!/bin/bash
#
# Copyright contributors to the Hyperledger Fabric Operator project
#
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at:
#
# 	  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

  # todo: Refuse to overwrite an existing admin enrollment ?


function channel_up() {
  set -x

  enroll_org_admins

  create_channel_msp
  create_genesis_block

  join_channel_orderers
  join_channel_peers
}

# create an enrollment MSP config.yaml
function create_msp_config_yaml() {
  local ca_name=$1
  local ca_cert_name=$2
  local msp_dir=$3
  echo "Creating msp config ${msp_dir}/config.yaml with cert ${ca_cert_name}"

  cat << EOF > ${msp_dir}/config.yaml
NodeOUs:
  Enable: true
  ClientOUIdentifier:
    Certificate: cacerts/${ca_cert_name}
    OrganizationalUnitIdentifier: client
  PeerOUIdentifier:
    Certificate: cacerts/${ca_cert_name}
    OrganizationalUnitIdentifier: peer
  AdminOUIdentifier:
    Certificate: cacerts/${ca_cert_name}
    OrganizationalUnitIdentifier: admin
  OrdererOUIdentifier:
    Certificate: cacerts/${ca_cert_name}
    OrganizationalUnitIdentifier: orderer
EOF
}

function get_connection_profile() {
  local node_name=$1
  local connection_profile=$2

  mkdir -p $(dirname ${connection_profile})

  echo "writing $node_name connection profile to $connection_profile"

  kubectl -n $NS get cm/${node_name}-connection-profile -o json \
    | jq -r .binaryData.\"profile.json\" \
    | base64 -d \
    > ${connection_profile}
}

function enroll_org_admin() {
  local type=$1
  local org=$2
  local username=$3
  local password=$4

  echo "Enrolling $type org admin $username"

  ENROLLMENTS_DIR=${TEMP_DIR}/enrollments
  ORG_ADMIN_DIR=${ENROLLMENTS_DIR}/${org}/users/${username}

  # skip the enrollment if the admin certificate is available.
  if [ -f "${ORG_ADMIN_DIR}/msp/keystore/key.pem" ]; then
    echo "Found an existing admin enrollment at ${ORG_ADMIN_DIR}"
    return
  fi

  # Retrieve the CA information from Kubernetes
  CA_NAME=${org}-ca
  CA_DIR=${TEMP_DIR}/cas/${CA_NAME}
  CONNECTION_PROFILE=${CA_DIR}/connection-profile.json

  get_connection_profile $CA_NAME $CONNECTION_PROFILE

  # extract the CA enrollment URL and tls cert from the org connection profile
  CA_AUTH=${username}:${password}
  CA_ENDPOINT=$(jq -r .endpoints.api $CONNECTION_PROFILE)
  CA_HOST=$(echo ${CA_ENDPOINT} | cut -d/ -f3 | tr ':' '\n' | head -1)
  CA_PORT=$(echo ${CA_ENDPOINT} | cut -d/ -f3 | tr ':' '\n' | tail -1)
  CA_URL=https://${CA_AUTH}@${CA_HOST}:${CA_PORT}

  jq -r .tls.cert $CONNECTION_PROFILE | base64 -d >& $CA_DIR/tls-cert.pem

  # enroll the admin user
  FABRIC_CA_CLIENT_HOME=${ORG_ADMIN_DIR} fabric-ca-client enroll --url ${CA_URL} --tls.certfiles ${CA_DIR}/tls-cert.pem

  # Construct an msp config.yaml
  CA_CERT_NAME=${NS}-${CA_NAME}-ca-$(echo $INGRESS_DOMAIN | tr -s . -)-${CA_PORT}.pem

  create_msp_config_yaml ${CA_NAME} ${CA_CERT_NAME} ${ORG_ADMIN_DIR}/msp

  # private keys are hashed by name, but we only support one enrollment.
  # test-network examples refer to this as "server.key", which is incorrect.
  # This is the private key used to endorse transactions using the admin's
  # public key.
  mv ${ORG_ADMIN_DIR}/msp/keystore/*_sk ${ORG_ADMIN_DIR}/msp/keystore/key.pem


  # enroll the admin user at the TLS CA - used for the channel admin API
  FABRIC_CA_CLIENT_HOME=${ORG_ADMIN_DIR} \
    fabric-ca-client enroll \
    --url ${CA_URL} \
    --tls.certfiles ${CA_DIR}/tls-cert.pem \
    --mspdir ${ORG_ADMIN_DIR}/tls \
    --caname tlsca

  mv ${ORG_ADMIN_DIR}/tls/keystore/*_sk ${ORG_ADMIN_DIR}/tls/keystore/key.pem
}

function enroll_org_admins() {
  push_fn "Enrolling org admin users"

  enroll_org_admin orderer org0 org0admin org0adminpw
  enroll_org_admin peer    org1 org1admin org1adminpw
  enroll_org_admin peer    org2 org2admin org2adminpw

  pop_fn
}

function create_channel_org_msp() {
  local type=$1
  local org=$2
  echo "Creating channel org $org MSP"

  CA_DIR=${TEMP_DIR}/cas/${org}-ca
  ORG_MSP_DIR=${TEMP_DIR}/channel-msp/${type}Organizations/${org}/msp

  mkdir -p ${ORG_MSP_DIR}/cacerts
  mkdir -p ${ORG_MSP_DIR}/tlscacerts

  jq -r .ca.signcerts ${CA_DIR}/connection-profile.json | base64 -d >& ${ORG_MSP_DIR}/cacerts/ca-signcert.pem
  jq -r .tlsca.signcerts ${CA_DIR}/connection-profile.json | base64 -d >& ${ORG_MSP_DIR}/tlscacerts/tlsca-signcert.pem

  create_msp_config_yaml ${org}-ca ca-signcert.pem ${ORG_MSP_DIR}
}

function create_channel_msp() {
  push_fn "Creating channel MSP"

  create_channel_org_msp orderer org0
  create_channel_org_msp peer org1
  create_channel_org_msp peer org2

  extract_orderer_tls_cert org0 orderersnode1
  extract_orderer_tls_cert org0 orderersnode2
  extract_orderer_tls_cert org0 orderersnode3

  pop_fn
}

function extract_orderer_tls_cert() {
  local org=$1
  local orderer=$2

  echo "Extracting TLS cert for $org $orderer"

  ORDERER_NAME=${org}-${orderer}
  ORDERER_DIR=${TEMP_DIR}/channel-msp/ordererOrganizations/${org}/orderers/${ORDERER_NAME}
  ORDERER_TLS_DIR=${ORDERER_DIR}/tls
  CONNECTION_PROFILE=${ORDERER_DIR}/connection-profile.json

  get_connection_profile $ORDERER_NAME $CONNECTION_PROFILE

  mkdir -p $ORDERER_TLS_DIR/signcerts

  jq -r .tls.signcerts ${CONNECTION_PROFILE} \
    | base64 -d \
    >& $ORDERER_TLS_DIR/signcerts/tls-cert.pem
}

function create_genesis_block() {
  push_fn "Creating channel genesis block"

  mkdir -p ${TEMP_DIR}/config
  cp ${PWD}/config/core.yaml ${TEMP_DIR}/config/

  # The channel configtx file needs to specify dynamic elements from the environment,
  # for instance, the ${INGRESS_DOMAIN} for ingress controller and service endpoints.
  cat ${PWD}/config/configtx-template.yaml | envsubst > ${TEMP_DIR}/config/configtx.yaml

  FABRIC_CFG_PATH=${TEMP_DIR}/config \
    configtxgen \
      -profile      TwoOrgsApplicationGenesis \
      -channelID    $CHANNEL_NAME \
      -outputBlock  ${TEMP_DIR}/genesis_block.pb

#  configtxgen -inspectBlock ${TEMP_DIR}/genesis_block.pb

  pop_fn
}

function join_channel_orderers() {
  push_fn "Joining orderers to channel ${CHANNEL_NAME}"

  join_channel_orderer org0 orderersnode1
  join_channel_orderer org0 orderersnode2
  join_channel_orderer org0 orderersnode3

  # todo: readiness / liveiness equivalent for channel?  Needs a little bit to settle before peers can join.
  sleep 10

  pop_fn
}

# Request from the channel ADMIN api that the orderer joins the target channel
function join_channel_orderer() {
  local org=$1
  local orderer=$2

  # The client certificate presented in this case is the admin USER TLS enrollment key.  This is a stronger assertion
  # of identity than the Docker Compose network, which transmits the orderer NODE TLS key pair directly

  osnadmin channel join \
    --orderer-address ${NS}-${org}-${orderer}-admin.${INGRESS_DOMAIN} \
    --ca-file         ${TEMP_DIR}/channel-msp/ordererOrganizations/${org}/orderers/${org}-${orderer}/tls/signcerts/tls-cert.pem \
    --client-cert     ${TEMP_DIR}/enrollments/${org}/users/${org}admin/tls/signcerts/cert.pem \
    --client-key      ${TEMP_DIR}/enrollments/${org}/users/${org}admin/tls/keystore/key.pem \
    --channelID       ${CHANNEL_NAME} \
    --config-block    ${TEMP_DIR}/genesis_block.pb
}

function join_channel_peers() {
  join_org_peers 1
  join_org_peers 2
}

function join_org_peers() {
  local orgnum=$1
  push_fn "Joining org${orgnum} peers to channel ${CHANNEL_NAME}"

  # Join peers to channel
  join_channel_peer $orgnum 1
  join_channel_peer $orgnum 2

  pop_fn
}

function join_channel_peer() {
  local orgnum=$1
  local peernum=$2

  export_peer_context $orgnum $peernum

  peer channel join \
    --blockpath   ${TEMP_DIR}/genesis_block.pb
}
