#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#
# Exit on first error
set -ex

# Bring the test network down
pushd ../test-network
./network.sh down
popd

# clean out any old identites in the wallets
rm -rf wallet
rm -rf addAssets.json mychannel_basic.log mychannel__lifecycle.log nextblock.txt

docker stop offchaindb
docker rm offchaindb
