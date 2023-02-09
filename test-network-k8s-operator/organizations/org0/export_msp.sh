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

print "Exporting org0 channel MSP"

#
# Prepare a folder structure containing the organization's MSP certificates
# necessary to join the consortium.
#
ORG_DIR=channel-config/organizations/ordererOrganizations/org0.localho.st

write_pem ca .ca.signcerts $ORG_DIR/msp/cacerts/ca-signcert.pem
write_pem ca .tlsca.signcerts $ORG_DIR/msp/tlscacerts/tlsca-signcert.pem
write_msp_config ca ca-signcert.pem $ORG_DIR/msp


#
# Extract the orderer TLS certificates.  These will be used by osnadmin for
# TLS connections to the orderers when joining orgs to a channel.
#
write_pem orderernode1 .tls.signcerts $ORG_DIR/orderers/orderernode1/tls/signcerts/tls-cert.pem
write_pem orderernode2 .tls.signcerts $ORG_DIR/orderers/orderernode2/tls/signcerts/tls-cert.pem
write_pem orderernode3 .tls.signcerts $ORG_DIR/orderers/orderernode3/tls/signcerts/tls-cert.pem