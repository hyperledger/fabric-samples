#!/usr/bin/env sh

set -eu

export PATH="${PWD}"/../../fabric/build/bin:"${PWD}"/../bin:"$PATH"
export FABRIC_CFG_PATH="${PWD}"/../config

ORDERER_CONSENSUS_TYPE="etcdraft"
if [ $# -gt 0 ]
then
    if [ "$1" != "BFT" ] && [ "$1" != "etcdraft" ]
    then
        echo "Unsupported input consensus type ${1}"
        exit 1
    fi
    if [ "$1" = "BFT" ]
    then
      ORDERER_CONSENSUS_TYPE="BFT"
    fi
fi

osnadmin channel join --channelID mychannel --config-block ./channel-artifacts/mychannel.block -o localhost:9443
osnadmin channel join --channelID mychannel --config-block ./channel-artifacts/mychannel.block -o localhost:9444
osnadmin channel join --channelID mychannel --config-block ./channel-artifacts/mychannel.block -o localhost:9445

if [ "$ORDERER_CONSENSUS_TYPE" = "BFT" ]; then
  osnadmin channel join --channelID mychannel --config-block ./channel-artifacts/mychannel.block -o localhost:9446
fi
