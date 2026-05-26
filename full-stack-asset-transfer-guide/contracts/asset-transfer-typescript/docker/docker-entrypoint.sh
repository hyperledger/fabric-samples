#!/usr/bin/env bash
#
# SPDX-License-Identifier: Apache-2.0
#
set -euo pipefail
: ${CORE_PEER_TLS_ENABLED:="false"}
: ${DEBUG:="false"}

if [ "${DEBUG,,}" = "true" ]; then
  npm run start:server-debug

elif [[ ! -v CHAINCODE_SERVER_ADDRESS ]]; then
  npm start -- --peer.address $CORE_PEER_ADDRESS

elif [ "${CORE_PEER_TLS_ENABLED,,}" = "true" ]; then
   npm run start:server

else
   npm run start:server-nontls
fi

