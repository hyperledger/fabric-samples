#!/bin/bash
#
# SPDX-License-Identifier: Apache-2.0
#
set -ex

function _exit(){
    printf "Exiting:%s\n" "$1"
    exit -1
}

# Where am I?
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"

## Use this to remove anything existing on the basic network before starting
#  docker kill $(docker network inspect net_basic --format '{{json .Containers}}' | jq -r 'keys[]') && docker rm $(docker ps -aq) 

## Start the Fabric Network
cd "${DIR}/basic-network"
. ./start.sh

docker ps

## Run as MagnetoCorp
# cd "${DIR}/commercial-paper/organization/magnetocorp/configuration/cli"
# docker-compose -f docker-compose.yml up -d cliMagnetoCorp

# docker exec cliMagnetoCorp peer lifecycle chaincode package cp.tar.gz --lang node --path /opt/gopath/src/github.com/hyperledger/fabric-samples/commercial-paper/organization/magnetocorp/contract --label cp_0
# docker exec cliMagnetoCorp peer lifecycle chaincode install cp.tar.gz
# export PACKAGE_ID=$(docker exec cliMagnetoCorp peer lifecycle chaincode queryinstalled 2>&1 | awk -F "[, ]+" '/Label: /{print $3}')

# docker exec cliMagnetoCorp peer lifecycle chaincode approveformyorg --channelID mychannel --name papercontract -v 0 --package-id $PACKAGE_ID --sequence 1 --signature-policy "AND ('Org1MSP.member')" 
# docker exec cliMagnetoCorp peer lifecycle chaincode commit -o orderer.example.com:7050 --channelID mychannel --name papercontract -v 0 --sequence 1 --waitForEvent --signature-policy "AND ('Org1MSP.member')" 
# docker exec cliMagnetoCorp peer chaincode invoke -o orderer.example.com:7050 --channelID mychannel --name papercontract -c '{"Args":["org.papernet.commercialpaper:instantiate"]}' --waitForEvent


cd "${DIR}/commercial-paper/organization/digibank/configuration/cli"
docker-compose -f docker-compose.yml up -d cliDigiBank
CLI_CONTAINER=cliDigiBank
docker exec ${CLI_CONTAINER} peer lifecycle chaincode package cp.tar.gz --lang java --path /opt/gopath/src/github.com/hyperledger/fabric-samples/commercial-paper/organization/magnetocorp/contract-java/build/libs --label cp_0
docker exec ${CLI_CONTAINER} peer lifecycle chaincode install cp.tar.gz
export PACKAGE_ID=$(docker exec ${CLI_CONTAINER} peer lifecycle chaincode queryinstalled 2>&1 | awk -F "[, ]+" '/Label: /{print $3}')

docker exec ${CLI_CONTAINER} peer lifecycle chaincode approveformyorg --channelID mychannel --name papercontract -v 0 --package-id $PACKAGE_ID --sequence 1 --signature-policy "AND ('Org1MSP.member')" 
docker exec ${CLI_CONTAINER} peer lifecycle chaincode commit -o orderer.example.com:7050 --channelID mychannel --name papercontract -v 0 --sequence 1 --waitForEvent --signature-policy "AND ('Org1MSP.member')" 
docker exec ${CLI_CONTAINER} peer chaincode invoke -o orderer.example.com:7050 --channelID mychannel --name papercontract -c '{"Args":["org.papernet.commercialpaper:instantiate"]}' --waitForEvent



cd "${DIR}/commercial-paper/organization/magnetocorp/application"
npm install
node addToWallet.js
node issue.js

cd "${DIR}/commercial-paper/organization/digibank/application"
npm install
node addToWallet.js
node buy.js
node redeem.js