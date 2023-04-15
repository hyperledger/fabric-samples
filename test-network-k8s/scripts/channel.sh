#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

function channel_command_group() {
  # set -x

  COMMAND=$1
  shift

  if [ "${COMMAND}" == "create" ]; then
    log "Creating channel \"${CHANNEL_NAME}\":"
    channel_up
    log "🏁 - Channel is ready."

  else
    print_help
    exit 1
  fi
}

function channel_up() {

  register_org_admins
  enroll_org_admins

  create_channel_MSP
  create_genesis_block

  join_channel_orderers
  join_channel_peers
}

function register_org_admins() {
  push_fn "Registering org Admin users"

  register_org_admin org0 org0admin org0adminpw
  register_org_admin org1 org1admin org1adminpw
  register_org_admin org2 org2admin org2adminpw

  pop_fn
}

# Register the org admin user
function register_org_admin() {
  local type=admin
  local org=$1
  local id_name=$2
  local id_secret=$3
  local ca_name=${org}-ca

  echo "Registering org admin $username"

  fabric-ca-client  register \
    --id.name       ${id_name} \
    --id.secret     ${id_secret} \
    --id.type       ${type} \
    --url           https://${ca_name}.${DOMAIN}:${NGINX_HTTPS_PORT} \
    --tls.certfiles $TEMP_DIR/cas/${ca_name}/tlsca-cert.pem \
    --mspdir        $TEMP_DIR/enrollments/${org}/users/${RCAADMIN_USER}/msp \
    --id.attrs      "hf.Registrar.Roles=client,hf.Registrar.Attributes=*,hf.Revoker=true,hf.GenCRL=true,admin=true:ecert,abac.init=true:ecert"
}

function enroll_org_admins() {
  push_fn "Enrolling org Admin users"

  enroll_org_admin orderer  org0 org0admin org0adminpw
  enroll_org_admin peer     org1 org1admin org1adminpw
  enroll_org_admin peer     org2 org2admin org2adminpw

  pop_fn
}

