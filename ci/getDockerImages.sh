#!/bin/bash -e
set -o pipefail

echo "======== PULL DOCKER IMAGES ========"
##########################################################
REPO_URL=hyperledger-fabric.jfrog.io
ORG_NAME="fabric"

VERSION=2.0.0
ARCH="amd64"
: ${STABLE_VERSION:=$VERSION-stable}
STABLE_TAG=$ARCH-$STABLE_VERSION
MASTER_TAG=$ARCH-stable

echo "---------> STABLE_VERSION:" $STABLE_VERSION

dockerTag() {
  for IMAGES in baseos peer orderer ca tools orderer ccenv javaenv nodeenv; do
    echo "Images: $IMAGES"
    echo
    docker pull $REPO_URL/$ORG_NAME-$IMAGES:$STABLE_TAG
          if [ $? != 0 ]; then
             echo  "FAILED: Docker Pull Failed on $IMAGES"
             exit 1
          fi
    docker tag $REPO_URL/$ORG_NAME-$IMAGES:$STABLE_TAG hyperledger/$ORG_NAME-$IMAGES
    docker tag $REPO_URL/$ORG_NAME-$IMAGES:$STABLE_TAG hyperledger/$ORG_NAME-$IMAGES:latest
    docker tag $REPO_URL/$ORG_NAME-$IMAGES:$STABLE_TAG hyperledger/$ORG_NAME-$IMAGES:$ARCH-$VERSION-stable
    docker tag $REPO_URL/$ORG_NAME-$IMAGES:$STABLE_TAG hyperledger/$ORG_NAME-$IMAGES:$ARCH-stable
    docker tag $REPO_URL/$ORG_NAME-$IMAGES:$STABLE_TAG hyperledger/$ORG_NAME-$IMAGES:$VERSION
    
    echo "Deleting  docker images: $IMAGES"
    docker rmi -f $REPO_URL/$ORG_NAME-$IMAGES:$STABLE_TAG
  done
}

dockerTag

echo
docker images 
echo