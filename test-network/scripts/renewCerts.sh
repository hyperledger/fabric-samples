#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

# This script is used to renew certificates in the test network.
# It re-enrolls the identities using the Fabric CA.

. scripts/utils.sh

function renewOrg1() {
  infoln "Renewing Org1 certificates"

  export FABRIC_CA_CLIENT_HOME=${PWD}/organizations/peerOrganizations/org1.example.com/

  # Remove existing MSP and TLS - this is important to avoid 'malformed serial number'
  # and other parsing errors when fabric-ca-client tries to use old certs for auth.
  rm -rf "${FABRIC_CA_CLIENT_HOME}/msp"
  rm -rf "${FABRIC_CA_CLIENT_HOME}/tls"
  rm -rf "${FABRIC_CA_CLIENT_HOME}/ca"
  rm -rf "${FABRIC_CA_CLIENT_HOME}/tlsca"
  rm -rf "${FABRIC_CA_CLIENT_HOME}/peers"
  rm -rf "${FABRIC_CA_CLIENT_HOME}/users"

  # Re-enroll everything
  . organizations/fabric-ca/registerEnroll.sh
  createOrg1
}

function renewOrg2() {
  infoln "Renewing Org2 certificates"

  export FABRIC_CA_CLIENT_HOME=${PWD}/organizations/peerOrganizations/org2.example.com/

  rm -rf "${FABRIC_CA_CLIENT_HOME}/msp"
  rm -rf "${FABRIC_CA_CLIENT_HOME}/tls"
  rm -rf "${FABRIC_CA_CLIENT_HOME}/ca"
  rm -rf "${FABRIC_CA_CLIENT_HOME}/tlsca"
  rm -rf "${FABRIC_CA_CLIENT_HOME}/peers"
  rm -rf "${FABRIC_CA_CLIENT_HOME}/users"

  . organizations/fabric-ca/registerEnroll.sh
  createOrg2
}

function renewOrderer() {
  infoln "Renewing Orderer certificates"

  export FABRIC_CA_CLIENT_HOME=${PWD}/organizations/ordererOrganizations/example.com

  rm -rf "${FABRIC_CA_CLIENT_HOME}/msp"
  rm -rf "${FABRIC_CA_CLIENT_HOME}/tls"
  rm -rf "${FABRIC_CA_CLIENT_HOME}/ca"
  rm -rf "${FABRIC_CA_CLIENT_HOME}/tlsca"
  rm -rf "${FABRIC_CA_CLIENT_HOME}/orderers"
  rm -rf "${FABRIC_CA_CLIENT_HOME}/users"

  . organizations/fabric-ca/registerEnroll.sh
  createOrderer
}

# Check if CAs are running
if [ $(docker ps -q --filter name=ca_org1 --filter status=running | wc -l) -eq 0 ]; then
  fatalln "Fabric CAs must be running to renew certificates. Please run './network.sh up -ca' first."
fi

renewOrg1
renewOrg2
renewOrderer

infoln "Certificates renewed successfully. You may need to restart your network nodes (peer/orderer) to pick up the new certificates."