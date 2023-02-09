#!/usr/bin/env bash
#
# Copyright contributors to the Hyperledgendary Kubernetes Test Network project
#
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at:
#
# 	  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
set -euo pipefail
. scripts/utils.sh

GENESIS_BLOCK=${CHANNEL_NAME}_genesis_block.pb
CHANNEL_CONFIG=channel-config/config/configtx-multi-namespace.yaml

print "Creating channel-config/$GENESIS_BLOCK from $CHANNEL_CONFIG"

#
# The working directories and environment for configtxgen are confusing.
#
# Run configtxgen from the channel-config folder.  This instructs the
# routine to read configtxgen.yaml from the local configuration, not the
# default config created when the Fabric binaries were downloaded.
#
# In configtx.yaml, path references will be relative to the config folder,
# not the current working directory.
#
cd channel-config
export FABRIC_CFG_PATH=$PWD/config

configtxgen \
  -profile      TwoOrgsApplicationGenesis \
  -channelID    $CHANNEL_NAME \
  -outputBlock  $GENESIS_BLOCK


#configtxgen -inspectBlock $GENESIS_BLOCK | tee ${CHANNEL_NAME}_genesis_block.json | jq
