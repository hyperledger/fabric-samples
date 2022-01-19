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
    echo -ne "\r✅"  >> ${LOG_FILE}

  elif [ $res -eq 1 ]; then
    echo -ne "\r⚠️" >> ${LOG_FILE}

  elif [ $res -eq 2 ]; then
    echo -ne "\r☠️" >> ${LOG_FILE}

  elif [ $res -eq 127 ]; then
    echo -ne "\r☠️" >> ${LOG_FILE}

  else
    echo -ne "\r" >> ${LOG_FILE}
  fi

  echo "" >> ${LOG_FILE}
}

