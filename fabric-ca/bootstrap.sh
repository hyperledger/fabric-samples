#!/bin/bash
#
# Copyright IBM Corp. All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0
#

# current version of fabric-ca released
export CA_TAG=${1:-1.2.0}

dockerCaPull() {
      echo "==> FABRIC CA IMAGE"
      echo
      for image in "" "-tools" "-orderer" "-peer"; do
         docker pull hyperledger/fabric-ca${image}:$CA_TAG
         docker tag hyperledger/fabric-ca${image}:$CA_TAG hyperledger/fabric-ca${image}
      done
}

echo "===> Pulling fabric ca Image"
dockerCaPull ${CA_TAG}

echo "===> List out hyperledger docker images"
docker images | grep hyperledger/fabric-ca
