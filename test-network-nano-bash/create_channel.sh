#!/usr/bin/env sh
#
# SPDX-License-Identifier: Apache-2.0
#
set -eu

# create channel and add anchor peer
peer channel create -c mychannel -o 127.0.0.1:6050 -f "${PWD}"/channel-artifacts/mychannel.tx --outputBlock "${PWD}"/channel-artifacts/mychannel.block  --tls --cafile "${PWD}"/crypto-config/ordererOrganizations/example.com/orderers/orderer.example.com/tls/ca.crt
peer channel update -o 127.0.0.1:6050 -c mychannel -f "${PWD}"/channel-artifacts/Org1MSPanchors.tx --tls --cafile "${PWD}"/crypto-config/ordererOrganizations/example.com/orderers/orderer.example.com/tls/ca.crt

# join peer to channel
peer channel join -b "${PWD}"/channel-artifacts/mychannel.block
