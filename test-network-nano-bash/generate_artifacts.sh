#!/usr/bin/env sh
#
# SPDX-License-Identifier: Apache-2.0
#
set -eu

ordererType="etcdraft"
INCLUDE_CA=false

# parse flags
while [ $# -ge 1 ] ; do
  key="$1"
  case $key in
  etcdraft )
    ordererType="etcdraft"
    ;;
  BFT )
    ordererType="BFT"
    ;;
  -ca )
    INCLUDE_CA=true
    ;;
  * )
    ;;
  esac
  shift
done


# remove existing artifacts, or proceed on if the directories don't exist
rm -r "${PWD}"/channel-artifacts || true
rm -r "${PWD}"/crypto-config || true
rm -r "${PWD}"/data || true

# look for binaries in local dev environment /build/bin directory and then in local samples /bin directory
export PATH="${PWD}"/../../fabric/build/bin:"${PWD}"/../bin:"$PATH"

# if INCLUDE_CA is false (default), then use cryptogen
if [ "${INCLUDE_CA}" = false ]; then

  echo "Generating MSP certificates using cryptogen tool"
  cryptogen generate --config="${PWD}"/crypto-config.yaml

else

  mkdir -p "${PWD}"/logs

  # execute the script to configure the default set of enrollments
  echo "Generating MSP certificates using the Fabric CAs, see results in ./logs/createEnrollments.log"
  ./ca/createEnrollments.sh > ./logs/createEnrollments.log 2>&1

fi

# set FABRIC_CFG_PATH to configtx.yaml directory that contains the profiles
export FABRIC_CFG_PATH="${PWD}"

if [ "${ordererType}" = "BFT" ]
then
    profile="ChannelUsingBFT"
    ordererType="BFT"
    export FABRIC_CFG_PATH="${PWD}/bft-config"
else
    profile="ChannelUsingRaft"
fi

echo "Generating application channel genesis block with ${ordererType} consensus"
configtxgen -profile ${profile} -outputBlock ./channel-artifacts/mychannel.block -channelID mychannel


