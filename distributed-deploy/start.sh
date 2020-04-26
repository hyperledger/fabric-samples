#!/bin/bash
#
# Copyright CGB Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

# Print the usage message
function printHelp() {
  echo "Usage: "
  echo "  start.sh <mode> [-c <channel name>] [-t <timeout>] [-d <delay>] [-f <docker-compose-file>] [-s <dbtype>] [-l <language>] [-o <consensus-type>] [-i <imagetag>] [-a] [-n] [-v]"
  echo "    <mode> - one of 'up', 'down', 'restart', 'generate' or 'upgrade'"
  echo "      - 'up' - bring up the network with docker-compose up"
  echo "      - 'down' - clear the network with docker-compose down"
  echo "      - 'restart' - restart the network"
  echo "      - 'generate' - generate required certificates and genesis block"
  echo "      - 'upgrade'  - upgrade the network from version 1.3.x to 1.4.0"
  echo "    -c <channel name> - channel name to use (defaults to \"mychannel\")"
  echo "    -t <timeout> - CLI timeout duration in seconds (defaults to 10)"
  echo "    -d <delay> - delay duration in seconds (defaults to 3)"
  echo "    -f <docker-compose-file> - specify which docker-compose file use (defaults to docker-compose-cli.yaml)"
  echo "    -s <dbtype> - the database backend to use: goleveldb (default) or couchdb"
  echo "    -l <language> - the chaincode language: golang (default) or node"
  echo "    -o <consensus-type> - the consensus-type of the ordering service: solo (default), kafka, or etcdraft"
  echo "    -p <path> - the data path"
  echo "    -i <imagetag> - the tag to be used to launch the network (defaults to \"latest\")"
  echo "    -a - launch certificate authorities (no certificate authorities are launched by default)"
  echo "    -n - do not deploy chaincode (abstore chaincode is deployed by default)"
  echo "    -v - verbose mode"
  echo "  start.sh -h (print this message)"
  echo
  echo "Typically, one would first generate the required certificates and "
  echo "genesis block, then bring up the network. e.g.:"
  echo
  echo "	start.sh generate -c mychannel"
  echo "	start.sh up -c mychannel -s couchdb"
  echo "        start.sh up -c mychannel -s couchdb -i 1.4.0"
  echo "	start.sh up -l node"
  echo "	start.sh down -c mychannel"
  echo "        start.sh upgrade -c mychannel"
  echo
  echo "Taking all defaults:"
  echo "	start.sh up"
  echo "	start.sh down"
}

function mvnPackage() {
  mvn clean
  mvn package -DskipTests

  if [ $? -ne 0 ]; then
    echo "Failed of mvn package..."
    exit 1
  fi
}

# Obtain CONTAINER_IDS and remove them
# TODO Might want to make this optional - could clear other containers
function clearContainers() {
  CONTAINER_IDS=$(docker ps -a | awk '($2 ~ /dev-peer.*/) {print $1}')
  if [ -z "$CONTAINER_IDS" -o "$CONTAINER_IDS" == " " ]; then
    echo "---- No containers available for deletion ----"
  else
    docker rm -f $CONTAINER_IDS
  fi
}

# Delete any images that were generated as a part of this setup
# specifically the following images are often left behind:
# TODO list generated image naming patterns
function removeUnwantedImages() {
  DOCKER_IMAGE_IDS=$(docker images | awk '($1 ~ /dev-peer.*/) {print $3}')
  if [ -z "$DOCKER_IMAGE_IDS" -o "$DOCKER_IMAGE_IDS" == " " ]; then
    echo "---- No images available for deletion ----"
  else
    docker rmi -f $DOCKER_IMAGE_IDS
  fi
}

# Generate the needed certificates, the genesis block and start the network.
function networkUp() {
  # checkPrereqs
  mvnPackage

  # Copy the file to the specified directory
  cp bcp-install-main/target/bcp-install.jar bcp-install-main/resources/generateInstallPackage/masterPackage/

  # create directory if it don't exist
  if [ ! -d $DATA_PATH ]; then
    mkdir $DATA_PATH
  fi

  BASE_PATH=$(pwd)

  cd $MASTER_DIR
  sudo ./start-installService-master.sh -m newInstall -p $BASE_PATH/$DATA_PATH
}

# Tear down running network
function networkDown() {
  docker stop $(docker ps -aq)
  docker rm $(docker ps -aq)

  docker volume prune
  docker network prune

  # Don't remove the generated artifacts -- note, the ledgers are always removed
  if [ "$MODE" != "restart" ]; then
    # Bring down the network, deleting the volumes
    #Delete any ledger backups
    # docker run -v $PWD:/tmp/first-network --rm hyperledger/fabric-tools:$IMAGETAG rm -Rf /tmp/first-network/ledgers-backup
    #Cleanup the chaincode containers
    clearContainers
    #Cleanup images
    removeUnwantedImages
  fi

  # clear java env
  mvn clean
}

BASE_DIR=$(pwd)
MASTER_DIR=bcp-install-main/resources/generateInstallPackage/masterPackage

# data storage path
DATA_PATH=./mainData

MODE=$1
shift

while getopts "h?c:t:d:f:s:l:i:o:p:anv" opt; do
  case "$opt" in
  h | \?)
    printHelp
    exit 0
    ;;
  c)
    CHANNEL_NAME=$OPTARG
    ;;
  t)
    CLI_TIMEOUT=$OPTARG
    ;;
  d)
    CLI_DELAY=$OPTARG
    ;;
  f)
    COMPOSE_FILE=$OPTARG
    ;;
  s)
    IF_COUCHDB=$OPTARG
    ;;
  l)
    LANGUAGE=$OPTARG
    ;;
  i)
    IMAGETAG=$(go env GOARCH)"-"$OPTARG
    ;;
  o)
    CONSENSUS_TYPE=$OPTARG
    ;;
  p)
    DATA_PATH=$OPTARG
    ;;
  a)
    CERTIFICATE_AUTHORITIES=true
    ;;
  n)
    NO_CHAINCODE=true
    ;;
  v)
    VERBOSE=true
    ;;
  esac
done

# Determine whether starting, stopping, restarting, generating or upgrading
if [ "$MODE" == "up" ]; then
  networkUp
elif [ "$MODE" == "down" ]; then
  networkDown
elif [ "$MODE" == "restart" ]; then
  networkDown
  networkUp
else
  printHelp
  exit 1
fi

