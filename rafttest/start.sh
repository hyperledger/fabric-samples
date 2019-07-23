#!/bin/bash
export CHANNEL_NAME=mychannel
export FABRIC_CFG_PATH=$PWD
export PATH=${PWD}/../bin:${PWD}:$PATH

cryptogen generate --config=./crypto-config.yaml

configtxgen -profile SampleMultiNodeEtcdRaft  -outputBlock ./channel-artifacts/genesis.block

configtxgen -profile TwoOrgsChannel -outputCreateChannelTx ./channel-artifacts/channel.tx -channelID $CHANNEL_NAME

configtxgen -profile TwoOrgsChannel -outputAnchorPeersUpdate ./channel-artifacts/Org1MSPanchors.tx -channelID $CHANNEL_NAME -asOrg Org1MSP

sleep 5
docker-compose -f docker-compose-org1.yaml up -d
sleep 20
docker exec -it cli sh -c "./scripts/script.sh"
