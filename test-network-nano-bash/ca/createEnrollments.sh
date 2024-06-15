#!/usr/bin/env sh
#
# SPDX-License-Identifier: Apache-2.0
#
export PATH="${PWD}"/../../fabric/build/bin:"${PWD}"/../bin:"$PATH"

export crypto_dir=$PWD/crypto-config

export orderer_org_dir=${crypto_dir}/ordererOrganizations/example.com
export org1_dir=${crypto_dir}/peerOrganizations/org1.example.com
export org2_dir=${crypto_dir}/peerOrganizations/org2.example.com

export orderer1_dir=${orderer_org_dir}/orderers/orderer.example.com
export orderer2_dir=${orderer_org_dir}/orderers/orderer2.example.com
export orderer3_dir=${orderer_org_dir}/orderers/orderer3.example.com
export orderer4_dir=${orderer_org_dir}/orderers/orderer4.example.com
export orderer5_dir=${orderer_org_dir}/orderers/orderer5.example.com

export peer0org1_dir=${org1_dir}/peers/peer0.org1.example.com
export peer1org1_dir=${org1_dir}/peers/peer1.org1.example.com

export peer0org2_dir=${org2_dir}/peers/peer0.org2.example.com
export peer1org2_dir=${org2_dir}/peers/peer1.org2.example.com

export orderer_org_tls=${PWD}/data_ca/ordererca/ca/ca-cert.pem
export org1_tls=${PWD}/data_ca/org1ca/ca/ca-cert.pem
export org2_tls=${PWD}/data_ca/org2ca/ca/ca-cert.pem

# import utilies
. ca/ca_utils.sh

######################################################################################
#  Create admin certificates for the CAs
######################################################################################

# Enroll CA Admin for ordererca
createEnrollment "5052" "admin" "adminpw" "" "${orderer_org_dir}/ca" "${orderer_org_tls}"

# Enroll CA Admin for org1ca
createEnrollment "5053" "admin" "adminpw" "org1" "${org1_dir}/ca" "${org1_tls}"

# Enroll CA Admin for org2ca
createEnrollment "5054" "admin" "adminpw" "org2" "${org2_dir}/ca" "${org2_tls}"


######################################################################################
#  Create admin and user certificates for the Organizations
######################################################################################

# Enroll Admin certificate for the ordering service org
registerAndEnroll "5052" "osadmin" "osadminpw" "admin" "" "${orderer_org_dir}/users/Admin@example.com" "${orderer_org_dir}" "${orderer_org_tls}"

# Enroll Admin certificate for org1
registerAndEnroll "5053" "org1admin" "org1adminpw" "admin" "org1" "${org1_dir}/users/Admin@org1.example.com" "${org1_dir}" "${org1_tls}"

# Enroll User certificate for org1
registerAndEnroll "5053" "org1user1" "org1user1pw" "client" "org1" "${org1_dir}/users/User1@org1.example.com" "${org1_dir}" "${org1_tls}"

# Enroll Admin certificate for org2
registerAndEnroll "5054" "org2admin" "org2adminpw" "admin" "org2" "${org2_dir}/users/Admin@org2.example.com" "${org2_dir}" "${org2_tls}"

# Enroll User certificate for org1
registerAndEnroll "5054" "org2user1" "org2user1pw" "client" "org2" "${org2_dir}/users/User1@org2.example.com" "${org2_dir}" "${org2_tls}"

######################################################################################
#  Create the certificates for the Ordering Organization
######################################################################################

# Create enrollment and TLS certificates for orderer1
registerAndEnroll "5052" "orderer1" "orderer1pw" "orderer" "" "${orderer1_dir}" "${orderer_org_dir}" "${orderer_org_tls}"

# Create enrollment and TLS certificates for orderer2
registerAndEnroll "5052" "orderer2" "orderer2pw" "orderer" "" "${orderer2_dir}" "${orderer_org_dir}" "${orderer_org_tls}"

# Create enrollment and TLS certificates for orderer3
registerAndEnroll "5052" "orderer3" "orderer3pw" "orderer" "" "${orderer3_dir}" "${orderer_org_dir}" "${orderer_org_tls}"

# Create enrollment and TLS certificates for orderer4
registerAndEnroll "5052" "orderer4" "orderer4pw" "orderer" "" "${orderer4_dir}" "${orderer_org}" "${orderer_org_tls}"

# Create enrollment and TLS certificates for orderer5
registerAndEnroll "5052" "orderer5" "orderer5pw" "orderer" "" "${orderer5_dir}" "${orderer_org_dir}" "${orderer_org_tls}"


######################################################################################
#  Create the certificates for Org1
######################################################################################

# Create enrollment and TLS certificates for peer0org1
registerAndEnroll "5053" "org1peer0" "org1peer0pw" "peer" "org1" "${peer0org1_dir}" "${org1_dir}" "${org1_tls}"

# Create enrollment and TLS certificates for peer1org1
registerAndEnroll "5053" "org1peer1" "org1peer1pw" "peer" "org1" "${peer1org1_dir}" "${org1_dir}" "${org1_tls}"


######################################################################################
#  Create the certificates for Org2
######################################################################################

# Create enrollment and TLS certificates for peer0org2
registerAndEnroll "5054" "org2peer0" "org2peer0pw" "peer" "org2" "${peer0org2_dir}" "${org2_dir}" "${org2_tls}"

# Create enrollment and TLS certificates for peer1org2
registerAndEnroll "5054" "org2peer1" "org2peer1pw" "peer" "org2" "${peer1org2_dir}" "${org2_dir}" "${org2_tls}"


######################################################################################
#  Create the Membership Service Providers (MSPs)
######################################################################################

# Create the MSP for the Orderering Org
createMSP "ordererca" "" "${orderer_org_dir}"

# Create the MSP for Org1
createMSP "org1ca" "org1" "${org1_dir}"

# Create the MSP for Org2
createMSP "org2ca" "org2" "${org2_dir}"
