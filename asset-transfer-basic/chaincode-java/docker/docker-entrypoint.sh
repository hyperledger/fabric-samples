#!/usr/bin/env bash
#
# SPDX-License-Identifier: Apache-2.0
#
set -euo pipefail
: ${CORE_PEER_TLS_ENABLED:="false"}
: ${DEBUG:="false"}

if [ "${DEBUG,,}" = "true" ]; then
   java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:8000 -jar /chaincode.jar
elif [ "${CORE_PEER_TLS_ENABLED,,}" = "true" ]; then
   java -jar /chaincode.jar # todo
else
   java -jar /chaincode.jar
fi

