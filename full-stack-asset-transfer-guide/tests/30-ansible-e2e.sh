#!/bin/bash

set -v -eou pipefail

# All tests run in the workshop root folder
cd "$(dirname "$0")"/..

# Clean house on exit
function exitHook() {

  # Just in case the just left some bits running around
  kind delete cluster --name kind

  # Just in case ...
  if docker inspect kind-registry &>/dev/null; then
      echo "Stopping container registry"
      docker kill kind-registry
      docker rm kind-registry
  fi

  # Delete the sample network configuration and crypto material
  rm -rf "${WORKSHOP_PATH}"/_cfg
}

trap exitHook SIGINT SIGTERM EXIT


###############################################################################
# 00-setup
###############################################################################

curl -sSL https://raw.githubusercontent.com/hyperledger/fabric/main/scripts/install-fabric.sh | bash -s -- binary

export WORKSHOP_PATH="${PWD}"
export PATH="${WORKSHOP_PATH}/bin:${PATH}"
export FABRIC_CFG_PATH="${WORKSHOP_PATH}/config"

"${WORKSHOP_PATH}/check.sh"


###############################################################################
# 10-kube
###############################################################################

# env checks
[[ ${WORKSHOP_PATH+x}   ]] || exit 1
[[ ${FABRIC_CFG_PATH+x} ]] || exit 1

just check-setup

# Set the ingress domain and target k8s namespace
export WORKSHOP_INGRESS_DOMAIN=localho.st
export WORKSHOP_NAMESPACE=fabricinfra


# Create a Kubernetes cluster in Docker, configure an Nginx ingress, and docker container registry
just kind

# KIND will set the current kube client context in ~/.kube/config
kubectl cluster-info

# Run k9s to observe the target namespace
# k9s -n $WORKSHOP_NAMESPACE



###############################################################################
# 20-fabric
###############################################################################

# Clear out any certs from a prior run, just in case
rm -rf ${WORKSHOP_PATH}/_cfg

just check-kube


# env checks
[[ ${WORKSHOP_PATH+x}           ]] || exit 1
[[ ${FABRIC_CFG_PATH+x}         ]] || exit 1
[[ ${WORKSHOP_INGRESS_DOMAIN+x} ]] || exit 1
[[ ${WORKSHOP_NAMESPACE+x}      ]] || exit 1

# check Nginx ingress
kubectl -n ingress-nginx get all
kubectl -n ingress-nginx get deployment.apps/ingress-nginx-controller

curl http://${WORKSHOP_INGRESS_DOMAIN}
curl --insecure https://${WORKSHOP_INGRESS_DOMAIN}:443

# Bring up the operator and console
just ansible-review-config

just ansible-ingress

just ansible-operator

just ansible-console

# Bring up the fabric network with ansible
# Unfortunately, the GHA executors do not have enough RAM to run the full network setup.
# We can at least check that the operator and console came up OK.
# just ansible-network


# Operator running?
kubectl -n ${WORKSHOP_NAMESPACE} get deployment fabric-operator

# Console running?
kubectl -n ${WORKSHOP_NAMESPACE} get deployment hlf-console

# Console listening at the Nginx ingress?
curl --fail --insecure https://${WORKSHOP_NAMESPACE}-hlf-console-console.${WORKSHOP_INGRESS_DOMAIN}/

