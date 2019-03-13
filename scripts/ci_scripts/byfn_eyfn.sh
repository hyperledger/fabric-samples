#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

# docker container list
CONTAINER_LIST=(peer0.org1 peer1.org1 peer0.org2 peer1.org2 peer0.org3 peer1.org3 orderer)
COUCHDB_CONTAINER_LIST=(couchdb0 couchdb1 couchdb2 couchdb3 couchdb4 couchdb5)

cd $WORKSPACE/$BASE_DIR/first-network
# export path
export PATH=$WORKSPACE/$BASE_DIR/bin:$PATH

logs() {
  # Create Logs directory
  mkdir -p $WORKSPACE/Docker_Container_Logs
  for CONTAINER in ${CONTAINER_LIST[*]}; do
    docker logs $CONTAINER.example.com >& $WORKSPACE/Docker_Container_Logs/$CONTAINER-$1.log
    echo
  done
}

if [ ! -z $2 ]; then
  for CONTAINER in ${COUCHDB_CONTAINER_LIST[*]}; do
    docker logs $CONTAINER >& $WORKSPACE/Docker_Container_Logs/$CONTAINER-$1.log
    echo
  done
fi

copy_logs() {
  # Call logs function
  logs $2 $3
  if [ $1 != 0 ]; then
    echo -e "\033[31m $2 test case is FAILED" "\033[0m"
    exit 1
  fi
}

echo " ################ "
echo -e "\033[1m DEFAULT CHANNEL\033[0m"
echo " # ############## "
set -x
echo y | ./byfn.sh -m down
echo y | ./byfn.sh -m up -t 60
copy_logs $? default-channel
echo y | ./eyfn.sh -m up -t 60
copy_logs $? default-channel
echo y | ./eyfn.sh -m down
set +x
echo

echo " ############################ "
echo -e "\033[1mCUSTOM CHANNEL - COUCHDB\033[0m"
echo " # ########################## "
set -x
echo y | ./byfn.sh -m up -c custom-channel-couchdb -s couchdb -t 75 -d 15
copy_logs $? custom-channel-couch couchdb
echo y | ./eyfn.sh -m up -c custom-channel-couchdb -s couchdb -t 75 -d 15
copy_logs $? custom-channel-couch
echo y | ./eyfn.sh -m down
set +x
echo

echo " #################################### "
echo -e "\033[1m NODE CHAINCODE\033[0m"
echo " # ################################## "
set -x
echo y | ./byfn.sh -m up -l node -t 60
copy_logs $? default-channel-node
echo y | ./eyfn.sh -m up -l node -t 60
copy_logs $? default-channel-node
echo y | ./eyfn.sh -m down
set +x
