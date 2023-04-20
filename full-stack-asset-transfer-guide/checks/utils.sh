#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

SUCCESS="✅"
WARN="⚠️ "

# tests if varname is defined in the env AND it's an existing directory
function must_declare() {
  local varname=$1

  if [[ ${!varname+x} ]]
  then
    printf "%s %-40s%s\n" $SUCCESS $varname ${!varname}
  else
    printf "%s %-40s\n" ${WARN} $varname
    EXIT=1
  fi
}


function check() {
  local name=$1
  local message=$2

  printf "🤔 %s" $name

  if $name &>/dev/null ; then
    printf "\r%s %-40s" $SUCCESS $name
  else
    printf "\r%s  %-40s" $WARN $name
    EXIT=1
  fi

  echo $message
}