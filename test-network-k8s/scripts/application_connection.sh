#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

function app_extract_MSP_archives() {
  mkdir -p build/msp
  set -ex
  kubectl -n $NS exec deploy/org1-ecert-ca -- tar zcf - -C /var/hyperledger/fabric organizations/peerOrganizations/org1.example.com/msp | tar zxf - -C build/msp
  kubectl -n $NS exec deploy/org2-ecert-ca -- tar zcf - -C /var/hyperledger/fabric organizations/peerOrganizations/org2.example.com/msp | tar zxf - -C build/msp

  kubectl -n $NS exec deploy/org1-ecert-ca -- tar zcf - -C /var/hyperledger/fabric organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp | tar zxf - -C build/msp
  kubectl -n $NS exec deploy/org2-ecert-ca -- tar zcf - -C /var/hyperledger/fabric organizations/peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp | tar zxf - -C build/msp
}

function app_one_line_pem {
    echo "`awk 'NF {sub(/\\n/, ""); printf "%s\\\\\\\n",$0;}' $1`"
}

function app_json_ccp {
  local ORG=$1
  local PP=$(one_line_pem $2)
  local CP=$(one_line_pem $3)
  sed -e "s/\${ORG}/$ORG/" \
      -e "s#\${PEERPEM}#$PP#" \
      -e "s#\${CAPEM}#$CP#" \
      scripts/ccp-template.json
}

function app_id {
  local MSP=$1
  local CERT=$(one_line_pem $2)
  local PK=$(one_line_pem $3)

  sed -e "s#\${CERTIFICATE}#$CERT#" \
      -e "s#\${PRIVATE_KEY}#$PK#" \
      -e "s#\${MSPID}#$MSP#" \
      scripts/appuser.id.template
}

function construct_application_configmap() {
  push_fn "Constructing application connection profiles"

  app_extract_MSP_archives

  mkdir -p build/application/wallet
  mkdir -p build/application/gateways
  
  local peer_pem=build/msp/organizations/peerOrganizations/org1.example.com/msp/tlscacerts/org1-tls-ca.pem
  local ca_pem=build/msp/organizations/peerOrganizations/org1.example.com/msp/cacerts/org1-ecert-ca.pem

  echo "$(json_ccp 1 $peer_pem $ca_pem)" > build/application/gateways/org1_ccp.json
  
  peer_pem=build/msp/organizations/peerOrganizations/org2.example.com/msp/tlscacerts/org2-tls-ca.pem
  ca_pem=build/msp/organizations/peerOrganizations/org2.example.com/msp/cacerts/org2-ecert-ca.pem

  echo "$(json_ccp 2 $peer_pem $ca_pem)" > build/application/gateways/org2_ccp.json

  pop_fn

  push_fn "Getting Application Identities"

  local cert=build/msp/organizations/peerOrganizations/org1.example.com/users/Admin\@org1.example.com/msp/signcerts/cert.pem
  local pk=build/msp/organizations/peerOrganizations/org1.example.com/users/Admin\@org1.example.com/msp/keystore/server.key

  echo "$(app_id Org1MSP $cert $pk)" > build/application/wallet/appuser_org1.id

  local cert=build/msp/organizations/peerOrganizations/org2.example.com/users/Admin\@org2.example.com/msp/signcerts/cert.pem
  local pk=build/msp/organizations/peerOrganizations/org2.example.com/users/Admin\@org2.example.com/msp/keystore/server.key

  echo "$(app_id Org2MSP $cert $pk)" > build/application/wallet/appuser_org2.id

  pop_fn

  push_fn "Creating ConfigMap \"app-fabric-tls-v1-map\" with TLS certificates for the application"
  kubectl -n $NS delete configmap app-fabric-tls-v1-map || true
  kubectl -n $NS create configmap app-fabric-tls-v1-map --from-file=./build/msp/organizations/peerOrganizations/org1.example.com/msp/tlscacerts
  pop_fn

  push_fn "Creating ConfigMap \"app-fabric-ids-v1-map\" with identities for the application"
  kubectl -n $NS delete configmap app-fabric-ids-v1-map || true
  kubectl -n $NS create configmap app-fabric-ids-v1-map --from-file=./build/application/wallet
  pop_fn

  push_fn "Creating ConfigMap \"app-fabric-ccp-v1-map\" with ConnectionProfile for the application"
  kubectl -n $NS delete configmap app-fabric-ccp-v1-map || true
  kubectl -n $NS create configmap app-fabric-ccp-v1-map --from-file=./build/application/gateways
  pop_fn

  push_fn "Creating ConfigMap \"app-fabric-org1-v1-map\" with Organization 1 information for the application"

cat <<EOF > build/app-fabric-org1-v1-map.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-fabric-org1-v1-map
data:
  fabric_channel: ${CHANNEL_NAME}
  fabric_contract: ${CHAINCODE_NAME}
  fabric_wallet_dir: /fabric/application/wallet
  fabric_gateway_hostport: org1-peer-gateway-svc:7051
  fabric_gateway_sslHostOverride: org1-peer-gateway-svc
  fabric_user: appuser_org1
  fabric_gateway_tlsCertPath: /fabric/tlscacerts/org1-tls-ca.pem
EOF

  kubectl -n $NS apply -f build/app-fabric-org1-v1-map.yaml

  # todo: could add the second org here

  pop_fn
}


function application_connection() {

 construct_application_configmap

log
 log "For k8s applications:"
 log "Config Maps created for the application"
 log "To deploy your application updated the image name and issue these commands"
 log ""
 log "kubectl -n $NS apply -f kube/application-deployment.yaml"
 log "kubectl -n $NS rollout status deploy/application-deployment"
 log
 log "For non-k8s applications:"
 log "ConnectionPrfiles are in ${PWD}/build/application/gateways"
 log "Identities are in  ${PWD}/build/application/wallets"
 log
}