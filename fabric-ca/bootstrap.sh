#!/bin/bash
#
# Copyright IBM Corp. All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0
#

# current version of fabric-ca released
export CA_VERSION=${1:-1.1.0}
export ARCH=$(echo "$(uname -s|tr '[:upper:]' '[:lower:]'|sed 's/mingw64_nt.*/windows/')-$(uname -m | sed 's/x86_64/amd64/g')" | awk '{print tolower($0)}')
#Set MARCH variable i.e ppc64le,s390x,x86_64,i386
MARCH=`uname -m`

dockerCaPull() {
      local CA_TAG=$1
      echo "==> FABRIC CA IMAGE"
      echo
      for image in "" "-tools" "-orderer" "-peer"; do
         docker pull hyperledger/fabric-ca${image}:$CA_TAG
         docker tag hyperledger/fabric-ca${image}:$CA_TAG hyperledger/fabric-ca${image}
      done
}

: ${CA_TAG:="$MARCH-$CA_VERSION"}

echo "===> Pulling fabric ca Image"
dockerCaPull ${CA_TAG}

echo "===> List out hyperledger docker images"
docker images | grep hyperledger*
