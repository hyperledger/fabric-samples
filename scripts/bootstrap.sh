#!/bin/bash
#
# Copyright IBM Corp. All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0
#

# if version not passed in, default to latest released version
export VERSION=1.4.0
# if ca version not passed in, default to latest released version
export CA_VERSION=$VERSION
# current version of thirdparty images (couchdb, kafka and zookeeper) released
export THIRDPARTY_IMAGE_VERSION=0.4.14
export ARCH=$(echo "$(uname -s|tr '[:upper:]' '[:lower:]'|sed 's/mingw64_nt.*/windows/')-$(uname -m | sed 's/x86_64/amd64/g')")
export MARCH=$(uname -m)

# ensure we're in the fabric-samples directory
dir=`basename $PWD`
if [ "${dir}" == "scripts" ]; then
        cd ..
fi

dir=`basename $PWD`
if [ "${dir}" != "fabric-samples" ]; then
	echo "You should run this script from the fabric-samples root directory."
	exit 1
fi

printHelp() {
  echo "Usage: bootstrap.sh [<version>] [<ca_version>] [<thirdparty_version>][-d -b]"
  echo
  echo "-d - bypass docker image download"
  echo "-b - bypass download of platform-specific binaries"
  echo
  echo "e.g. bootstrap.sh 1.4.0 1.4.0 0.4.14"
  echo "would download docker images and binaries for version 1.4.0 (fabric) 1.4.0 (fabric-ca) 0.4.14 (thirdparty)"
}

dockerFabricPull() {
  local FABRIC_TAG=$1
  for IMAGES in peer orderer ccenv tools; do
      echo "==> FABRIC IMAGE: $IMAGES"
      echo
      docker pull hyperledger/fabric-$IMAGES:$FABRIC_TAG
      docker tag hyperledger/fabric-$IMAGES:$FABRIC_TAG hyperledger/fabric-$IMAGES
  done
}

dockerThirdPartyImagesPull() {
  local THIRDPARTY_TAG=$1
  for IMAGES in couchdb kafka zookeeper; do
      echo "==> THIRDPARTY DOCKER IMAGE: $IMAGES"
      echo
      docker pull hyperledger/fabric-$IMAGES:$THIRDPARTY_TAG
      docker tag hyperledger/fabric-$IMAGES:$THIRDPARTY_TAG hyperledger/fabric-$IMAGES
  done
}

dockerCaPull() {
      local CA_TAG=$1
      echo "==> FABRIC CA IMAGE"
      echo
      docker pull hyperledger/fabric-ca:$CA_TAG
      docker tag hyperledger/fabric-ca:$CA_TAG hyperledger/fabric-ca
}

# Incrementally downloads the .tar.gz file locally first, only decompressing it
# after the download is complete. This is slower than binaryDownload() but
# allows the download to be resumed.
binaryIncrementalDownload() {
      local BINARY_FILE=$1
      local URL=$2
      curl -f -s -C - ${URL} -o ${BINARY_FILE} || rc=$?
      # Due to limitations in the current Nexus repo:
      # curl returns 33 when there's a resume attempt with no more bytes to download
      # curl returns 2 after finishing a resumed download
      # with -f curl returns 22 on a 404
      if [ "$rc" = 22 ]; then
	  # looks like the requested file doesn't actually exist so stop here
	  return 22
      fi
      if [ -z "$rc" ] || [ $rc -eq 33 ] || [ $rc -eq 2 ]; then
          # The checksum validates that RC 33 or 2 are not real failures
          echo "==> File downloaded. Verifying the md5sum..."
          localMd5sum=$(md5sum ${BINARY_FILE} | awk '{print $1}')
          remoteMd5sum=$(curl -s ${URL}.md5)
          if [ "$localMd5sum" == "$remoteMd5sum" ]; then
              echo "==> Extracting ${BINARY_FILE}..."
              tar xzf ./${BINARY_FILE} --overwrite
	      echo "==> Done."
              rm -f ${BINARY_FILE} ${BINARY_FILE}.md5
          else
              echo "Download failed: the local md5sum is different from the remote md5sum. Please try again."
              rm -f ${BINARY_FILE} ${BINARY_FILE}.md5
              exit 1
          fi
      else
          echo "Failure downloading binaries (curl RC=$rc). Please try again and the download will resume from where it stopped."
          exit 1
      fi
}

