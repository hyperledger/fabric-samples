#!/bin/bash -e
set -euo pipefail

CONTAINER_CLI=${CONTAINER_CLI:-"docker"}
FABRIC_VERSION=${FABRIC_VERSION:-2.4}
STABLE_TAG=amd64-${FABRIC_VERSION}-stable

echo "Pulling images with $CONTAINER_CLI"

for image in baseos peer orderer ca tools orderer ccenv javaenv nodeenv tools; do
	$CONTAINER_CLI pull -q "hyperledger-fabric.jfrog.io/fabric-${image}:${STABLE_TAG}"
	$CONTAINER_CLI tag "hyperledger-fabric.jfrog.io/fabric-${image}:${STABLE_TAG}" hyperledger/fabric-${image}
	$CONTAINER_CLI tag "hyperledger-fabric.jfrog.io/fabric-${image}:${STABLE_TAG}" "hyperledger/fabric-${image}:${FABRIC_VERSION}"
	$CONTAINER_CLI rmi -f "hyperledger-fabric.jfrog.io/fabric-${image}:${STABLE_TAG}"
done

$CONTAINER_CLI pull -q couchdb:3.1.1
$CONTAINER_CLI images | grep hyperledger
