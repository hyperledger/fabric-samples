#!/bin/bash

set -v -eou pipefail

# Log all commands
set -x

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


kubectl version --client -o yaml

kind version

export LOG_ERROR_LINES=20


###############################################################################
# 10-kube
###############################################################################

# env checks
[[ ${WORKSHOP_PATH+x}   ]] || exit 1
[[ ${FABRIC_CFG_PATH+x} ]] || exit 1

just check-setup

# Set the ingress domain and target k8s namespace
export WORKSHOP_INGRESS_DOMAIN=localho.st
export WORKSHOP_NAMESPACE=test-network


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
rm -rf ${WORKSHOP_PATH}/infrastructure/sample-network/temp

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

# Install operator CRDs
kubectl apply -k https://github.com/hyperledger-labs/fabric-operator.git/config/crd


kubectl get customresourcedefinition.apiextensions.k8s.io/ibpcas.ibp.com
kubectl get customresourcedefinition.apiextensions.k8s.io/ibpconsoles.ibp.com
kubectl get customresourcedefinition.apiextensions.k8s.io/ibporderers.ibp.com
kubectl get customresourcedefinition.apiextensions.k8s.io/ibppeers.ibp.com

kubectl wait --for condition=established customresourcedefinition.apiextensions.k8s.io/ibpcas.ibp.com
kubectl wait --for condition=established customresourcedefinition.apiextensions.k8s.io/ibpconsoles.ibp.com
kubectl wait --for condition=established customresourcedefinition.apiextensions.k8s.io/ibporderers.ibp.com
kubectl wait --for condition=established customresourcedefinition.apiextensions.k8s.io/ibppeers.ibp.com

# Bring up the network
just cloud-network

# Operator running?
kubectl -n ${WORKSHOP_NAMESPACE} get deployment fabric-operator

# Did it apply the CRDs?
kubectl -n ${WORKSHOP_NAMESPACE} get ibpca org0-ca
kubectl -n ${WORKSHOP_NAMESPACE} get ibpca org1-ca
kubectl -n ${WORKSHOP_NAMESPACE} get ibpca org2-ca
kubectl -n ${WORKSHOP_NAMESPACE} get ibppeer org1-peer1
kubectl -n ${WORKSHOP_NAMESPACE} get ibppeer org1-peer2
kubectl -n ${WORKSHOP_NAMESPACE} get ibppeer org2-peer1
kubectl -n ${WORKSHOP_NAMESPACE} get ibppeer org2-peer2
kubectl -n ${WORKSHOP_NAMESPACE} get ibporderer org0-orderersnode1
kubectl -n ${WORKSHOP_NAMESPACE} get ibporderer org0-orderersnode2
kubectl -n ${WORKSHOP_NAMESPACE} get ibporderer org0-orderersnode3

# Did the operator reconcile the CRDs as deployments?
kubectl -n ${WORKSHOP_NAMESPACE} get deployment fabric-operator
kubectl -n ${WORKSHOP_NAMESPACE} get deployment org0-ca
kubectl -n ${WORKSHOP_NAMESPACE} get deployment org0-orderersnode1
kubectl -n ${WORKSHOP_NAMESPACE} get deployment org0-orderersnode2
kubectl -n ${WORKSHOP_NAMESPACE} get deployment org0-orderersnode3
kubectl -n ${WORKSHOP_NAMESPACE} get deployment org1-ca
kubectl -n ${WORKSHOP_NAMESPACE} get deployment org1-peer1
kubectl -n ${WORKSHOP_NAMESPACE} get deployment org1-peer2
kubectl -n ${WORKSHOP_NAMESPACE} get deployment org2-ca
kubectl -n ${WORKSHOP_NAMESPACE} get deployment org2-peer1
kubectl -n ${WORKSHOP_NAMESPACE} get deployment org2-peer2

export WORKSHOP_CRYPTO=$WORKSHOP_PATH/infrastructure/sample-network/temp

