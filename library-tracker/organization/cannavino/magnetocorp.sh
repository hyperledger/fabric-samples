#!/bin/bash
#
# SPDX-License-Identifier: Apache-2.0
shopt -s extglob

function _exit(){
    printf "Exiting:%s\n" "$1"
    exit -1
}


: ${CHANNEL_NAME:="mychannel"}
: ${DELAY:="3"}
: ${MAX_RETRY:="5"}
: ${VERBOSE:="false"}

# Where am I?
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Locate the test-network 
cd "${DIR}/../../../test-network"
env | sort > /tmp/env.orig

OVERRIDE_ORG="2"
. ./scripts/envVar.sh


parsePeerConnectionParameters 1 2
export PEER_PARMS="${PEER_CONN_PARMS##*( )}"

# set the fabric config path
export FABRIC_CFG_PATH="${DIR}/../../../config"
export PATH="${DIR}/../../../bin:${PWD}:$PATH"

env | sort | comm -1 -3 /tmp/env.orig - | sed -E 's/(.*)=(.*)/export \1="\2"/'
rm /tmp/env.orig

cd ${DIR}