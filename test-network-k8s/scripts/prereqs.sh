#!/bin/bash
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

  # Use the local fabric binaries if available.  If not, go get them.
  bin/peer version &> /dev/null
  if [[ $? -ne 0 ]]; then
    echo "Downloading LATEST Fabric binaries and config"
    curl -sSL https://raw.githubusercontent.com/hyperledger/fabric/main/scripts/bootstrap.sh \
      | bash -s -- -s -d

    # remove sample config files extracted by the installation script
    rm config/configtx.yaml
    rm config/core.yaml
    rm config/orderer.yaml
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