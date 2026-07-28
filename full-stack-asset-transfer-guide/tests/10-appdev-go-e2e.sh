#!/usr/bin/env bash

set -v -euo pipefail

# All tests run in the workshop root folder
cd "$(dirname "$0")"/..

export WORKSHOP_PATH="${PWD}"
export PATH="${WORKSHOP_PATH}/bin:${PATH}"
export FABRIC_CFG_PATH="${WORKSHOP_PATH}/config"

"${WORKSHOP_PATH}/check.sh"

CHAINCODE_PID=

function exitHook() {

  # Shut down the Go chaincode process
  [ -n "${CHAINCODE_PID}" ] && kill "${CHAINCODE_PID}" 2>/dev/null || true

  # Shut down Microfab
  docker kill microfab &>/dev/null || true

  # Delete the network configuration and crypto material
  rm -rf "${WORKSHOP_PATH}/_cfg"
}

trap exitHook SIGINT SIGTERM EXIT

#
# Start Microfab
#
just microfab

#
# Configure the environment
#
source "${WORKSHOP_PATH}/_cfg/uf/org1admin.env"

just debugcc


cd "${WORKSHOP_PATH}/contracts/asset-transfer-go"

CORE_CHAINCODE_ID_NAME="${CHAINCODE_ID}" \
CORE_CHAINCODE_SERVER_ADDRESS="${CHAINCODE_SERVER_ADDRESS}" \
go run ./src &

CHAINCODE_PID=$!

sleep 5


cd "${WORKSHOP_PATH}/applications/trader-typescript"

export ENDPOINT=org1peer-api.127-0-0-1.nip.io:8080
export MSP_ID=org1MSP
export CERTIFICATE=../../_cfg/uf/_msp/org1/org1admin/msp/signcerts/cert.pem
export PRIVATE_KEY=../../_cfg/uf/_msp/org1/org1admin/msp/keystore/cert_sk

npm install

npm start getAllAssets
npm start transact
npm start getAllAssets
npm start create banana bananaman yellow
npm start read banana
npm start delete banana
SIMULATED_FAILURE_COUNT=2 npm start listen