#!/usr/bin/env sh
#
# SPDX-License-Identifier: Apache-2.0
#
set -eu

# remove existing artifacts, or proceed on if the directories don't exist
rm -r "${PWD}"/channel-artifacts || true
rm -r "${PWD}"/crypto-config || true
rm -r "${PWD}"/data || true

# look for binaries in local dev environment /build/bin directory and then in local samples /bin directory
export PATH="${PWD}"/../../fabric/build/bin:"${PWD}"/../bin:"$PATH"

echo "Generating MSP certificates using cryptogen tool"
cryptogen generate --config="${PWD}"/crypto-config.yaml

# set FABRIC_CFG_PATH to configtx.yaml directory that contains the profiles
export FABRIC_CFG_PATH="${PWD}"

ordererType="etcdraft"
if [ $# -gt 0 ] && [ "$1" = "BFT" ]
then
    profile="ChannelUsingBFT"
    ordererType="BFT"
    export FABRIC_CFG_PATH="${PWD}/bft-config"
else
    profile="ChannelUsingRaft"
fi

echo "Generating application channel genesis block with ${ordererType} consensus"
configtxgen -profile ${profile} -outputBlock ./channel-artifacts/mychannel.block -channelID mychannel


