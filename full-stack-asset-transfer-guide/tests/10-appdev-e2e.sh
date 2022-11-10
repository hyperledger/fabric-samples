#!/usr/bin/env bash

set -v -eou pipefail

# All tests run in the workshop root folder
cd "$(dirname "$0")"/..

export WORKSHOP_PATH="${PWD}"
export PATH="${WORKSHOP_PATH}/bin:${PATH}"
export FABRIC_CFG_PATH="${WORKSHOP_PATH}/config"

"${WORKSHOP_PATH}/check.sh"

CHAINDODE_PID=

function exitHook() {

  # shut down the npm run
  [ -n "${CHAINCODE_PID}" ] && kill "${CHAINCODE_PID}"

  # and node children spawned by npm.  This could be improved by scraping out the pid for the target node command.
  [ -n "${CHAINCODE_PID}" ] && killall node

  # Shut down microfab
  docker kill microfab &> /dev/null

  # Delete the network configuration and crypto material
  rm -rf "${WORKSHOP_PATH}"/_cfg
}

trap exitHook SIGINT SIGTERM EXIT

just microfab

source "${WORKSHOP_PATH}/_cfg/uf/org1admin.env"
just debugcc

source "${WORKSHOP_PATH}/_cfg/uf/org1admin.env"
cd "${WORKSHOP_PATH}/contracts/asset-transfer-typescript"
npm install
npm run build
npm run start:server-nontls &
CHAINCODE_PID=$!

sleep 5

cd "${WORKSHOP_PATH}/applications/trader-typescript"
export ENDPOINT=org1peer-api.127-0-0-1.nip.io:8080
export MSP_ID=org1MSP
export CERTIFICATE=../../_cfg/uf/_msp/org1/org1admin/msp/signcerts/org1admin.pem
export PRIVATE_KEY=../../_cfg/uf/_msp/org1/org1admin/msp/keystore/cert_sk
npm install
npm start getAllAssets
npm start transact
npm start getAllAssets
npm start create banana bananaman yellow
npm start read banana
npm start delete banana
SIMULATED_FAILURE_COUNT=2 npm start listen
