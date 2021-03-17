#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

function createOrg5 {
	infoln "Enrolling the CA admin"
	mkdir -p ../organizations/peerOrganizations/org5.example.com/

	export FABRIC_CA_CLIENT_HOME=${PWD}/../organizations/peerOrganizations/org5.example.com/

  set -x
  fabric-ca-client enroll -u https://admin:adminpw@localhost:15054 --caname ca-org5 --tls.certfiles "${PWD}/fabric-ca/org5/tls-cert.pem"
  { set +x; } 2>/dev/null

  echo 'NodeOUs:
  Enable: true
  ClientOUIdentifier:
    Certificate: cacerts/localhost-15054-ca-org5.pem
    OrganizationalUnitIdentifier: client
  PeerOUIdentifier:
    Certificate: cacerts/localhost-15054-ca-org5.pem
    OrganizationalUnitIdentifier: peer
  AdminOUIdentifier:
    Certificate: cacerts/localhost-15054-ca-org5.pem
    OrganizationalUnitIdentifier: admin
  OrdererOUIdentifier:
    Certificate: cacerts/localhost-15054-ca-org5.pem
    OrganizationalUnitIdentifier: orderer' > "${PWD}/../organizations/peerOrganizations/org5.example.com/msp/config.yaml"

	infoln "Registering peer0"
  set -x
	fabric-ca-client register --caname ca-org5 --id.name peer0 --id.secret peer0pw --id.type peer --tls.certfiles "${PWD}/fabric-ca/org5/tls-cert.pem"
  { set +x; } 2>/dev/null

  infoln "Registering user"
  set -x
  fabric-ca-client register --caname ca-org5 --id.name user1 --id.secret user1pw --id.type client --tls.certfiles "${PWD}/fabric-ca/org5/tls-cert.pem"
  { set +x; } 2>/dev/null

  infoln "Registering the org admin"
  set -x
  fabric-ca-client register --caname ca-org5 --id.name org5admin --id.secret org5adminpw --id.type admin --tls.certfiles "${PWD}/fabric-ca/org5/tls-cert.pem"
  { set +x; } 2>/dev/null

  infoln "Generating the peer0 msp"
  set -x
	fabric-ca-client enroll -u https://peer0:peer0pw@localhost:15054 --caname ca-org5 -M "${PWD}/../organizations/peerOrganizations/org5.example.com/peers/peer0.org5.example.com/msp" --csr.hosts peer0.org5.example.com --tls.certfiles "${PWD}/fabric-ca/org5/tls-cert.pem"
  { set +x; } 2>/dev/null

  cp "${PWD}/../organizations/peerOrganizations/org5.example.com/msp/config.yaml" "${PWD}/../organizations/peerOrganizations/org5.example.com/peers/peer0.org5.example.com/msp/config.yaml"

  infoln "Generating the peer0-tls certificates"
  set -x
  fabric-ca-client enroll -u https://peer0:peer0pw@localhost:15054 --caname ca-org5 -M "${PWD}/../organizations/peerOrganizations/org5.example.com/peers/peer0.org5.example.com/tls" --enrollment.profile tls --csr.hosts peer0.org5.example.com --csr.hosts localhost --tls.certfiles "${PWD}/fabric-ca/org5/tls-cert.pem"
  { set +x; } 2>/dev/null


  cp "${PWD}/../organizations/peerOrganizations/org5.example.com/peers/peer0.org5.example.com/tls/tlscacerts/"* "${PWD}/../organizations/peerOrganizations/org5.example.com/peers/peer0.org5.example.com/tls/ca.crt"
  cp "${PWD}/../organizations/peerOrganizations/org5.example.com/peers/peer0.org5.example.com/tls/signcerts/"* "${PWD}/../organizations/peerOrganizations/org5.example.com/peers/peer0.org5.example.com/tls/server.crt"
  cp "${PWD}/../organizations/peerOrganizations/org5.example.com/peers/peer0.org5.example.com/tls/keystore/"* "${PWD}/../organizations/peerOrganizations/org5.example.com/peers/peer0.org5.example.com/tls/server.key"

  mkdir "${PWD}/../organizations/peerOrganizations/org5.example.com/msp/tlscacerts"
  cp "${PWD}/../organizations/peerOrganizations/org5.example.com/peers/peer0.org5.example.com/tls/tlscacerts/"* "${PWD}/../organizations/peerOrganizations/org5.example.com/msp/tlscacerts/ca.crt"

  mkdir "${PWD}/../organizations/peerOrganizations/org5.example.com/tlsca"
  cp "${PWD}/../organizations/peerOrganizations/org5.example.com/peers/peer0.org5.example.com/tls/tlscacerts/"* "${PWD}/../organizations/peerOrganizations/org5.example.com/tlsca/tlsca.org5.example.com-cert.pem"

  mkdir "${PWD}/../organizations/peerOrganizations/org5.example.com/ca"
  cp "${PWD}/../organizations/peerOrganizations/org5.example.com/peers/peer0.org5.example.com/msp/cacerts/"* "${PWD}/../organizations/peerOrganizations/org5.example.com/ca/ca.org5.example.com-cert.pem"

  infoln "Generating the user msp"
  set -x
	fabric-ca-client enroll -u https://user1:user1pw@localhost:15054 --caname ca-org5 -M "${PWD}/../organizations/peerOrganizations/org5.example.com/users/User1@org5.example.com/msp" --tls.certfiles "${PWD}/fabric-ca/org5/tls-cert.pem"
  { set +x; } 2>/dev/null

  cp "${PWD}/../organizations/peerOrganizations/org5.example.com/msp/config.yaml" "${PWD}/../organizations/peerOrganizations/org5.example.com/users/User1@org5.example.com/msp/config.yaml"

  infoln "Generating the org admin msp"
  set -x
	fabric-ca-client enroll -u https://org5admin:org5adminpw@localhost:15054 --caname ca-org5 -M "${PWD}/../organizations/peerOrganizations/org5.example.com/users/Admin@org5.example.com/msp" --tls.certfiles "${PWD}/fabric-ca/org5/tls-cert.pem"
  { set +x; } 2>/dev/null

  cp "${PWD}/../organizations/peerOrganizations/org5.example.com/msp/config.yaml" "${PWD}/../organizations/peerOrganizations/org5.example.com/users/Admin@org5.example.com/msp/config.yaml"
}
