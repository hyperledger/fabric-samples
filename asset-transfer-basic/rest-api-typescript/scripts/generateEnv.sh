#!/usr/bin/env bash

#
# SPDX-License-Identifier: Apache-2.0
#

: "${TEST_NETWORK_HOME:=../..}"
: "${CONNECTION_PROFILE_FILE:=${TEST_NETWORK_HOME}/organizations/peerOrganizations/org1.example.com/connection-org1.json}"
: "${CERTIFICATE_FILE:=${TEST_NETWORK_HOME}/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/signcerts/Admin@org1.example.com-cert.pem}"
: "${PRIVATE_KEY_FILE:=${TEST_NETWORK_HOME}/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/keystore/priv_sk}"

cat << ENV_END > .env
LOG_LEVEL=info

PORT=3000

RETRY_DELAY=3000

HLF_CONNECTION_PROFILE=$(cat ${CONNECTION_PROFILE_FILE} | jq -c .)

HLF_CERTIFICATE="$(cat ${CERTIFICATE_FILE} | sed -e 's/$/\\n/' | tr -d '\r\n')"

HLF_PRIVATE_KEY="$(cat ${PRIVATE_KEY_FILE} | sed -e 's/$/\\n/' | tr -d '\r\n')"

HLF_COMMIT_TIMEOUT=3000

HLF_ENDORSE_TIMEOUT=30

REDIS_HOST=localhost

REDIS_PORT=6379

ORG1_APIKEY=$(uuidgen)

#REDIS_USERNAME=

#REDIS_PASSWORD=

ENV_END
