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
#
###############################################################################

# Ensure all properties are exported as shell env-vars
set export

# Use environment variables from the (git-ignored and hidden) .env files
set dotenv-load

# set the current directory, and the location of the test dats
CWDIR := justfile_directory()

_default:
  @just -f {{justfile()}} --list

# Run the check script to validate third party dependencies
check:
  ${CWDIR}/scripts/check.sh


###############################################################################
# Environment and just parameters
###############################################################################

CLUSTER_NAME        := env_var_or_default("TEST_NETWORK_CLUSTER_NAME",      "kind")
NAMESPACE           := env_var_or_default("TEST_NETWORK_NAMESPACE",         "test-network")
OPERATOR_IMAGE      := env_var_or_default("TEST_NETWORK_OPERATOR_IMAGE",    "ghcr.io/hyperledger-labs/fabric-operator:1.0")
FABRIC_VERSION      := env_var_or_default("TEST_NETWORK_FABRIC_VERSION",    "2.5.0-beta")
FABRIC_CA_VERSION   := env_var_or_default("TEST_NETWORK_FABRIC_CA_VERSION", "1.5.6-beta3")
CA_IMAGE            := env_var_or_default("TEST_NETWORK_CA_IMAGE",          "hyperledger/fabric-ca")
CA_IMAGE_TAG        := env_var_or_default("TEST_NETWORK_CA_IMAGE_TAG",      FABRIC_CA_VERSION)
PEER_IMAGE          := env_var_or_default("TEST_NETWORK_PEER_IMAGE",        "ghcr.io/hyperledger-labs/k8s-fabric-peer")
PEER_IMAGE_TAG      := env_var_or_default("TEST_NETWORK_PEER_IMAGE_TAG",    "v0.8.0")
ORDERER_IMAGE       := env_var_or_default("TEST_NETWORK_ORDERER_IMAGE",     "hyperledger/fabric-orderer")
ORDERER_IMAGE_TAG   := env_var_or_default("TEST_NETWORK_ORDERER_IMAGE_TAG", FABRIC_VERSION)
CHANNEL_NAME        := env_var_or_default("TEST_NETWORK_CHANNEL_NAME",      "mychannel")
CHAINCODE_NAME      := env_var_or_default("TEST_NETWORK_CHAINCODE_NAME",    "asset-transfer")
CHAINCODE_VERSION   := env_var_or_default("TEST_NETWORK_CHAINCODE_VERSION", "v0.1.4")
CHAINCODE_SEQUENCE  := env_var_or_default("TEST_NETWORK_CHAINCODE_SEQUENCE","1")
CHAINCODE_PKG_NAME  := env_var_or_default("TEST_NETWORK_CHAINCODE_PKG_NAME","asset-transfer-typescript-v0.1.4.tgz")
CHAINCODE_PKG_URL   := env_var_or_default("TEST_NETWORK_CHAINCODE_PKG_URL", "https://github.com/hyperledgendary/full-stack-asset-transfer-guide/releases/download/v0.1.4/" + CHAINCODE_PKG_NAME)


###############################################################################
# KIND / k8s targets
###############################################################################

# Start a local KIND cluster with nginx ingress
kind: check unkind
    scripts/kind_with_nginx.sh {{CLUSTER_NAME}}

# Shut down the KIND cluster
unkind:
    #!/usr/bin/env bash
    kind delete cluster --name {{CLUSTER_NAME}}

    if docker inspect kind-registry &>/dev/null; then
        echo "Stopping container registry"
        docker kill kind-registry
        docker rm kind-registry
    fi


###############################################################################
# TL/DR actions.  These don't exist, other than for convenience to run the
# entire flow without splitting across multiple "org" terminals.
###############################################################################

start-network:
    just start org0
    just start org1
    just start org2

# Shut down the test network and remove all certificates
destroy:
    #!/usr/bin/env bash
    rm -rf organizations/org0/enrollments && echo "org0 enrollments deleted"
    rm -rf organizations/org1/enrollments && echo "org1 enrollments deleted"
    rm -rf organizations/org2/enrollments && echo "org2 enrollments deleted"
    rm -rf organizations/org0/chaincode   && echo "org0 chaincode packages deleted"
    rm -rf organizations/org1/chaincode   && echo "org1 chaincode packages deleted"
    rm -rf organizations/org2/chaincode   && echo "org2 chaincode packages deleted"

    rm -rf channel-config/organizations   && echo "consortium MSP deleted"
    rm channel-config/{{CHANNEL_NAME}}_genesis_block.pb && echo {{CHANNEL_NAME}} " genesis block deleted"

    kubectl delete ns org0 --ignore-not-found=true
    kubectl delete ns org1 --ignore-not-found=true
    kubectl delete ns org2 --ignore-not-found=true

# Check that all network services are running
check-network:
    scripts/check-network.sh


###############################################################################
# Test Network
###############################################################################

# Create the org namespace and start the operator for an org
init org:
    #!/usr/bin/env bash
    export NAMESPACE={{org}} # todo: move to an org directory?
    scripts/start_operator.sh

# Start the nodes for an org
start org: (init org)
    organizations/{{org}}/start.sh

# todo: clear enrollments, cc packages, etc.
# Stop the nodes for an org
stop org:
    kubectl delete ns {{org}} --ignore-not-found=true

# todo: + dependency (start org)?
# Enroll the users for an org
enroll org:
    organizations/{{org}}/enroll.sh


###############################################################################
# Channel Construction
###############################################################################

# Create the channel genesis block
create-genesis-block: check-network gather-msp
    channel-config/create_genesis_block.sh

# todo: include this?  Which org is running the target?
# Export the MSP certificates for all orgs
gather-msp:
    just export-msp org0
    just export-msp org1
    just export-msp org2

# Export org MSP certificates to the consortium organizer
export-msp org:
    organizations/{{org}}/export_msp.sh

# inspect the genesis block
inspect-genesis-block:
    #!/usr/bin/env bash
    configtxgen -inspectBlock channel-config/mychannel_genesis_block.pb | jq

# Join an org to the channel
join org:
    organizations/{{org}}/join_channel.sh


###############################################################################
# Chaincode and Gateway Appplication Development
###############################################################################

# Install a smart contract on all peers in an org
install-cc org:
    organizations/{{org}}/install_chaincode.sh

# Display env for targeting a peer with the Fabric binaries
show-context msp org peer:
    #!/usr/bin/env bash
    . {{CWDIR}}/scripts/utils.sh
    appear_as {{msp}} {{org}} {{peer}}

    # use export to load the peer context into the current environment:
    # export $(just show-context Org1MSP org1 peer1)
    printenv | egrep "CORE_PEER|FABRIC_|ORDERER_" | sort
