#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

function extract_MSP_archives() {
  mkdir -p build/msp

  kubectl -n $NS exec deploy/org1-ecert-ca -- tar zcf - -C /var/hyperledger/fabric organizations/peerOrganizations/org1.example.com/msp | tar zxf - -C build/msp
  kubectl -n $NS exec deploy/org2-ecert-ca -- tar zcf - -C /var/hyperledger/fabric organizations/peerOrganizations/org2.example.com/msp | tar zxf - -C build/msp

  kubectl -n $NS exec deploy/org1-ecert-ca -- tar zcf - -C /var/hyperledger/fabric organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp | tar zxf - -C build/msp
  kubectl -n $NS exec deploy/org2-ecert-ca -- tar zcf - -C /var/hyperledger/fabric organizations/peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp | tar zxf - -C build/msp
}

function one_line_pem {
    echo "`awk 'NF {sub(/\\n/, ""); printf "%s\\\\\\\n",$0;}' $1`"
}

function json_ccp {
  local ORG=$1
  local PP=$(one_line_pem $2)
  local CP=$(one_line_pem $3)
  sed -e "s/\${ORG}/$ORG/" \
      -e "s#\${PEERPEM}#$PP#" \
      -e "s#\${CAPEM}#$CP#" \
      scripts/ccp-template.json
}

function construct_rest_sample_configmap() {
  push_fn "Constructing fabric-rest-sample connection profiles"

  extract_MSP_archives

  mkdir -p build/fabric-rest-sample-config

  local peer_pem=build/msp/organizations/peerOrganizations/org1.example.com/msp/tlscacerts/org1-tls-ca.pem
  local ca_pem=build/msp/organizations/peerOrganizations/org1.example.com/msp/cacerts/org1-ecert-ca.pem

  echo "$(json_ccp 1 $peer_pem $ca_pem)" > build/fabric-rest-sample-config/HLF_CONNECTION_PROFILE_ORG1
  
  peer_pem=build/msp/organizations/peerOrganizations/org2.example.com/msp/tlscacerts/org2-tls-ca.pem
  ca_pem=build/msp/organizations/peerOrganizations/org2.example.com/msp/cacerts/org2-ecert-ca.pem

  echo "$(json_ccp 2 $peer_pem $ca_pem)" > build/fabric-rest-sample-config/HLF_CONNECTION_PROFILE_ORG2
  
  cat build/msp/organizations/peerOrganizations/org1.example.com/users/Admin\@org1.example.com/msp/signcerts/cert.pem > build/fabric-rest-sample-config/HLF_CERTIFICATE_ORG1
  cat build/msp/organizations/peerOrganizations/org2.example.com/users/Admin\@org2.example.com/msp/signcerts/cert.pem > build/fabric-rest-sample-config/HLF_CERTIFICATE_ORG2

  cat build/msp/organizations/peerOrganizations/org1.example.com/users/Admin\@org1.example.com/msp/keystore/server.key > build/fabric-rest-sample-config/HLF_PRIVATE_KEY_ORG1
  cat build/msp/organizations/peerOrganizations/org2.example.com/users/Admin\@org2.example.com/msp/keystore/server.key > build/fabric-rest-sample-config/HLF_PRIVATE_KEY_ORG2

  kubectl -n $NS delete configmap fabric-rest-sample-config || true
  kubectl -n $NS create configmap fabric-rest-sample-config --from-file=build/fabric-rest-sample-config/

  pop_fn
}

# todo: Make sure to port this to IKS / ICP
function ensure_rest_sample_image() {
  push_fn "Ensuring fabric-rest-sample image"

  # todo: apply a tag / label to avoid pulling :latest from ghcr.io

  pop_fn 0
}

function rollout_rest_sample() {
  push_fn "Starting fabric-rest-sample"

  kubectl -n $NS apply -f kube/fabric-rest-sample.yaml
  kubectl -n $NS rollout status deploy/fabric-rest-sample

  pop_fn
}

function launch_rest_sample() {
  ensure_rest_sample_image
  construct_rest_sample_configmap
  rollout_rest_sample

  log ""
  log "The fabric-rest-sample has started.  See https://github.com/hyperledgendary/fabric-rest-sample/tree/main/asset-transfer-basic/rest-api-typescript#rest-api for additional usage."
  log "To access the endpoint:"
  log ""
  log "export SAMPLE_APIKEY=97834158-3224-4CE7-95F9-A148C886653E"
  log 'curl -s --header "X-Api-Key: ${SAMPLE_APIKEY}" http://localhost/api/assets'
  log ""
}