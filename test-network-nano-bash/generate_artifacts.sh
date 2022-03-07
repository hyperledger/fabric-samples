#!/usr/bin/env sh
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

echo "Generating orderer genesis block"
configtxgen -profile TwoOrgsOrdererGenesis -channelID test-system-channel-name -outputBlock channel-artifacts/genesis.block

echo "Generating channel create config transaction"
configtxgen -channelID mychannel -outputCreateChannelTx channel-artifacts/mychannel.tx -profile TwoOrgsChannel

echo "Generating anchor peer update transaction for Org1"
configtxgen -profile TwoOrgsChannel -outputAnchorPeersUpdate channel-artifacts/Org1MSPanchors.tx -channelID mychannel -asOrg Org1MSP

echo "Generating anchor peer update transaction for Org2"
configtxgen -profile TwoOrgsChannel -outputAnchorPeersUpdate channel-artifacts/Org2MSPanchors.tx -channelID mychannel -asOrg Org2MSP
