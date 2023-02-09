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
# Save all of the organization enrollments in a local folder.
#
ENROLLMENTS_DIR=${PWD}/organizations/org0/enrollments

#
# Before we can work with the CA, extract the CA's TLS certificate and
# store in .pem format for access with client utilities.
#
write_pem ca .tls.cert $ENROLLMENTS_DIR/ca-tls-cert.pem

# Enroll the org0 admin user.  Registration is performed by the operator according
# to entries in the org0 ca CRD.
enroll org0 org0admin org0adminpw

# When connecting to the orderers, the channel admin API requires that the HTTP client
# presents a TLS certificate that has been signed by the organization's TLS CA.
enroll_tls org0 org0admin org0adminpw
