#
# Copyright contributors to the Hyperledgendary Full Stack Asset Transfer project
#
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at:
#
# 	  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Main justfile to run all the development scripts
# To install 'just' see https://github.com/casey/just#installation


###############################################################################
# COMMON TARGETS                                                              #
###############################################################################


# Ensure all properties are exported as shell env-vars
set export

# set the current directory, and the location of the test dats
CWDIR := justfile_directory()

_default:
  @just -f {{justfile()}} --list

# Run the check script to validate tool versions installed
check:
  ${CWDIR}/check.sh

cluster_name    := env_var_or_default("WORKSHOP_CLUSTER_NAME",       "kind")
cluster_runtime := env_var_or_default("WORKSHOP_CLUSTER_RUNTIME",    "kind")
ingress_domain  := env_var_or_default("WORKSHOP_INGRESS_DOMAIN",     "localho.st")
storage_class   := env_var_or_default("WORKSHOP_STORAGE_CLASS",      "standard")
chaincode_name  := env_var_or_default("WORKSHOP_CHAINCODE_NAME",     "asset-transfer")
internal_repo_endpoint  := env_var_or_default("WORKSHOP_INTERNAL_REPO",     "localhost:5000")
external_repo_endpoint  := env_var_or_default("WORKSHOP_EXTERNAL_REPO",     "localhost:5000")
cluster_type    := env_var_or_default("WORKSHOP_CLUSTER_TYPE",       "k8s")


# Start a local KIND cluster with nginx, localhost:5000 registry, and *.localho.st alias in kube DNS
kind: unkind
    #!/bin/bash
    set -e -o pipefail

    infrastructure/kind_with_nginx.sh {{cluster_name}}
    ls -lart ~/.kube/config
    chmod o+r ~/.kube/config

    # check connectivity to local k8s
    kubectl cluster-info &>/dev/null

# Shut down the KIND cluster
unkind:
    #!/bin/bash
    kind delete cluster --name {{cluster_name}}

    if docker inspect kind-registry &>/dev/null; then
        echo "Stopping container registry"
        docker kill kind-registry
        docker rm kind-registry
    fi

# Bring up the nginx ingress controller on the target k8s cluster
nginx:
    #!/bin/bash
    kubectl apply -k https://github.com/hyperledger-labs/fabric-operator.git/config/ingress/{{ cluster_runtime }}

    sleep 20

    kubectl wait --namespace ingress-nginx \
      --for=condition=ready pod \
      --selector=app.kubernetes.io/component=controller \
      --timeout=3m

# Just start the operator
operator: operator-crds
    infrastructure/sample-network/network operator

# Just start the console
console: operator
    infrastructure/sample-network/network console

# Just install the operator CRDs
operator-crds: check-kube
    kubectl apply -k https://github.com/hyperledger-labs/fabric-operator.git/config/crd


###############################################################################
# TEST TARGETS
###############################################################################

# Run e2e tests of all scenarios
test: test-chaincode test-appdev test-cloud # test-ansible

# Run an e2e test of the SmartContractDev scenario
test-chaincode:
    tests/00-chaincode-e2e.sh

# Run an e2e test of the ApplicationDev scenario
test-appdev:
    tests/10-appdev-e2e.sh

# Run an e2e test of the CloudNative scenario
test-cloud:
    tests/20-cloud-e2e.sh

# Run tests of the network setup with operator, console, and ansible plays
test-ansible:
    tests/30-ansible-e2e.sh

# Run tests of the console setup using the direct line to kube API controller (not ansible)
test-console:
    tests/40-console.sh


###############################################################################
# MICROFAB / DEV TARGETS                                                      #
###############################################################################

# Shut down the microfab (uf) instance
microfab-down:
    #!/bin/bash

    if docker inspect microfab &>/dev/null; then
        echo "Removing existing microfab container:"
        docker kill microfab
    fi


