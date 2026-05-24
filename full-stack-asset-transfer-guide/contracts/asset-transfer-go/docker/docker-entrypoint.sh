#!/usr/bin/env bash

set -euo pipefail

: "${CORE_PEER_TLS_ENABLED:=false}"

if [[ ! -v CHAINCODE_SERVER_ADDRESS ]]; then
    # Legacy peer-managed mode: binary acts as a regular chaincode process.
    exec ./chaincode --peer.address "${CORE_PEER_ADDRESS}"

elif [[ "${CORE_PEER_TLS_ENABLED,,}" == "true" ]]; then
    # CaaS + TLS: fabric-chaincode-go/v2 reads CHAINCODE_SERVER_ADDRESS,
    # CORE_CHAINCODE_ID_NAME, and TLS vars directly as env vars.
    exec env \
        CORE_CHAINCODE_ID_NAME="${CHAINCODE_ID}" \
        CHAINCODE_SERVER_ADDRESS="${CHAINCODE_SERVER_ADDRESS}" \
        CORE_PEER_TLS_ENABLED=true \
        CORE_PEER_TLS_ROOTCERT_FILE="${CHAINCODE_TLS_KEY:-/hyperledger/privatekey.pem}" \
        CORE_TLS_CLIENT_KEY_FILE="${CHAINCODE_TLS_CERT:-/hyperledger/cert.pem}" \
        CORE_TLS_CLIENT_CERT_FILE="${CHAINCODE_TLS_CLIENT_CACERT:-/hyperledger/rootcert.pem}" \
        ./chaincode

else
    # CaaS without TLS: fabric-chaincode-go/v2 uses CHAINCODE_SERVER_ADDRESS
    # and CORE_CHAINCODE_ID_NAME env vars to start the gRPC server.
    exec env \
        CORE_CHAINCODE_ID_NAME="${CHAINCODE_ID}" \
        CHAINCODE_SERVER_ADDRESS="${CHAINCODE_SERVER_ADDRESS}" \
        ./chaincode
fi