# This will attempt to download the .tar.gz all at once, but will trigger the
# binaryIncrementalDownload() function upon a failure, allowing for resume
# if there are network failures.
binaryDownload() {
      local BINARY_FILE=$1
      local URL=$2
      echo "===> Downloading: " ${URL}
      # Check if a previous failure occurred and the file was partially downloaded
      if [ -e ${BINARY_FILE} ]; then
          echo "==> Partial binary file found. Resuming download..."
          binaryIncrementalDownload ${BINARY_FILE} ${URL}
      else
          curl ${URL} | tar xz || rc=$?
          if [ ! -z "$rc" ]; then
              echo "==> There was an error downloading the binary file. Switching to incremental download."
              echo "==> Downloading file..."
              binaryIncrementalDownload ${BINARY_FILE} ${URL}
	  else
	      echo "==> Done."
          fi
      fi
}

binariesInstall() {
  echo "===> Downloading version ${FABRIC_TAG} platform specific fabric binaries"
  binaryDownload ${BINARY_FILE} https://nexus.hyperledger.org/content/repositories/releases/org/hyperledger/fabric/hyperledger-fabric/${ARCH}-${VERSION}/${BINARY_FILE}
  if [ $? -eq 22 ]; then
     echo
     echo "------> ${FABRIC_TAG} platform specific fabric binary is not available to download <----"
     echo
   fi

  echo "===> Downloading version ${CA_TAG} platform specific fabric-ca-client binary"
  binaryDownload ${CA_BINARY_FILE} https://nexus.hyperledger.org/content/repositories/releases/org/hyperledger/fabric-ca/hyperledger-fabric-ca/${ARCH}-${CA_VERSION}/${CA_BINARY_FILE}
  if [ $? -eq 22 ]; then
     echo
     echo "------> ${CA_TAG} fabric-ca-client binary is not available to download  (Available from 1.1.0-rc1) <----"
     echo
   fi
}

dockerInstall() {
  which docker >& /dev/null
  NODOCKER=$?
  if [ "${NODOCKER}" == 0 ]; then
	  echo "===> Pulling fabric Images"
	  dockerFabricPull ${FABRIC_TAG}
	  echo "===> Pulling fabric ca Image"
	  dockerCaPull ${CA_TAG}
	  echo "===> Pulling thirdparty docker images"
	  dockerThirdPartyImagesPull ${THIRDPARTY_TAG}
	  echo
	  echo "===> List out hyperledger docker images"
	  docker images | grep hyperledger*
  else
    echo "========================================================="
    echo "Docker not installed, bypassing download of Fabric images"
    echo "========================================================="
  fi
}

DOCKER=true
SAMPLES=true
BINARIES=true

# Parse commandline args pull out
# version and/or ca-version strings first
if echo $1 | grep -q '\d'; then
  VERSION=$1;shift
  if echo $1 | grep -q '\d'; then
    CA_VERSION=$1;shift
    if echo $1 | grep -q '\d'; then
      THIRDPARTY_IMAGE_VERSION=$1;shift
    fi
  fi
fi

# prior to 1.1.0 architecture was determined by uname -m
if [[ $VERSION =~ ^1\.[0]\.* ]]; then
  export FABRIC_TAG=${MARCH}-${VERSION}
  export CA_TAG=${MARCH}-${CA_VERSION}
  export THIRDPARTY_TAG=${MARCH}-${THIRDPARTY_IMAGE_VERSION}
else
  # starting with 1.2.0, multi-arch images will be default
  : ${CA_TAG:="$CA_VERSION"}
  : ${FABRIC_TAG:="$VERSION"}
  : ${THIRDPARTY_TAG:="$THIRDPARTY_IMAGE_VERSION"}
fi

BINARY_FILE=hyperledger-fabric-${ARCH}-${VERSION}.tar.gz
CA_BINARY_FILE=hyperledger-fabric-ca-${ARCH}-${CA_VERSION}.tar.gz

# then parse opts
while getopts "h?db" opt; do
  case "$opt" in
    h|\?)
      printHelp
      exit 0
    ;;
    d)  DOCKER=false
    ;;
    b)  BINARIES=false
    ;;
  esac
done

if [ "$BINARIES" == "true" ]; then
  echo
  echo "Installing Hyperledger Fabric binaries"
  echo
  binariesInstall
fi
if [ "$DOCKER" == "true" ]; then
  echo
  echo "Installing Hyperledger Fabric docker images"
  echo
  dockerInstall
fi