# Start a micro fab instance and create configuration in _cfg/uf
microfab: microfab-down
    #!/bin/bash
    set -e -o pipefail

    export CFG=$CWDIR/_cfg/uf
    export MICROFAB_CONFIG='{
        "endorsing_organizations":[
            {
                "name": "org1"
            },
            {
                "name": "org2"
            }
        ],
        "channels":[
            {
                "name": "mychannel",
                "endorsing_organizations":[
                    "org1"
                ]
            },
            {
                "name": "appchannel",
                "endorsing_organizations":[
                    "org1","org2"
                ]
            }

        ],
        "capability_level":"V2_0"
    }'

    mkdir -p $CFG
    echo
    echo "Stating microfab...."

    docker run --name microfab -p 8080:8080 --add-host host.docker.internal:host-gateway --rm -d -e MICROFAB_CONFIG="${MICROFAB_CONFIG}"  ibmcom/ibp-microfab:0.0.16
    sleep 5

    curl -s http://console.127-0-0-1.nip.io:8080/ak/api/v1/components | weft microfab -w $CFG/_wallets -p $CFG/_gateways -m $CFG/_msp -f
    cat << EOF > $CFG/org1admin.env
    export CORE_PEER_LOCALMSPID=org1MSP
    export CORE_PEER_MSPCONFIGPATH=$CFG/_msp/org1/org1admin/msp
    export CORE_PEER_ADDRESS=org1peer-api.127-0-0-1.nip.io:8080
    export FABRIC_CFG_PATH=$CWDIR/config
    export CORE_PEER_CLIENT_CONNTIMEOUT=15s
    export CORE_PEER_DELIVERYCLIENT_CONNTIMEOUT=15s
    EOF

    cat << EOF > $CFG/org2admin.env
    export CORE_PEER_LOCALMSPID=org2MSP
    export CORE_PEER_MSPCONFIGPATH=$CFG/_msp/org2/org2admin/msp
    export CORE_PEER_ADDRESS=org2peer-api.127-0-0-1.nip.io:8080
    export FABRIC_CFG_PATH=$CWDIR/config
    export CORE_PEER_CLIENT_CONNTIMEOUT=15s
    export CORE_PEER_DELIVERYCLIENT_CONNTIMEOUT=15s
    EOF

    echo
    echo "To get an peer cli environment run:"
    echo
    echo 'source $WORKSHOP_PATH/_cfg/uf/org1admin.env'

# Creates a chaincode package and install/approve/commit
debugcc:
    #!/bin/bash
    set -e -o pipefail

    export CFG=$CWDIR/_cfg/uf

    pushd $CWDIR/contracts/asset-transfer-typescript

    # this is the ip address the peer will use to talk to the CHAINCODE_ID
    # remember this is relative from where the peer is running.
    export CHAINCODE_SERVER_ADDRESS=host.docker.internal:9999
    export CHAINCODE_ID=$(weft chaincode package caas --path . --label asset-transfer --address ${CHAINCODE_SERVER_ADDRESS} --archive asset-transfer.tgz --quiet)
    export CORE_PEER_LOCALMSPID=org1MSP
    export CORE_PEER_MSPCONFIGPATH=$CFG/_msp/org1/org1admin/msp
    export CORE_PEER_ADDRESS=org1peer-api.127-0-0-1.nip.io:8080
    export CORE_PEER_CLIENT_CONNTIMEOUT=15s
    export CORE_PEER_DELIVERYCLIENT_CONNTIMEOUT=15s

    echo "CHAINCODE_ID=${CHAINCODE_ID}"

    set -x && peer lifecycle chaincode install asset-transfer.tgz &&     { set +x; } 2>/dev/null
    echo
    set -x && peer lifecycle chaincode approveformyorg --channelID mychannel --name asset-transfer -v 0 --package-id $CHAINCODE_ID --sequence 1 --connTimeout 15s && { set +x; } 2>/dev/null
    echo
    set -x && peer lifecycle chaincode commit --channelID mychannel --name asset-transfer -v 0 --sequence 1  --connTimeout 15s && { set +x; } 2>/dev/null
    echo
    set -x && peer lifecycle chaincode querycommitted --channelID=mychannel && { set +x; } 2>/dev/null
    echo
    popd

    cat << CC_EOF >> $CFG/org1admin.env
    export CHAINCODE_SERVER_ADDRESS=0.0.0.0:9999
    export CHAINCODE_ID=${CHAINCODE_ID}
    CC_EOF

    echo "Added CHAINCODE_ID and CHAINCODE_SERVER_ADDRESS to org1admin.env"
    echo
    echo '   source $WORKSHOP_PATH/_cfg/uf/org1admin.env'

###############################################################################
# CLOUD NATIVE TARGETS                                                        #
###############################################################################

# Deploy the operator sample network and create a channel
cloud-network: cloud-network-down check-kube
    infrastructure/sample-network/network up

# Tear down the operator sample network
cloud-network-down:
    infrastructure/sample-network/network down

# Create 'mychannel'
cloud-channel:
    infrastructure/sample-network/network channel create

# Check that the cloud setup has been performed
check-setup: check

# Check that the k8s API controller is ready
check-kube: check-setup
    checks/check-kube.sh

# Check that the sample network and channel have been deployed
check-network: check-kube
    checks/check-network.sh

# Check that the smart contract has been deployed
check-chaincode: check-network
    checks/check-chaincode.sh

# Create 'rest-easy'
cloud-rest-easy:
    infrastructure/sample-network/network rest-easy
# Create 'frontend'
cloud-frontend:
    infrastructure/sample-network/network frontend

