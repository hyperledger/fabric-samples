#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

# This script extends the Hyperledger Fabric test network by adding
# adding a third organization to the network
#

# prepending $PWD/../bin to PATH to ensure we are picking up the correct binaries
# this may be commented out to resolve installed version of tools if desired
export PATH=${PWD}/../../bin:${PWD}:$PATH
export FABRIC_CFG_PATH=${PWD}
export VERBOSE=false

# Print the usage message
function printHelp () {
  echo "Usage: "
  echo "  addOrg3.sh up|down|generate [-c <channel name>] [-t <timeout>] [-d <delay>] [-f <docker-compose-file>] [-s <dbtype>]"
  echo "  addOrg3.sh -h|--help (print this message)"
  echo "    <mode> - one of 'up', 'down', or 'generate'"
  echo "      - 'up' - add org3 to the sample network. You need to create a channel first."
  echo "      - 'down' - clear the network with docker-compose down"
  echo "      - 'generate' - generate required certificates and org definition"
  echo "    -c <channel name> - channel name to use (defaults to \"mychannel\")"
  echo "    -t <timeout> - CLI timeout duration in seconds (defaults to 10)"
  echo "    -d <delay> - delay duration in seconds (defaults to 3)"
  echo "    -f <docker-compose-file> - specify which docker-compose file use (defaults to docker-compose-cli.yaml)"
  echo "    -s <dbtype> - the database backend to use: goleveldb (default) or couchdb"
  echo "    -i <imagetag> - the tag to be used to launch the network (defaults to \"latest\")"
  echo "    -v - verbose mode"
  echo
  echo "Typically, one would first generate the required certificates and "
  echo "genesis block, then bring up the network. e.g.:"
  echo
  echo "	addOrg3.sh generate"
  echo "	addOrg3.sh up -c mychannel -s couchdb"
  echo "	addOrg3.sh up -l node"
  echo "	addOrg3.sh down -c mychannel"
  echo
  echo "Taking all defaults:"
  echo "	addOrg3.sh up"
  echo "	addOrg3.sh down"
}

# We use the cryptogen tool to generate the cryptographic material
# (x509 certs) for the new org.  After we run the tool, the certs will
# be put in the organizations folder with org1 and org2

# Generates Org3 certs using cryptogen tool
function generateOrg3 (){
  which cryptogen
  if [ "$?" -ne 0 ]; then
    echo "cryptogen tool not found. exiting"
    exit 1
  fi
  echo
  echo "###############################################################"
  echo "##### Generate Org3 certificates using cryptogen tool #########"
  echo "###############################################################"

   set -x
   cryptogen generate --config=org3-crypto.yaml --output="../organizations"
   res=$?
   set +x
   if [ $res -ne 0 ]; then
     echo "Failed to generate certificates..."
     exit 1
   fi
  echo
}

# Generate channel configuration transaction
function generateOrg3Definition() {
  which configtxgen
  if [ "$?" -ne 0 ]; then
    echo "configtxgen tool not found. exiting"
    exit 1
  fi
  echo "##########################################################"
  echo "#########  Generating Org3 config material ###############"
  echo "##########################################################"
   export FABRIC_CFG_PATH=$PWD
   set -x
   configtxgen -printOrg Org3MSP > ../organizations/peerOrganizations/org3.example.com/org3.json
   res=$?
   set +x
   if [ $res -ne 0 ]; then
     echo "Failed to generate Org3 config material..."
     exit 1
   fi
  echo
}