# Enroll the admin client to the local certificate storage folder.
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

  # Determine the CA information and TLS certificate
  CA_NAME=${org}-ca
  CA_DIR=${TEMP_DIR}/cas/${CA_NAME}

  CA_AUTH=${username}:${password}
  CA_HOST=${CA_NAME}.${DOMAIN}
  CA_PORT=${NGINX_HTTPS_PORT}
  CA_URL=https://${CA_AUTH}@${CA_HOST}:${CA_PORT}

  # enroll the org admin
  FABRIC_CA_CLIENT_HOME=${ORG_ADMIN_DIR} fabric-ca-client enroll \
    --url ${CA_URL} \
    --tls.certfiles ${CA_DIR}/tlsca-cert.pem

  # Construct an msp config.yaml
  CA_CERT_NAME=${CA_NAME}-$(echo $DOMAIN | tr -s . -)-${CA_PORT}.pem

  create_msp_config_yaml ${CA_NAME} ${CA_CERT_NAME} ${ORG_ADMIN_DIR}/msp

  # private keys are hashed by name, but we only support one enrollment.
  # test-network examples refer to this as "server.key", which is incorrect.
  # This is the private key used to endorse transactions using the admin's
  # public key.
  mv ${ORG_ADMIN_DIR}/msp/keystore/*_sk ${ORG_ADMIN_DIR}/msp/keystore/key.pem
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

function create_channel_MSP() {
  push_fn "Creating channel MSP"

  create_channel_org_MSP org0 orderer $ORG0_NS
  create_channel_org_MSP org1 peer $ORG1_NS
  create_channel_org_MSP org2 peer $ORG2_NS

  extract_orderer_tls_cert org0 orderer1
  extract_orderer_tls_cert org0 orderer2
  extract_orderer_tls_cert org0 orderer3

  pop_fn
}

function create_channel_org_MSP() {
  local org=$1
  local type=$2
  local ns=$3
  local ca_name=${org}-ca

  ORG_MSP_DIR=${TEMP_DIR}/channel-msp/${type}Organizations/${org}/msp
  mkdir -p ${ORG_MSP_DIR}/cacerts
  mkdir -p ${ORG_MSP_DIR}/tlscacerts

  # extract the CA's signing authority from the CA/cainfo response
  curl -s \
    --cacert ${TEMP_DIR}/cas/${ca_name}/tlsca-cert.pem \
    https://${ca_name}.${DOMAIN}:${NGINX_HTTPS_PORT}/cainfo \
    | jq -r .result.CAChain \
    | base64 -d \
    > ${ORG_MSP_DIR}/cacerts/ca-signcert.pem

  # extract the CA's TLS CA certificate from the cert-manager secret
  kubectl -n $ns get secret ${ca_name}-tls-cert -o json \
    | jq -r .data.\"ca.crt\" \
    | base64 -d \
    > ${ORG_MSP_DIR}/tlscacerts/tlsca-signcert.pem

  # create an MSP config.yaml with the CA's signing certificate
  create_msp_config_yaml ${ca_name} ca-signcert.pem ${ORG_MSP_DIR}
}

# Extract an orderer's TLS signing certificate for inclusion in the channel config block
function extract_orderer_tls_cert() {
  local org=$1
  local orderer=$2
  local ns=$ORG0_NS

  echo "Extracting TLS cert for $org $orderer"

  ORDERER_TLS_DIR=${TEMP_DIR}/channel-msp/ordererOrganizations/${org}/orderers/${org}-${orderer}/tls
  mkdir -p $ORDERER_TLS_DIR/signcerts

  kubectl -n $ns get secret ${org}-${orderer}-tls-cert -o json \
    | jq -r .data.\"tls.crt\" \
    | base64 -d \
    > ${ORDERER_TLS_DIR}/signcerts/tls-cert.pem
}

function create_genesis_block() {
  push_fn "Creating channel genesis block"
  cat ${PWD}/config/org0/configtx-template.yaml | envsubst > ${TEMP_DIR}/configtx.yaml
  FABRIC_CFG_PATH=${TEMP_DIR} \
    configtxgen \
      -profile      TwoOrgsApplicationGenesis \
      -channelID    $CHANNEL_NAME \
      -outputBlock  ${TEMP_DIR}/genesis_block.pb

  # configtxgen -inspectBlock ${TEMP_DIR}/genesis_block.pb

  pop_fn
}

function join_channel_orderers() {
  push_fn "Joining orderers to channel ${CHANNEL_NAME}"

  join_channel_orderer org0 orderer1
  join_channel_orderer org0 orderer2
  join_channel_orderer org0 orderer3

  # todo: readiness / liveiness equivalent for channel?  Needs a little bit to settle before peers can join.
  sleep 10

  pop_fn
}

# Request from the channel ADMIN api that the orderer joins the target channel
function join_channel_orderer() {
  local org=$1
  local orderer=$2

  # The client certificate presented in this case is the admin user's enrollment key.  This is a stronger assertion
  # of identity than the Docker Compose network, which transmits the orderer node's TLS key pair directly
  osnadmin channel join \
    --orderer-address ${org}-${orderer}-admin.${DOMAIN}:${NGINX_HTTPS_PORT} \
    --ca-file         ${TEMP_DIR}/channel-msp/ordererOrganizations/${org}/orderers/${org}-${orderer}/tls/signcerts/tls-cert.pem \
    --client-cert     ${TEMP_DIR}/enrollments/${org}/users/${org}admin/msp/signcerts/cert.pem \
    --client-key      ${TEMP_DIR}/enrollments/${org}/users/${org}admin/msp/keystore/key.pem \
    --channelID       ${CHANNEL_NAME} \
    --config-block    ${TEMP_DIR}/genesis_block.pb
}

function join_channel_peers() {
  join_org_peers org1
  join_org_peers org2
}

function join_org_peers() {
  local org=$1
  push_fn "Joining ${org} peers to channel ${CHANNEL_NAME}"

  # Join peers to channel
  join_channel_peer $org peer1
  join_channel_peer $org peer2

  pop_fn
}

function join_channel_peer() {
  local org=$1
  local peer=$2

  export_peer_context $org $peer

  peer channel join \
    --blockpath   ${TEMP_DIR}/genesis_block.pb \
    --orderer     org0-orderer1.${DOMAIN} \
    --connTimeout ${ORDERER_TIMEOUT} \
    --tls         \
    --cafile      ${TEMP_DIR}/channel-msp/ordererOrganizations/org0/orderers/org0-orderer1/tls/signcerts/tls-cert.pem
}
