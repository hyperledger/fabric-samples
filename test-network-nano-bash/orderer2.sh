#!/usr/bin/env sh
#
# SPDX-License-Identifier: Apache-2.0
#
set -eu

# look for binaries in local dev environment /build/bin directory and then in local samples /bin directory
export PATH="${PWD}"/../../fabric/build/bin:"${PWD}"/../bin:"$PATH"
export FABRIC_CFG_PATH="${PWD}"/../config

export FABRIC_LOGGING_SPEC=debug:cauthdsl,policies,msp,common.configtx,common.channelconfig=info
export ORDERER_GENERAL_LISTENPORT=6051
export ORDERER_GENERAL_LOCALMSPID=OrdererMSP
export ORDERER_GENERAL_LOCALMSPDIR="${PWD}"/crypto-config/ordererOrganizations/example.com/orderers/orderer2.example.com/msp
export ORDERER_GENERAL_TLS_ENABLED=true
export ORDERER_GENERAL_TLS_PRIVATEKEY="${PWD}"/crypto-config/ordererOrganizations/example.com/orderers/orderer2.example.com/tls/server.key
export ORDERER_GENERAL_TLS_CERTIFICATE="${PWD}"/crypto-config/ordererOrganizations/example.com/orderers/orderer2.example.com/tls/server.crt
# following setting is not really needed at runtime since channel config has ca root certs, but we need to override the default in orderer.yaml
export ORDERER_GENERAL_TLS_ROOTCAS="${PWD}"/crypto-config/ordererOrganizations/example.com/orderers/orderer2.example.com/tls/ca.crt
export ORDERER_GENERAL_BOOTSTRAPMETHOD=none
export ORDERER_CHANNELPARTICIPATION_ENABLED=true
export ORDERER_FILELEDGER_LOCATION="${PWD}"/data/orderer2
if [ $# -gt 0 ]
then
    if [ "$1" != "BFT" ] && [ "$1" != "etcdraft" ]
    then
        echo "Unsupported input consensus type ${1}"
        exit 1
    fi
    export ORDERER_CONSENSUS_TYPE=${1}
fi
export ORDERER_CONSENSUS_WALDIR="${PWD}"/data/orderer2/consensus/wal
export ORDERER_CONSENSUS_SNAPDIR="${PWD}"/data/orderer2/consensus/snap
export ORDERER_OPERATIONS_LISTENADDRESS=127.0.0.1:8444
export ORDERER_ADMIN_LISTENADDRESS=127.0.0.1:9444

# start orderer
orderer