###############################################################################
# ANSIBLE PLAYBOOK TARGETS                                                    #
###############################################################################

ansible_image   := env_var_or_default("ANSIBLE_IMAGE",      "ghcr.io/ibm-blockchain/ofs-ansibe:sha-ac6fd82")
namespace       := env_var_or_default("WORKSHOP_NAMESPACE", "fabricinfra")

# just set up everything with Ansible
ansible-doit: ansible-review-config ansible-operator ansible-console ansible-network


# Review the Ansible Blockchain Collection configuration in _cfg/
ansible-review-config:
    #!/bin/bash
    mkdir -p ${CWDIR}/_cfg
    rm -rf ${CWDIR}/_cfg/*  || true

    cp ${CWDIR}/infrastructure/configuration/*.yml ${CWDIR}/_cfg

    cat ${CWDIR}/infrastructure/configuration/operator-console-vars.yml | envsubst > ${CWDIR}/_cfg/operator-console-vars.yml

    echo ""
    echo ">> Fabric Common Configuration"
    echo ""
    cat ${CWDIR}/_cfg/fabric-common-vars.yml

    echo ""
    echo ">> Fabric Org1 Configuration"
    echo ""
    cat ${CWDIR}/_cfg/fabric-org1-vars.yml

    echo ""
    echo ">> Fabric Org2 Configuration"
    echo ""
    cat ${CWDIR}/_cfg/fabric-org2-vars.yml

    echo ""
    echo ">> Fabric Orderer Configuration"
    echo ""
    cat ${CWDIR}/_cfg/fabric-ordering-org-vars.yml

    echo ""
    echo ">> Fabric Operations Console Configuration"
    echo ""
    cat ${CWDIR}/_cfg/operator-console-vars.yml
    echo ""

# Start the Kubernetes fabric-operator with the Ansible Blockchain Collection
ansible-ingress:
    #!/bin/bash
    set -ex -o pipefail

    export EXTRAS=""
    if [ -f "/_cfg/k8s_context.yaml" ]; then
        export EXTRAS=" -e KUBECONFIG=/_cfg/k8s_context.yaml"
    fi

    docker run \
        --rm \
        -v ${HOME}/.kube/:/home/ibp-user/.kube/ \
        -v ${CWDIR}/_cfg:/_cfg \
        -v $(pwd)/infrastructure/kind_console_ingress:/playbooks \
        --network=host ${EXTRAS} \
        --workdir /playbooks \
        {{ansible_image}} \
            ansible-playbook /playbooks/90-KIND-ingress.yml



# Start the Kubernetes fabric-operator with the Ansible Blockchain Collection
ansible-operator:
    #!/bin/bash
    set -ex -o pipefail

    export EXTRAS=""
    if [ -f "/_cfg/k8s_context.yaml" ]; then
        export EXTRAS=" -e KUBECONFIG=/_cfg/k8s_context.yaml"
    fi

    docker run \
        --rm \
        -v ${HOME}/.kube/:/home/ibp-user/.kube/ \
        -v ${CWDIR}/_cfg:/_cfg \
        -v $(pwd)/infrastructure/operator_console_playbooks:/playbooks ${EXTRAS} \
        --network=host \
        {{ansible_image}} \
            ansible-playbook /playbooks/01-operator-install.yml

# Start the Fabric Operations Console with the Ansible Blockchain Collection
ansible-console:
    #!/bin/bash
    set -ex -o pipefail

    export EXTRAS=""
    if [ -f "/_cfg/k8s_context.yaml" ]; then
        export EXTRAS=" -e KUBECONFIG=/_cfg/k8s_context.yaml"
    fi

    docker run \
        --rm \
        -v ${HOME}/.kube/:/home/ibp-user/.kube/ \
        -v $(pwd)/infrastructure/operator_console_playbooks:/playbooks  ${EXTRAS} \
        -v ${CWDIR}/_cfg:/_cfg \
        --network=host \
        {{ansible_image}} \
            ansible-playbook /playbooks/02-console-install.yml

ansible-auth:
    #!/bin/bash
    set -ex -o pipefail

    AUTH=$(curl -X POST https://{{namespace}}-hlf-console-console.{{ingress_domain}}:443/ak/api/v2/permissions/keys -u admin:password -k -H 'Content-Type: application/json' -d '{"roles": ["writer", "manager"],"description": "newkey"}')
    KEY=$(echo $AUTH | jq .api_key | tr -d '"')
    SECRET=$(echo $AUTH | jq .api_secret | tr -d '"')

    echo "Writing authentication file for Ansible based IBP (Software) network building"
    cat << EOF > $CWDIR/_cfg/auth-vars.yml
    api_key: $KEY
    api_endpoint: https://{{namespace}}-hlf-console-console.{{ingress_domain}}/
    api_authtype: basic
    api_secret: $SECRET
    EOF
    cat ${CWDIR}/_cfg/auth-vars.yml


# Build a sample Fabric network with the Ansible Blockchain Collection
ansible-network: ansible-auth
    #!/bin/bash
    set -ex -o pipefail

    export EXTRAS=""
    if [ -f "/_cfg/k8s_context.yaml" ]; then
        export EXTRAS=" -e KUBECONFIG=/_cfg/k8s_context.yaml"
    fi

    docker run \
        --rm \
        -u $(id -u) \
        -v ${HOME}/.kube/:/home/ibp-user/.kube/ \
        -v ${CWDIR}/infrastructure/fabric_network_playbooks:/playbooks  ${EXTRAS} \
        -v ${CWDIR}/_cfg:/_cfg \
        --network=host \
        {{ansible_image}} \
            ansible-playbook /playbooks/00-complete.yml


# Bring down the sample network created with the Ansible Blockchain Collection
ansible-network-down:
    #!/bin/bash
    set -ex -o pipefail

    kubectl delete namespace {{ namespace }} --ignore-not-found


# Build a chaincode package with Ansible Blockchain Collection
ansible-build-chaincode:
    #!/bin/bash
    set -ex -o pipefail
    pushd ${CWDIR}/contracts/asset-transfer-typescript

    if [ "{{cluster_runtime}}" = "openshift" ]; then
        export IMAGE_NAME="{{namespace}}/{{chaincode_name}}"
    else
        export IMAGE_NAME="{{chaincode_name}}"
    fi
    DOCKER_BUILDKIT=1 docker build -t {{external_repo_endpoint}}/${IMAGE_NAME} . --target k8s
    docker push {{external_repo_endpoint}}/${IMAGE_NAME}

    # note the double { } for escaping
    export IMG_SHA=$(docker inspect --format='{{{{index .RepoDigests 0}}' {{external_repo_endpoint}}/${IMAGE_NAME} | cut -d'@' -f2)
    weft chaincode package k8s --name {{internal_repo_endpoint}}/${IMAGE_NAME} --digest ${IMG_SHA} --label {{chaincode_name}}
    mv {{chaincode_name}}.tgz ${CWDIR}/_cfg
    popd


# Deploy a chaincode package with the Ansible Blockchain Collection
ansible-deploy-chaincode:
    #!/bin/bash
    set -ex -o pipefail

    export EXTRAS=""
    if [ -f "/_cfg/k8s_context.yaml" ]; then
        export EXTRAS=" -e KUBECONFIG=/_cfg/k8s_context.yaml"
    fi

    # cp ${CWDIR}/contracts/asset-transfer-typescript/asset-transfer-chaincode-vars.yml ${CWDIR}/_cfg
    docker run \
        --rm \
        -u $(id -u) \
        -v ${HOME}/.kube/:/home/ibp-user/.kube/ \
        -v ${CWDIR}/infrastructure/production_chaincode_playbooks:/playbooks ${EXTRAS} \
        -v ${CWDIR}/_cfg:/_cfg \
        --network=host \
        {{ansible_image}} \
            ansible-playbook /playbooks/19-install-and-approve-chaincode.yml

    docker run \
        --rm \
        -u $(id -u) \
        -v ${HOME}/.kube/:/home/ibp-user/.kube/ \
        -v ${CWDIR}/infrastructure/production_chaincode_playbooks:/playbooks ${EXTRAS} \
        -v ${CWDIR}/_cfg:/_cfg \
        --network=host \
        {{ansible_image}} \
            ansible-playbook /playbooks/20-install-and-approve-chaincode.yml

    docker run \
        --rm \
        -u $(id -u) \
        -v ${HOME}/.kube/:/home/ibp-user/.kube/ \
        -v ${CWDIR}/infrastructure/production_chaincode_playbooks:/playbooks ${EXTRAS} \
        -v ${CWDIR}/_cfg:/_cfg \
        --network=host \
        {{ansible_image}} \
            ansible-playbook /playbooks/21-commit-chaincode.yml

# Creates a new identity for an application to use
ansible-ready-application:
    #!/bin/bash
    set -ex -o pipefail

    export EXTRAS=""
    if [ -f "/_cfg/k8s_context.yaml" ]; then
        export EXTRAS=" -e KUBECONFIG=/_cfg/k8s_context.yaml"
    fi

    docker run \
        --rm \
        -u $(id -u) \
        -v ${HOME}/.kube/:/home/ibp-user/.kube/ \
        -v ${CWDIR}/infrastructure/production_chaincode_playbooks:/playbooks ${EXTRAS} \
        -v ${CWDIR}/_cfg:/_cfg \
        --network=host \
        {{ansible_image}} \
            ansible-playbook /playbooks/22-register-application.yml
