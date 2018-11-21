#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

# docker container list
CONTAINER_LIST=(peer0.org1 peer1.org1 peer0.org2 peer1.org2 peer0.org3 peer1.org3 orderer)
COUCHDB_CONTAINER_LIST=(couchdb0 couchdb1 couchdb2 couchdb3 couchdb4 couchdb5)

MARCH=$(uname -s|tr '[:upper:]' '[:lower:]')
echo "-----------> MARCH" $MARCH
echo "-----------> PROJECT_VERSION:" $PROJECT_VERSION
# Download latest binaries from nexus2
MVN_METADATA=$(echo "https://nexus.hyperledger.org/content/repositories/releases/org/hyperledger/fabric/hyperledger-fabric-$PROJECT_VERSION/maven-metadata.xml")
curl -L "$MVN_METADATA" > maven-metadata.xml
RELEASE_TAG=$(cat maven-metadata.xml | grep release)
COMMIT=$(echo $RELEASE_TAG | awk -F - '{ print $4 }' | cut -d "<" -f1)
echo "-----------> COMMIT = $COMMIT"
cd $BASE_FOLDER/fabric-samples || exit
curl https://nexus.hyperledger.org/content/repositories/releases/org/hyperledger/fabric/hyperledger-fabric-$PROJECT_VERSION/$MARCH-$ARCH.$PROJECT_VERSION-$COMMIT/hyperledger-fabric-$PROJECT_VERSION-$MARCH-$ARCH.$PROJECT_VERSION-$COMMIT.tar.gz | tar xz

if [ $? -ne 0 ]; then
   echo -e "\033[31m FAILED to download binaries" "\033[0m"
   exit 1
fi

cd first-network || exit
export PATH=gopath/src/github.com/hyperledger/fabric-samples/bin:$PATH

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

 echo "############## BYFN,EYFN DEFAULT CHANNEL TEST ###################"
 echo "#################################################################"
 echo y | ./byfn.sh -m down
 echo y | ./byfn.sh -m up -t 60
 copy_logs $? default-channel
 echo y | ./eyfn.sh -m up -t 60
 copy_logs $? default-channel
 echo y | ./eyfn.sh -m down
 echo

 echo "############### BYFN,EYFN CUSTOM CHANNEL WITH COUCHDB TEST ##############"
 echo "#########################################################################"
 echo y | ./byfn.sh -m up -c custom-channel-couchdb -s couchdb -t 75 -d 15
 copy_logs $? custom-channel-couch couchdb
 echo y | ./eyfn.sh -m up -c custom-channel-couchdb -s couchdb -t 75 -d 15
 copy_logs $? custom-channel-couch
 echo y | ./eyfn.sh -m down
 echo

 echo "############### BYFN,EYFN WITH NODE Chaincode. TEST ################"
 echo "####################################################################"
 echo y | ./byfn.sh -m up -l node -t 60
 copy_logs $? default-channel-node
 echo y | ./eyfn.sh -m up -l node -t 60
 copy_logs $? default-channel-node
 echo y | ./eyfn.sh -m down

 echo "############### FABRIC-CA SAMPLES TEST ########################"
 echo "###############################################################"
 cd $WORKSPACE/gopath/src/github.com/hyperledger/fabric-samples/fabric-ca
 ./start.sh
 copy_logs $? fabric-ca
 ./stop.sh
