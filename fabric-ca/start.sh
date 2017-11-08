#!/bin/bash
#
# Copyright IBM Corp. All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0
#

#
# This script does everything required to run the fabric CA sample.
#

set -e

SDIR=$(dirname "$0")
source ${SDIR}/scripts/env.sh

cd ${SDIR}

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
   docker rmi -f $chaincodeImages > /dev/null
fi

# Start with a clean data directory
DDIR=${SDIR}/${DATA}
if [ -d ${DDIR} ]; then
   log "Cleaning up the data directory from previous run at $DDIR"
   rm -rf ${SDIR}/data
fi
mkdir -p ${DDIR}/logs

# Create the docker-compose file
${SDIR}/makeDocker.sh

# Create the docker containers
log "Creating docker containers ..."
docker-compose up -d

# Wait for the setup container to complete
dowait "the 'setup' container to finish registering identities, creating the genesis block and other artifacts" 90 $SDIR/$SETUP_LOGFILE $SDIR/$SETUP_SUCCESS_FILE

# Wait for the run container to start and then tails it's summary log
dowait "the docker 'run' container to start" 60 ${SDIR}/${SETUP_LOGFILE} ${SDIR}/${RUN_SUMFILE}
tail -f ${SDIR}/${RUN_SUMFILE}&
TAIL_PID=$!

# Wait for the run container to complete
while true; do 
   if [ -f ${SDIR}/${RUN_SUCCESS_FILE} ]; then
      kill -9 $TAIL_PID
      exit 0
   elif [ -f ${SDIR}/${RUN_FAIL_FILE} ]; then
      kill -9 $TAIL_PID
      exit 1
   else
      sleep 1
   fi
done
