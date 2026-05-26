#!/usr/bin/env bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

# Double check that kind, kubectl, docker, and all required images are present.
function check_prereqs() {

  set +e

  ${CONTAINER_CLI} version > /dev/null
  if [[ $? -ne 0 ]]; then
    echo "No '${CONTAINER_CLI}' binary available?"
    exit 1
  fi

  if [ "${CLUSTER_RUNTIME}" == "kind" ]; then
    kind version > /dev/null
    if [[ $? -ne 0 ]]; then
      echo "No 'kind' binary available? (https://kind.sigs.k8s.io/docs/user/quick-start/#installation)"
      exit 1
    fi
  fi

  kubectl > /dev/null
  if [[ $? -ne 0 ]]; then
    echo "No 'kubectl' binary available? (https://kubernetes.io/docs/tasks/tools/)"
    exit 1
  fi

  jq --version > /dev/null
  if [[ $? -ne 0 ]]; then
    echo "No 'jq' binary available? (https://stedolan.github.io/jq/)"
    exit 1
  fi

  echo | envsubst > /dev/null
  if [[ $? -ne 0 ]]; then
    echo "No 'envsubst' binary (gettext package) available? (https://www.gnu.org/software/gettext/)"
    exit 1
  fi

  # Define the sed expression to extract the version number
  VERSION_SED_EXPR='s/^ Version: v\{0,1\}\(.*\)$/\1/p'

  # Use the fabric peer and ca containers to check fabric image versions
  # NOTE: About extracting the version number:
  # In older versions, the prefix 'v' was not included in the version string,
  # but in recent versions, 'v' has been added.
  # The following commands remove the optional 'v' to standardize the format.
  FABRIC_IMAGE_VERSION=$(${CONTAINER_CLI} run --rm ${FABRIC_PEER_IMAGE} peer version | sed -ne "$VERSION_SED_EXPR")
  FABRIC_CA_IMAGE_VERSION=$(${CONTAINER_CLI} run --rm ${FABRIC_CONTAINER_REGISTRY}/fabric-ca:$FABRIC_CA_VERSION fabric-ca-server version | sed -ne "$VERSION_SED_EXPR")
  echo "Fabric image versions: Peer ($FABRIC_IMAGE_VERSION), CA ($FABRIC_CA_IMAGE_VERSION)"
  if [ -z "$FABRIC_IMAGE_VERSION" ] || [ -z "$FABRIC_CA_IMAGE_VERSION" ]; then
    echo "It seems some of the specified Fabric images are not available."
    exit 1
  fi

  # Use the local fabric binaries if available. If not, go get them.
  bin/peer version &> /dev/null
  if [[ $? -ne 0 ]]; then
    echo "Downloading Fabric binaries and config"
    curl -sSL https://raw.githubusercontent.com/hyperledger/fabric/main/scripts/install-fabric.sh \
      | bash -s -- -f ${FABRIC_IMAGE_VERSION} -c ${FABRIC_CA_IMAGE_VERSION} binary

    # remove sample config files extracted by the installation script
    rm config/configtx.yaml
    rm config/core.yaml
    rm config/orderer.yaml
  fi

  # Check if the binaries match your docker images
  FABRIC_LOCAL_VERSION=$(bin/peer version | sed -ne "$VERSION_SED_EXPR")
  FABRIC_CA_LOCAL_VERSION=$(bin/fabric-ca-client version | sed -ne "$VERSION_SED_EXPR")
    echo "Fabric binary versions: Peer ($FABRIC_LOCAL_VERSION), CA ($FABRIC_CA_LOCAL_VERSION)"
  if [ "$FABRIC_LOCAL_VERSION" != "$FABRIC_IMAGE_VERSION" ] || [ "$FABRIC_CA_LOCAL_VERSION" != "$FABRIC_CA_IMAGE_VERSION" ]; then
    echo "WARN: Local fabric binaries and docker images are out of sync. This may cause problems."
  fi

  export PATH=bin:$PATH

  # Double-check that the binary transfer was OK
  peer version > /dev/null
  if [[ $? -ne 0 ]]; then
    log "No 'peer' binary available?"
    exit 1
  fi

  set -e
}