#!/bin/bash
#
# Copyright IBM Corp. All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0
#

#
# This script builds the images required to run this sample.
#

function assertOnMasterBranch {
   if [ "`git rev-parse --abbrev-ref HEAD`" != "master" ]; then
      fatal "You must switch to the master branch in `pwd`"
   fi
}

set -e

SDIR=$(dirname "$0")
source $SDIR/scripts/env.sh

# Delete docker containers
dockerContainers=$(docker ps -a | awk '$2~/hyperledger/ {print $1}')
if [ "$dockerContainers" != "" ]; then
   log "Deleting existing docker containers ..."
   docker rm -f $dockerContainers > /dev/null
fi

# Remove chaincode docker images
chaincodeImages=`docker images | grep "^dev-peer" | awk '{print $3}'`
if [ "$chaincodeImages" != "" ]; then
   log "Removing chaincode docker images ..."
   docker rmi $chaincodeImages > /dev/null
fi

# Perform docker clean for fabric-ca
log "Cleaning fabric-ca docker images ..."
cd $GOPATH/src/github.com/hyperledger/fabric-ca
assertOnMasterBranch
make docker-clean

# Perform docker clean for fabric and rebuild
log "Cleaning and rebuilding fabric docker images ..."
cd $GOPATH/src/github.com/hyperledger/fabric
assertOnMasterBranch
make docker-clean docker

# Perform docker clean for fabric and rebuild against latest fabric images just built
log "Rebuilding fabric-ca docker images ..."
cd $GOPATH/src/github.com/hyperledger/fabric-ca
FABRIC_TAG=latest make docker

log "Setup completed successfully.  You may run the tests multiple times by running start.sh."
