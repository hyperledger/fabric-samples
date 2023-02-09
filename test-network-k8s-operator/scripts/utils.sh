#!/bin/bash

function print() {
	GREEN='\033[0;32m'
  NC='\033[0m'
  echo
	echo -e "${GREEN}${1}${NC}"
}

SUCCESS="✅"
WARN="⚠️ "

# tests if varname is defined in the env AND it's an existing directory
function must_declare() {
  local varname=$1

  if [[ ${!varname+x} ]]
  then
    printf "\r%s %-30s%s\n" $SUCCESS $varname ${!varname}
  else
    printf "\r%s  %-30s %s\n" $WARN $varname
    EXIT=1
  fi
}

function check() {
  local name=$1
  local message=$2

  printf "🤔 %s" $name

  if $name &>/dev/null ; then
    printf "\r%s %-30s" $SUCCESS $name
  else
    printf "\r%s  %-30s" $WARN $name
    EXIT=1
  fi

  echo $message
}

function wait_for() {
  local type=$1
  local name=$2

  kubectl -n ${NAMESPACE} wait $type $name --for jsonpath='{.status.type}'=Deployed --timeout=3m
  kubectl -n ${NAMESPACE} rollout status deploy $name
}

function apply_template() {
  local template=$1
  cat ${template} | envsubst | kubectl -n ${NAMESPACE} apply -f -
}

# Read a certificate by name from a node connection-profile config map.
function connection_profile_cert() {
  local node=$1
  local path=$2

  kubectl -n ${NAMESPACE} get cm/${node}-connection-profile -o json \
    | jq -r .binaryData.\"profile.json\" \
    | base64 -d \
    | jq -r ${path}
}

# Extract, decode, and save a certificate in .pem format to a local file
function write_pem() {
  local node=$1
  local jq_path=$2
  local to_file=$3

  mkdir -p $(dirname $to_file)

  echo $(connection_profile_cert $node $jq_path) | base64 -d >& $to_file
}

# create an enrollment MSP config.yaml
function write_msp_config() {
  local ca_name=$1
  local ca_cert_name=$2
  local msp_dir=$3

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

# Enroll a user at an org CA.
function enroll() {
  do_enroll msp ca $@
}

# Enroll a user at an org TLS CA
function enroll_tls() {
  do_enroll tls tlsca $@
}

function do_enroll() {
  local msp_type=$1
  local caname=$2
  local org=$3
  local user=$4
  local pazz=$5

  # Skip the enrollment if a previous enrollment key exists.
  local user_dir=$ENROLLMENTS_DIR/$user
  local user_key=$user_dir/$msp_type/keystore/key.pem

  if [ -f "$user_key" ]; then
    print "$user has already been enrolled at $org $caname"
    return
  fi

  print "enrolling $org $caname $user"
  local ca_url=https://${user}:${pazz}@${org}-ca-ca.${org}.localho.st
  local tls_certfile=$ENROLLMENTS_DIR/ca-tls-cert.pem

  fabric-ca-client  enroll \
    --url           $ca_url \
    --tls.certfiles $tls_certfile \
    --mspdir        $user_dir/$msp_type \
    --caname        $caname

  # Enrollment creates a key with a dynamic, hashed file name.  Move this to a predictable location
  mv $user_dir/$msp_type/keystore/*_sk $user_key
}

# Set the peer CLI environment in order to run commands as an org admin
function appear_as() {
  local mspid=$1
  local org=$2
  local peer=$3

  export FABRIC_CFG_PATH=${PWD}/channel-config/config
  export CORE_PEER_ADDRESS=${org}-${peer}-peer.${org}.localho.st:443
  export CORE_PEER_LOCALMSPID=${mspid}
  export CORE_PEER_MSPCONFIGPATH=$PWD/organizations/${org}/enrollments/${org}admin/msp
  export CORE_PEER_TLS_ENABLED=true
  export CORE_PEER_TLS_ROOTCERT_FILE=$PWD/channel-config/organizations/peerOrganizations/${org}.localho.st/msp/tlscacerts/tlsca-signcert.pem
  export CORE_PEER_CLIENT_CONNTIMEOUT=15s
  export CORE_PEER_DELIVERYCLIENT_CONNTIMEOUT=15s

  export ORDERER_ENDPOINT=org0-orderernode1-orderer.org0.localho.st:443
  export ORDERER_TLS_CERT=${PWD}/channel-config/organizations/ordererOrganizations/org0.localho.st/orderers/orderernode1/tls/signcerts/tls-cert.pem
}
