#!/usr/bin/env bash

set -euo pipefail

: "${CORE_PEER_TLS_ENABLED:=false}"

if [[ ! -v CHAINCODE_SERVER_ADDRESS ]]; then
    # Legacy peer-managed mode: binary acts as a regular chaincode process.
    exec ./chaincode --peer.address "${CORE_PEER_ADDRESS}"

elif [[ "${CORE_PEER_TLS_ENABLED,,}" == "true" ]]; then
    # CaaS + TLS
    exec ./chaincode \
        --chaincode.address "${CHAINCODE_SERVER_ADDRESS}" \
        --chaincode.id     "${CHAINCODE_ID}" \
        --chaincode.tls.enabled true \
        --chaincode.tls.key.file    "${CHAINCODE_TLS_KEY:-/hyperledger/privatekey.pem}" \
        --chaincode.tls.cert.file   "${CHAINCODE_TLS_CERT:-/hyperledger/cert.pem}" \
        --chaincode.tls.clientCaCert.file "${CHAINCODE_TLS_CLIENT_CACERT:-/hyperledger/rootcert.pem}"

else
    # CaaS without TLS
    exec ./chaincode \
        --chaincode.address "${CHAINCODE_SERVER_ADDRESS}" \
        --chaincode.id      "${CHAINCODE_ID}"
fi
