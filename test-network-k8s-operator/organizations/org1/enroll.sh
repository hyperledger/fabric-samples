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
# Bind all org1 services to the "org1" namespace
#
export NAMESPACE=org1

#
# Save all of the organization enrollments in a local folder.
#
ENROLLMENTS_DIR=${PWD}/organizations/org1/enrollments

#
# Before we can work with the CA, extract the CA's TLS certificate and
# store in .pem format for access with client utilities.
#
write_pem ca .tls.cert $ENROLLMENTS_DIR/ca-tls-cert.pem

# Enroll the org1 admin user.  Registration is performed by the operator according
# to entries in the org2-ca CRD.
enroll org1 org1admin org1adminpw

# create an msp config.yaml to indicate the user is an admin for the org
CA_CERT_NAME=org1-ca-ca-org1-localho-st-ca.pem
write_msp_config ca $CA_CERT_NAME $ENROLLMENTS_DIR/org1admin/msp

# Enroll the root CA administrator such that users can later be registered and enrolled for
# identities of transactions submitted to the ledger.
enroll org1 rcaadmin rcaadminpw

# Enroll a client user for submitting transactions through a gateway
# cliant application.  This user has been registered at the CA in the
# bootstrap registrations by the operator.
enroll org1 org1user org1userpw