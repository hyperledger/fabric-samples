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

#
# Bind all org2 services to the "org2" namespace
#
export NAMESPACE=org2



#
# Download the chaincode package.  (Or prepare one here with pkgcc.sh, tar, etc.)
#
CHAINCODE_PACKAGE=organizations/org2/chaincode/$CHAINCODE_PKG_NAME
if [ ! -f "$CHAINCODE_PACKAGE" ]; then
  print "downloading k8s chaincode package $CHAINCODE_PKG_URL"
  mkdir -p $(dirname $CHAINCODE_PACKAGE)
  curl -L $CHAINCODE_PKG_URL > $CHAINCODE_PACKAGE
fi


#
# Install the package on all of the org peers
# todo: find a reliable way to test if the chaincode PACKAGE_ID has been installed (queryinstalled, getinstalled, ...)
#

# org2-peer1
appear_as Org2MSP org2 peer1
export PACKAGE_ID=$(peer lifecycle chaincode calculatepackageid $CHAINCODE_PACKAGE)

print "installing $CHAINCODE_PKG_URL to $CORE_PEER_ADDRESS"
echo $PACKAGE_ID
peer lifecycle chaincode install $CHAINCODE_PACKAGE || true

# org2-peer2
appear_as Org2MSP org2 peer2
export PACKAGE_ID=$(peer lifecycle chaincode calculatepackageid $CHAINCODE_PACKAGE)

print "installing $CHAINCODE_PKG_URL to $CORE_PEER_ADDRESS"
echo $PACKAGE_ID
peer lifecycle chaincode install $CHAINCODE_PACKAGE || true


#
# Approve the chaincode for the org
#
print "approving $CHAINCODE_NAME for $org"
peer lifecycle \
  chaincode       approveformyorg \
  --channelID     ${CHANNEL_NAME} \
  --name          ${CHAINCODE_NAME} \
  --version       ${CHAINCODE_VERSION} \
  --sequence      ${CHAINCODE_SEQUENCE} \
  --package-id    ${PACKAGE_ID} \
  --orderer       ${ORDERER_ENDPOINT} \
  --tls --cafile  ${ORDERER_TLS_CERT} \
  --connTimeout   15s

#
# Commit the chaincode to the channel
#
print "committing $CHAINCODE_NAME to $CHANNEL_NAME"
peer lifecycle \
  chaincode       commit \
  --channelID     ${CHANNEL_NAME} \
  --name          ${CHAINCODE_NAME} \
  --version       ${CHAINCODE_VERSION} \
  --sequence      ${CHAINCODE_SEQUENCE} \
  --orderer       ${ORDERER_ENDPOINT} \
  --tls --cafile  ${ORDERER_TLS_CERT} \
  --connTimeout   15s