# Hit the CAs using the TLS certs, etc.
curl -s --cacert $WORKSHOP_CRYPTO/cas/org0-ca/tls-cert.pem https://$WORKSHOP_NAMESPACE-org0-ca-ca.$WORKSHOP_INGRESS_DOMAIN/cainfo | jq -c
curl -s --cacert $WORKSHOP_CRYPTO/cas/org1-ca/tls-cert.pem https://$WORKSHOP_NAMESPACE-org1-ca-ca.$WORKSHOP_INGRESS_DOMAIN/cainfo | jq -c
curl -s --cacert $WORKSHOP_CRYPTO/cas/org2-ca/tls-cert.pem https://$WORKSHOP_NAMESPACE-org2-ca-ca.$WORKSHOP_INGRESS_DOMAIN/cainfo | jq -c

# create a channel
just cloud-channel

# enrollment certificates and channel MSP
find ${WORKSHOP_CRYPTO}


###############################################################################
# 30-chaincode
###############################################################################

just check-network

# env checks
[[ ${FABRIC_CFG_PATH+x}         ]] || exit 1
[[ ${WORKSHOP_PATH+x}           ]] || exit 1
[[ ${WORKSHOP_CRYPTO+x}         ]] || exit 1
[[ ${WORKSHOP_INGRESS_DOMAIN+x} ]] || exit 1
[[ ${WORKSHOP_NAMESPACE+x}      ]] || exit 1


# org1-peer1 peer CLI context
export ORG1_PEER1_ADDRESS=${WORKSHOP_NAMESPACE}-org1-peer1-peer.${WORKSHOP_INGRESS_DOMAIN}:443
export ORG1_PEER2_ADDRESS=${WORKSHOP_NAMESPACE}-org1-peer2-peer.${WORKSHOP_INGRESS_DOMAIN}:443

export CORE_PEER_LOCALMSPID=Org1MSP
export CORE_PEER_ADDRESS=${ORG1_PEER1_ADDRESS}
export CORE_PEER_TLS_ENABLED=true
export CORE_PEER_MSPCONFIGPATH=${WORKSHOP_CRYPTO}/enrollments/org1/users/org1admin/msp
export CORE_PEER_TLS_ROOTCERT_FILE=${WORKSHOP_CRYPTO}/channel-msp/peerOrganizations/org1/msp/tlscacerts/tlsca-signcert.pem
export CORE_PEER_CLIENT_CONNTIMEOUT=15s
export CORE_PEER_DELIVERYCLIENT_CONNTIMEOUT=15s
export ORDERER_ENDPOINT=${WORKSHOP_NAMESPACE}-org0-orderersnode1-orderer.${WORKSHOP_INGRESS_DOMAIN}:443
export ORDERER_TLS_CERT=${WORKSHOP_CRYPTO}/channel-msp/ordererOrganizations/org0/orderers/org0-orderersnode1/tls/signcerts/tls-cert.pem


function build_cc() {
  CONTAINER_REGISTRY=$WORKSHOP_INGRESS_DOMAIN:5000
  CHAINCODE_IMAGE=$CONTAINER_REGISTRY/$CHAINCODE_NAME

  # Build the chaincode image
  # TODO: configure buildx builders on the CI runners to ensure target arch and os are automatically set.
  docker build --build-arg TARGETARCH=amd64 -t $CHAINCODE_IMAGE contracts/$CHAINCODE_NAME-typescript

  # Push the image to the insecure container registry
  docker push $CHAINCODE_IMAGE
}

function prepare_cc() {
  IMAGE_DIGEST=$(docker inspect --format='{{index .RepoDigests 0}}' $CHAINCODE_IMAGE | cut -d'@' -f2)
  infrastructure/pkgcc.sh -l $CHAINCODE_NAME -n localhost:5000/$CHAINCODE_NAME -d $IMAGE_DIGEST
}

