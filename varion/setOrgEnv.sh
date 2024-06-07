#!/bin/bash
#
# SPDX-License-Identifier: Apache-2.0




# default to using Org1
ORG=${farmer:-Org1}

# Exit on first error, print all commands.
set -e
set -o pipefail

# Where am I?
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"

ORDERER_CA=${DIR}/test-network/organizations/ordererOrganizations/varion.com/tlsca/tlsca.varion.com-cert.pem
PEER0_FARMER_CA=${DIR}/test-network/organizations/peerOrganizations/farmer.varion.com/tlsca/tlsca.farmer.varion.com-cert.pem
PEER0_PULPER_CA=${DIR}/test-network/organizations/peerOrganizations/pulper.varion.com/tlsca/tlsca.pulper.varion.com-cert.pem
PEER0_HULLER_CA=${DIR}/test-network/organizations/peerOrganizations/huller.varion.com/tlsca/tlsca.huller.varion.com-cert.pem
PEER0_EXPORT_CA=${DIR}/test-network/organizations/peerOrganizations/export.varion.com/tlsca/tlsca.export.varion.com-cert.pem


if [[ ${ORG,,} == "farmer" ]]; then

   CORE_PEER_LOCALMSPID=FarmerMSP
   CORE_PEER_MSPCONFIGPATH=${DIR}/test-network/organizations/peerOrganizations/farmer.varion.com/users/Admin@farmer.varion.com/msp
   CORE_PEER_ADDRESS=localhost:7051
   CORE_PEER_TLS_ROOTCERT_FILE=${DIR}/test-network/organizations/peerOrganizations/farmer.varion.com/tlsca/tlsca.farmer.varion.com-cert.pem

elif [[ ${ORG,,} == "pulper" ]]; then

   CORE_PEER_LOCALMSPID=PulperMSP
   CORE_PEER_MSPCONFIGPATH=${DIR}/test-network/organizations/peerOrganizations/pulper.varion.com/users/Admin@pulper.varion.com/msp
   CORE_PEER_ADDRESS=localhost:9051
   CORE_PEER_TLS_ROOTCERT_FILE=${DIR}/test-network/organizations/peerOrganizations/pulper.varion.com/tlsca/tlsca.pulper.varion.com-cert.pem

elif [[ ${ORG,,} == "huller" ]]; then

   CORE_PEER_LOCALMSPID=HullerMSP
   CORE_PEER_MSPCONFIGPATH=${DIR}/test-network/organizations/peerOrganizations/huller.varion.com/users/Admin@huller.varion.com/msp
   CORE_PEER_ADDRESS=localhost:9051
   CORE_PEER_TLS_ROOTCERT_FILE=${DIR}/test-network/organizations/peerOrganizations/huller.varion.com/tlsca/tlsca.huller.varion.com-cert.pem

elif [[ ${ORG,,} == "export" ]]; then

   CORE_PEER_LOCALMSPID=ExportMSP
   CORE_PEER_MSPCONFIGPATH=${DIR}/test-network/organizations/peerOrganizations/export.varion.com/users/Admin@export.varion.com/msp
   CORE_PEER_ADDRESS=localhost:9051
   CORE_PEER_TLS_ROOTCERT_FILE=${DIR}/test-network/organizations/peerOrganizations/export.varion.com/tlsca/tlsca.export.varion.com-cert.pem

else
   echo "Unknown \"$ORG\", please choose Farmer, Pulper, Huller, or Export"
   echo "For varion to get the environment variables to set upa Pulper shell environment run:  ./setOrgEnv.sh Pulper"
   echo
   echo "This can be automated to set them as well with:"
   echo
   echo 'export $(./setOrgEnv.sh Pulper | xargs)'
   exit 1
fi

# output the variables that need to be set
echo "CORE_PEER_TLS_ENABLED=true"
echo "ORDERER_CA=${ORDERER_CA}"
echo "PEER0_FARMER_CA=${PEER0_FARMER_CA}"
echo "PEER0_PULPER_CA=${PEER0_PULPER_CA}"
echo "PEER0_HULLER_CA=${PEER0_HULLER_CA}"
echo "PEER0_EXPORT_CA=${PEER0_EXPORT_CA}"

echo "CORE_PEER_MSPCONFIGPATH=${CORE_PEER_MSPCONFIGPATH}"
echo "CORE_PEER_ADDRESS=${CORE_PEER_ADDRESS}"
echo "CORE_PEER_TLS_ROOTCERT_FILE=${CORE_PEER_TLS_ROOTCERT_FILE}"

echo "CORE_PEER_LOCALMSPID=${CORE_PEER_LOCALMSPID}"
