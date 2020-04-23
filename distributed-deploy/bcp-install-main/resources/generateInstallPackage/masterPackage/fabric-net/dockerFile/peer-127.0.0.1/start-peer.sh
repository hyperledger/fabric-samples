#!/bin/bash

export PATH=${PWD}/../bin:${PWD}:$PATH
export FABRIC_CFG_PATH=${PWD}
export VERBOSE=false

# if version not passed in, default to latest released version
export VERSION=1.4.2
# current version of thirdparty images (couchdb, kafka and zookeeper) released
export THIRDPARTY_IMAGE_VERSION=0.4.15

# 两个 tag 变量值
: ${FABRIC_TAG:="$VERSION"}
: ${THIRDPARTY_TAG:="$THIRDPARTY_IMAGE_VERSION"}

# default image tag
IMAGETAG=${VERSION}

# 检查是启动 orderer 或 peer
THIS_FILE_PATH=$0
FILE_NAME=${THIS_FILE_PATH##*-}
PEER_TYPE=${FILE_NAME%.*}

NEED_DOWNLOAD_IMAGES=(peer tools orderer)

# 设置 go 相关环境
GOPATH="/opt/gopath"
GOROOT="/usr/local/go/"

# 需要下载的 docker image
if [ "$PEER_TYPE"x == "orderer"x ]; then
  NEED_DOWNLOAD_IMAGES=(orderer tools)
else
  NEED_DOWNLOAD_IMAGES=(peer tools)
fi

function printHelp() {
  echo "用法:"
  echo "  start-$PEER_TYPE.sh <mode>"
  echo "    <mode> - 必须是 'up', 'down' 两者之一"
  echo "      - 'up'   - 启动${PEER_TYPE}节点"
  echo "      - 'down' - 停止${PEER_TYPE}节点"
  echo "  start-$PEER_TYPE.sh -h (打印帮助信息)"
  echo
}

if [ "$#" -eq 0 ]; then
  printHelp
  exit 0
fi

checkAndInstallGo() {
  which go >& /dev/null
  if [ $? -ne 0 ]; then
    mkdir -p /tmp/golang
    cd /tmp/golang
    curl -L https://dl.google.com/go/go1.13.5.linux-amd64.tar.gz > ./go1.13.5.linux-amd64.tar.gz
    tar xf ./go1.13.5.linux-amd64.tar.gz
    mv ./go /usr/local/go
    cd -
    rm -rf /tmp/golang
  fi
  env | grep 'GOPATH'
  if [ $? -ne 0 ]; then
    echo "export GOROOT=$GOROOT">>/etc/profile
    echo "export GOPATH=$GOPATH">>/etc/profile
    mkdir -p $GOPATH/src/github.com/hyperledger
    mkdir -p $GOPATH/bin
    echo "export PATH=$PATH:$GOPATH/bin:$GOROOT/bin">>/etc/profile
    source /etc/profile
  fi
}

fabricCodePull() {
  if [ ! -d "$GOPATH/src/github.com/hyperledger/fabric" ]; then
    cd $GOPATH/src/github.com/hyperledger
    git clone https://github.com/hyperledger/fabric.git
    cd -
    cd $GOPATH/src/github.com/hyperledger/fabric
    git checkout "v${VERSION}"
    make release && cp ./release/linux-amd64/bin/* $GOPATH/bin
    cd -
  fi
}

dockerFabricPull() {
  local FABRIC_TAG=$1
  for IMAGES in $NEED_DOWNLOAD_IMAGES; do
    echo "==> FABRIC IMAGE: $IMAGES"
    echo
    docker pull hyperledger/fabric-$IMAGES:$FABRIC_TAG
    docker tag hyperledger/fabric-$IMAGES:$FABRIC_TAG hyperledger/fabric-$IMAGES
  done
}

dockerThirdPartyImagesPull() {
  if [ "$PEER_TYPE"x == "peer"x ]; then
    local THIRDPARTY_TAG=$1
    IMAGES="couchdb"
    echo "==> THIRDPARTY DOCKER IMAGE: $IMAGES"
    echo
    docker pull hyperledger/fabric-$IMAGES:$THIRDPARTY_TAG
    docker tag hyperledger/fabric-$IMAGES:$THIRDPARTY_TAG hyperledger/fabric-$IMAGES
  fi
}

dockerInstall() {
  which docker >& /dev/null
  NODOCKER=$?
  if [ "${NODOCKER}" -ne 0 ]; then
    yum install docker-ce

    which docker-compose >& /dev/null
    if [ "$?" -ne 0 ]; then
      curl -L https://get.daocloud.io/docker/compose/releases/download/1.22.0/docker-compose-`uname -s`-`uname -m` > /usr/local/bin/docker-compose
      chmod +x /usr/local/bin/docker-compose
    fi
  fi

  echo "===> Pulling fabric Images"
  dockerFabricPull ${FABRIC_TAG}
  echo "===> Pulling thirdparty docker images"
  dockerThirdPartyImagesPull ${THIRDPARTY_TAG}
  echo
  echo "===> List out hyperledger docker images"
  docker images | grep hyperledger*
}

# Obtain CONTAINER_IDS and remove them
# TODO Might want to make this optional - could clear other containers
function clearContainers() {
  CONTAINER_IDS=$(docker ps -a -q)
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

function removeDockerRubbish() {
  docker network prune -f
  docker volume prune -f
}

# Versions of fabric known not to work with this release of first-network
BLACKLISTED_VERSIONS="^1\.0\. ^1\.1\.0-preview ^1\.1\.0-alpha"

# Do some basic sanity checking to make sure that the appropriate versions of fabric
# binaries/images are available.  In the future, additional checking for the presence
# of go or other items could be added.
function checkPrereqs() {
  # Note, we check configtxlator externally because it does not require a config file, and peer in the
  # docker image because of FAB-8551 that makes configtxlator return 'development version' in docker
  DOCKER_IMAGE_VERSION=$(docker run --rm hyperledger/fabric-tools:$IMAGETAG peer version | sed -ne 's/ Version: //p' | head -1)

  echo "DOCKER_IMAGE_VERSION=$DOCKER_IMAGE_VERSION"

  for UNSUPPORTED_VERSION in $BLACKLISTED_VERSIONS; do
    echo "$DOCKER_IMAGE_VERSION" | grep -q $UNSUPPORTED_VERSION
    if [ $? -eq 0 ]; then
      echo "ERROR! Fabric Docker image version of $DOCKER_IMAGE_VERSION does not match this newer version of BYFN and is unsupported. Either move to a later version of Fabric or checkout an earlier version of fabric-samples."
      exit 1
    fi
  done
}

function clearCli(){
  docker stop cli
  docker rm cli
}

# Generate the needed certificates, the genesis block and start the network.
function networkUp() {
  checkAndInstallGo
  fabricCodePull

  checkPrereqs
  dockerInstall

  # check artifacts directory
  if [ ! -d "../crypto-config" ]; then
    echo "ERROR! crypto-config path not exist"
    exit 1
  fi

  COMPOSE_FILES="-f ${COMPOSE_FILE}"
  IMAGE_TAG=$IMAGETAG docker-compose ${COMPOSE_FILES} up -d 2>&1
  docker ps -a
  if [ $? -ne 0 ]; then
    echo "ERROR !!!! Unable to start network"
    exit 1
  fi
}

# Tear down running network
function networkDown() {
  # stop org3 containers also in addition to org1 and org2, in case we were running sample to add org3
  # stop kafka and zookeeper containers in case we're running with kafka consensus-type
  docker-compose -f $COMPOSE_FILE -f $COMPOSE_FILE_COUCH -f $COMPOSE_FILE_KAFKA -f $COMPOSE_FILE_RAFT2 -f $COMPOSE_FILE_CA -f $COMPOSE_FILE_ORG3 down --volumes --remove-orphans

  # Don't remove the generated artifacts -- note, the ledgers are always removed
  if [ "$MODE" != "restart" ]; then
    # Bring down the network, deleting the volumes
    # Delete any ledger backups
    docker run -v $PWD:/tmp/first-network --rm hyperledger/fabric-tools:$IMAGETAG rm -Rf /tmp/first-network/ledgers-backup
    #Cleanup the chaincode containers
    clearContainers
    #Cleanup images
    removeUnwantedImages
    # clear docker's network and volumns
    removeDockerRubbish
  fi
}

function clearCli(){
  docker stop cli
  docker rm cli
}

# use this as the default docker-compose yaml definition
COMPOSE_FILE=docker-compose-$PEER_TYPE.yaml
#
# use golang as the default language for chaincode
LANGUAGE=golang

# Parse commandline args
MODE=$1

#Create the network using docker compose
if [ "${MODE}" == "up" ]; then
  networkUp
elif [ "${MODE}" == "down" ]; then ## Clear the network
  networkDown
elif [ "${MODE}" == "restart" ]; then ## Restart the network
  networkDown
  networkUp
elif [ "${MODE}" == "startCli" ]; then
  clearCli
  networkUp
else
  exit 1
fi
