#!/bin/bash
#
# Copyright contributors to the Hyperledger Fabric Operator project
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

function console_up() {

  init_namespace

  apply_operator
  wait_for_deployment fabric-operator

  apply_console
  wait_for_deployment hlf-console

  local console_hostname=${NS}-hlf-console-console
  local console_url="https://${console_hostname}.${CONSOLE_DOMAIN}"

  log ""
  log "The Fabric Operations Console is ready."
  log "open ${console_url}"
  log "(self-signed cert - log in as ${CONSOLE_USERNAME}:${CONSOLE_PASSWORD})"

  # TODO: prepare an FoC bulk JSON import for the test network assets
  #  log "Log into Console and import the asset archive at build/console/console_assets.zip"
}

function apply_console() {
  push_fn "Applying Fabric Operations Console"

  apply_kustomization config/console

  sleep 5

  pop_fn
}