# Generate the needed certificates, the genesis block and start the network.
function networkUp () {
  # generate artifacts if they don't exist
  if [ ! -d "../organizations/peerOrganizations/org3.example.com" ]; then
    generateOrg3
    generateOrg3Definition
  fi
  # start org3 peers
  if [ "${DATABASE}" == "couchdb" ]; then
      IMAGE_TAG=${IMAGETAG} docker-compose -f $COMPOSE_FILE_ORG3 -f $COMPOSE_FILE_COUCH_ORG3 up -d 2>&1
  else
      IMAGE_TAG=$IMAGETAG docker-compose -f $COMPOSE_FILE_ORG3 up -d 2>&1
  fi
  if [ $? -ne 0 ]; then
    echo "ERROR !!!! Unable to start Org3 network"
    exit 1
  fi

  # Use the CLI container to create the configuration transaction needed to add
  # Org3 to the network
  echo
  echo "###############################################################"
  echo "####### Generate and submit config tx to add Org3 #############"
  echo "###############################################################"
  docker exec Org3cli ./scripts/org3-scripts/step1org3.sh $CHANNEL_NAME $CLI_DELAY $CLI_TIMEOUT $VERBOSE
  if [ $? -ne 0 ]; then
    echo "ERROR !!!! Unable to create config tx"
    exit 1
  fi

  echo
  echo "###############################################################"
  echo "############### Have Org3 peers join network ##################"
  echo "###############################################################"
  docker exec Org3cli ./scripts/org3-scripts/step2org3.sh $CHANNEL_NAME $CLI_DELAY $CLI_TIMEOUT $VERBOSE
  if [ $? -ne 0 ]; then
    echo "ERROR !!!! Unable to have Org3 peers join network"
    exit 1
  fi

}

# Tear down running network
function networkDown () {

    cd ..
    ./network.sh down

}


# If the test network is not up, abort
if [ ! -d ../organizations/peerOrganizations ]; then
  echo
  echo "ERROR: Please, run network.sh first."
  echo
  exit 1
fi

# Obtain the OS and Architecture string that will be used to select the correct
# native binaries for your platform
OS_ARCH=$(echo "$(uname -s|tr '[:upper:]' '[:lower:]'|sed 's/mingw64_nt.*/windows/')-$(uname -m | sed 's/x86_64/amd64/g')" | awk '{print tolower($0)}')
# timeout duration - the duration the CLI should wait for a response from
# another container before giving up
CLI_TIMEOUT=10
#default for delay
CLI_DELAY=3
# channel name defaults to "mychannel"
CHANNEL_NAME="mychannel"
# use this as the docker compose couch file
COMPOSE_FILE_COUCH_ORG3=docker/docker-compose-couch-org3.yaml
# use this as the default docker-compose yaml definition
COMPOSE_FILE_ORG3=docker/docker-compose-org3.yaml
# default image tag
IMAGETAG="latest"
# database
DATABASE="leveldb"

# Parse commandline args

MODE=$1;
shift

while getopts "h?c:t:d:f:s:l:i:v" opt; do
  case "$opt" in
    h|\?)
      printHelp
      exit 0
    ;;
    c)  CHANNEL_NAME=$OPTARG
    ;;
    t)  CLI_TIMEOUT=$OPTARG
    ;;
    d)  CLI_DELAY=$OPTARG
    ;;
    f)  COMPOSE_FILE=$OPTARG
    ;;
    s)  DATABASE=$OPTARG
    ;;
    i)  IMAGETAG=$OPTARG
    ;;
    v)  VERBOSE=true
    ;;
  esac
done

# Determine whether starting, stopping, restarting or generating for announce
if [ "$MODE" == "up" ]; then
  echo "Add Org3 to channel '${CHANNEL_NAME}' with '${CLI_TIMEOUT}' seconds and CLI delay of '${CLI_DELAY}' seconds and using database '${DATABASE}'"
  echo
elif [ "$MODE" == "down" ]; then
  EXPMODE="Stopping network"
elif [ "$MODE" == "generate" ]; then
  EXPMODE="Generating certs and organization definition for Org3"
else
  printHelp
  exit 1
fi

#Create the network using docker compose
if [ "${MODE}" == "up" ]; then
  networkUp
elif [ "${MODE}" == "down" ]; then ## Clear the network
  networkDown
elif [ "${MODE}" == "generate" ]; then ## Generate Artifacts
  generateOrg3
  generateOrg3Definition
else
  printHelp
  exit 1
fi
