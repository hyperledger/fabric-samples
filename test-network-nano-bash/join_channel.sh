#!/usr/bin/env sh
#
# SPDX-License-Identifier: Apache-2.0
#
set -eu

# join peer to channel
peer channel join -b "${PWD}"/channel-artifacts/mychannel.block
