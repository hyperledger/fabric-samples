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


rm -rf application-go/wallet/
rm -rf application-go/keystore/