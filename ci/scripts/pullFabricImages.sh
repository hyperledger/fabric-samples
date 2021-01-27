#!/bin/bash -e
set -euo pipefail

FABRIC_VERSION=${FABRIC_VERSION:-2.4}
STABLE_TAG=amd64-${FABRIC_VERSION}-stable

for image in baseos peer orderer ca tools orderer ccenv javaenv nodeenv tools; do
	docker pull -q "hyperledger-fabric.jfrog.io/fabric-${image}:${STABLE_TAG}"
	docker tag "hyperledger-fabric.jfrog.io/fabric-${image}:${STABLE_TAG}" hyperledger/fabric-${image}
	docker tag "hyperledger-fabric.jfrog.io/fabric-${image}:${STABLE_TAG}" "hyperledger/fabric-${image}:${FABRIC_VERSION}"
	docker rmi -f "hyperledger-fabric.jfrog.io/fabric-${image}:${STABLE_TAG}"
done

docker pull -q couchdb:3.1.1
docker images | grep hyperledger
