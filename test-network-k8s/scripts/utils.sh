#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
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

# Apply the current environment to a k8s template and apply to the cluster.
function apply_template() {

  echo "Applying template $1:"
  cat $1 | envsubst

  cat $1 | envsubst | kubectl -n $2 apply -f -
}

# Set the calling context to refer the peer binary to the correct org / peer instance
#
# todo: Expose the output of this function to a target that prints the context to STDOUT.
#
# e.g.:
# bash $ source $(network set-peer-context org1 peer2)
# bash $ peer chaincode list
# bash $ ...
function export_peer_context() {
  local org=$1
  local peer=$2

  export FABRIC_CFG_PATH=${PWD}/config/${org}
  export CORE_PEER_ADDRESS=${org}-${peer}.${DOMAIN}:${NGINX_HTTPS_PORT}
  export CORE_PEER_MSPCONFIGPATH=${TEMP_DIR}/enrollments/${org}/users/${org}admin/msp
  export CORE_PEER_TLS_ROOTCERT_FILE=${TEMP_DIR}/channel-msp/peerOrganizations/${org}/msp/tlscacerts/tlsca-signcert.pem
}

function absolute_path() {
  local relative_path=$1

  local abspath="$( cd "${relative_path}" && pwd )"

  echo $abspath
}
