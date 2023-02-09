#!/usr/bin/env bash
#
# Copyright contributors to the Hyperledgendary Kubernetes Test Network project
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
set -euo pipefail
. scripts/utils.sh


#
# Bind all org0 services to the "org0" namespace
#
export NAMESPACE=org0


#
# CA
#
print "starting org0 CA"

apply_template organizations/org0/org0-ca.yaml
sleep 5
wait_for ibpca ca

# Retrieve the org CA certificate for the bootstrap enrollment of peers/orderers.
# This value will be substituted from the environment into the node CRDs.
export CA_CERT=$(connection_profile_cert ca .tls.cert)


#
# Network nodes
#
print "starting org0 orderers"
apply_template organizations/org0/org0-orderer.yaml
sleep 5

wait_for ibporderer orderernode1
wait_for ibporderer orderernode2
wait_for ibporderer orderernode3

print "starting org0 peers"