function install_cc() {

  CORE_PEER_ADDRESS=${ORG1_PEER1_ADDRESS} peer lifecycle chaincode install $CHAINCODE_PACKAGE
  CORE_PEER_ADDRESS=${ORG1_PEER2_ADDRESS} peer lifecycle chaincode install $CHAINCODE_PACKAGE

  export PACKAGE_ID=$(peer lifecycle chaincode calculatepackageid $CHAINCODE_PACKAGE) && echo $PACKAGE_ID

  peer lifecycle \
    chaincode       approveformyorg \
    --channelID     ${CHANNEL_NAME} \
    --name          ${CHAINCODE_NAME} \
    --version       ${VERSION} \
    --package-id    ${PACKAGE_ID} \
    --sequence      ${SEQUENCE} \
    --orderer       ${ORDERER_ENDPOINT} \
    --tls --cafile  ${ORDERER_TLS_CERT} \
    --connTimeout   15s

  peer lifecycle \
    chaincode       commit \
    --channelID     ${CHANNEL_NAME} \
    --name          ${CHAINCODE_NAME} \
    --version       ${VERSION} \
    --sequence      ${SEQUENCE} \
    --orderer       ${ORDERER_ENDPOINT} \
    --tls --cafile  ${ORDERER_TLS_CERT} \
    --connTimeout   15s
}

function check_cc_meta() {
  peer chaincode query -n $CHAINCODE_NAME -C mychannel -c '{"Args":["org.hyperledger.fabric:GetMetadata"]}' | jq
}


###############################################################################
# 31 : build, tag, push, install
###############################################################################

CHANNEL_NAME=mychannel
VERSION=v0.0.1
SEQUENCE=1

CHAINCODE_NAME=asset-transfer
CHAINCODE_PACKAGE=${CHAINCODE_NAME}.tgz

build_cc
prepare_cc
install_cc
check_cc_meta

# cc pod is up - is there a selector for the target sequence / rev?
kubectl -n test-network describe pods -l app.kubernetes.io/created-by=fabric-builder-k8s

kubectl -n ${WORKSHOP_NAMESPACE} describe pods -l app.kubernetes.io/created-by=fabric-builder-k8s
COUNT=$(kubectl -n ${WORKSHOP_NAMESPACE} get pods -l app.kubernetes.io/created-by=fabric-builder-k8s | wc -l)

# one pod per peer + header line
[[ $COUNT -eq 3 ]]



###############################################################################
# 31 : build, tag, push, install
###############################################################################

# build again and iterate
SEQUENCE=$((SEQUENCE + 1))
VERSION=v0.0.$SEQUENCE

build_cc
prepare_cc
install_cc
check_cc_meta

COUNT=$(kubectl -n ${WORKSHOP_NAMESPACE} get pods -l app.kubernetes.io/created-by=fabric-builder-k8s | wc -l)

# one pod per peer + header line
[[ $COUNT -eq 5 ]]


###############################################################################
# 32 : Install chaincode from a prepared / CI package on a web server
###############################################################################

SEQUENCE=$((SEQUENCE + 1))
VERSION=v0.1.3
CHAINCODE_PACKAGE=asset-transfer-typescript-${VERSION}.tgz

# Download the package from a github release
curl -LO https://github.com/hyperledgendary/full-stack-asset-transfer-guide/releases/download/${VERSION}/${CHAINCODE_PACKAGE}

install_cc
check_cc_meta

COUNT=$(kubectl -n ${WORKSHOP_NAMESPACE} get pods -l app.kubernetes.io/created-by=fabric-builder-k8s | wc -l)

# one pod per peer + header line
[[ $COUNT -eq 7 ]]


###############################################################################
# 33 : crazy time, run CCaaS on localhost, invoked by peer in k8s
###############################################################################

echo "todo: 33 : crazy time, run CCaaS on localhost, invoked by peer in k8s"


###############################################################################
# 40-bananas
###############################################################################


# env checks
[[ ${FABRIC_CFG_PATH+x}         ]] || exit 1
[[ ${WORKSHOP_PATH+x}           ]] || exit 1
[[ ${WORKSHOP_CRYPTO+x}         ]] || exit 1
[[ ${WORKSHOP_INGRESS_DOMAIN+x} ]] || exit 1
[[ ${WORKSHOP_NAMESPACE+x}      ]] || exit 1

