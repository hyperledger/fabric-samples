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


function logging_init() {
  # Reset the output and debug log files
  printf '' > ${LOG_FILE} > ${DEBUG_FILE}

  # Write all output to the control flow log to STDOUT
  tail -f ${LOG_FILE} &

  # Call the exit handler when we exit.
  trap "exit_fn" EXIT

  # Send stdout and stderr from child programs to the debug log file
  exec 1>>${DEBUG_FILE} 2>>${DEBUG_FILE}

  # There can be a race between the tail starting and the next log statement
  sleep 0.5
}

function exit_fn() {
  rc=$?
  set +x

  # Write an error icon to the current logging statement.
  if [ "0" -ne $rc ]; then
    pop_fn $rc
  fi

  # always remove the log trailer when the process exits.
  pkill -P $$
}

function push_fn() {
  #echo -ne "   - entering ${FUNCNAME[1]} with arguments $@"

  echo -ne "   - $@ ..." >> ${LOG_FILE}
}

function log() {
  echo -e $@ >> ${LOG_FILE}
}

function pop_fn() {
#  echo exiting ${FUNCNAME[1]}

  if [ $# -eq 0 ]; then
    echo -ne "\r✅"  >> ${LOG_FILE}
    echo "" >> ${LOG_FILE}
    return
  fi

  local res=$1
  if [ $res -eq 0 ]; then
    echo -ne "\r✅\n"  >> ${LOG_FILE}

  elif [ $res -eq 1 ]; then
    echo -ne "\r⚠️\n" >> ${LOG_FILE}

  elif [ $res -eq 2 ]; then
    echo -ne "\r☠️\n" >> ${LOG_FILE}

  elif [ $res -eq 127 ]; then
    echo -ne "\r☠️\n" >> ${LOG_FILE}

  else
    echo -ne "\r\n" >> ${LOG_FILE}
  fi

  if [ $res -ne 0 ]; then
    tail -${LOG_ERROR_LINES} network-debug.log >> ${LOG_FILE}
  fi

  #echo "" >> ${LOG_FILE}
}

function wait_for_deployment() {
  local name=$1
  push_fn "Waiting for deployment $name"

  kubectl -n $NS rollout status deploy $name

  pop_fn
}

function absolute_path() {
  local relative_path=$1

  local abspath="$( cd "${relative_path}" && pwd )"

  echo $abspath
}

function apply_kustomization() {
  $KUSTOMIZE_BUILD $1 | envsubst | kubectl -n $NS apply -f -
}

function undo_kustomization() {
  $KUSTOMIZE_BUILD $1 | envsubst | kubectl -n $NS delete --ignore-not-found=true -f -
}

function create_image_pull_secret() {
  local secret=$1
  local registry=$2
  local username=$3
  local password=$4

  push_fn "Creating $secret for access to $registry"

  kubectl -n $NS delete secret $secret --ignore-not-found

  # todo: can this be moved to a kustomization overlay?
  kubectl -n $NS \
    create secret docker-registry \
    $secret \
    --docker-server="$registry" \
    --docker-username="$username" \
    --docker-password="$password"

  pop_fn
}

function export_peer_context() {
  local orgnum=$1
  local peernum=$2
  local org=org${orgnum}
  local peer=peer${peernum}

#  export FABRIC_LOGGING_SPEC=DEBUG

  export FABRIC_CFG_PATH=${PWD}/config
  export CORE_PEER_ADDRESS=${NS}-${org}-${peer}-peer.${INGRESS_DOMAIN}:443
  export CORE_PEER_LOCALMSPID=Org${orgnum}MSP
  export CORE_PEER_TLS_ENABLED=true
  export CORE_PEER_MSPCONFIGPATH=${TEMP_DIR}/enrollments/${org}/users/${org}admin/msp
  export CORE_PEER_TLS_ROOTCERT_FILE=${TEMP_DIR}/channel-msp/peerOrganizations/${org}/msp/tlscacerts/tlsca-signcert.pem
  export CORE_PEER_CLIENT_CONNTIMEOUT=15s
  export CORE_PEER_DELIVERYCLIENT_CONNTIMEOUT=15s

#  export | egrep "CORE_|FABRIC_"
}