# User organization MSP ID
export MSP_ID=Org1MSP
export ORG=org1
export USERNAME=org1user
export PASSWORD=org1userpw

# register / enroll the new user
ADMIN_MSP_DIR=$WORKSHOP_CRYPTO/enrollments/${ORG}/users/rcaadmin/msp
USER_MSP_DIR=$WORKSHOP_CRYPTO/enrollments/${ORG}/users/${USERNAME}/msp
PEER_MSP_DIR=$WORKSHOP_CRYPTO/channel-msp/peerOrganizations/${ORG}/msp

fabric-ca-client  register \
  --id.name       $USERNAME \
  --id.secret     $PASSWORD \
  --id.type       client \
  --url           https://$WORKSHOP_NAMESPACE-$ORG-ca-ca.$WORKSHOP_INGRESS_DOMAIN \
  --tls.certfiles $WORKSHOP_CRYPTO/cas/$ORG-ca/tls-cert.pem \
  --mspdir        $WORKSHOP_CRYPTO/enrollments/$ORG/users/rcaadmin/msp

fabric-ca-client enroll \
  --url           https://$USERNAME:$PASSWORD@$WORKSHOP_NAMESPACE-$ORG-ca-ca.$WORKSHOP_INGRESS_DOMAIN \
  --tls.certfiles $WORKSHOP_CRYPTO/cas/$ORG-ca/tls-cert.pem \
  --mspdir        $WORKSHOP_CRYPTO/enrollments/$ORG/users/$USERNAME/msp

mv $USER_MSP_DIR/keystore/*_sk $USER_MSP_DIR/keystore/key.pem


# Path to private key file
export PRIVATE_KEY=${USER_MSP_DIR}/keystore/key.pem

# Path to user certificate file
export CERTIFICATE=${USER_MSP_DIR}/signcerts/cert.pem

# Path to CA certificate
export TLS_CERT=${PEER_MSP_DIR}/tlscacerts/tlsca-signcert.pem

# Gateway peer SSL host name override
export HOST_ALIAS=${WORKSHOP_NAMESPACE}-${ORG}-peer1-peer.${WORKSHOP_INGRESS_DOMAIN}

# Gateway endpoint
export ENDPOINT=$HOST_ALIAS:443


# Build the client application
pushd applications/trader-typescript

npm install

npm start getAllAssets

npm start create banana bananaman yellow

npm start getAllAssets

npm start transact

npm start getAllAssets


##### Take it further: Gateway load balancing

# Set up the k8s Ingress and Service
kubectl kustomize \
  ../../infrastructure/sample-network/config/gateway \
  | envsubst \
  | kubectl -n ${WORKSHOP_NAMESPACE} apply -f -


# Try submitting a few transactions to the different peer endpoints
unset HOST_ALIAS


# Try a few times with org1-peer1
export ENDPOINT=${WORKSHOP_NAMESPACE}-org1-peer1-peer.${WORKSHOP_INGRESS_DOMAIN}:443

npm start getAllAssets
npm start getAllAssets
npm start getAllAssets
npm start getAllAssets


# Then with org1-peer2
export ENDPOINT=${WORKSHOP_NAMESPACE}-org1-peer2-peer.${WORKSHOP_INGRESS_DOMAIN}:443

npm start getAllAssets
npm start getAllAssets
npm start getAllAssets
npm start getAllAssets


# Then with the gateway endpoint.  Connections will be distributed across org1-peer1 and org1-peer2
export ENDPOINT=${WORKSHOP_NAMESPACE}-org1-peer-gateway.${WORKSHOP_INGRESS_DOMAIN}:443

npm start getAllAssets
npm start getAllAssets
npm start getAllAssets
npm start getAllAssets
npm start getAllAssets

npm start transact
npm start getAllAssets
npm start getAllAssets
npm start getAllAssets
npm start getAllAssets

popd


###############################################################################
# 90-teardown
###############################################################################

just cloud-network-down


###############################################################################
# Looks good!
###############################################################################
exit 0
