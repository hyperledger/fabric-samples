#!/usr/bin/env bash
set -eo pipefail
# script directory
SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
# define the CA setup
CA_HOST=localhost
CA_URL="${CA_HOST}:7054"

TLS_CERT="${SCRIPT_DIR}/../../test-network/organizations/fabric-ca/org1/tls-cert.pem"

export SOFTHSM2_CONF="${SOFTHSM2_CONF:-${HOME}/softhsm2.conf}"

LocateHsmLib() {
  local POSSIBLE_LIB_LOC=( \
    '/usr/lib/softhsm/libsofthsm2.so' \
    '/usr/lib/x86_64-linux-gnu/softhsm/libsofthsm2.so' \
    '/usr/local/lib/softhsm/libsofthsm2.so' \
    '/usr/lib/libacsp-pkcs11.so' \
    '/opt/homebrew/lib/softhsm/libsofthsm2.so' \
  )
  for TEST_LIB in "${POSSIBLE_LIB_LOC[@]}"; do
    if [ -f "${TEST_LIB}" ]; then
      echo "${TEST_LIB}"
      return
    fi
  done
}

HSM2_LIB="${PKCS11_LIB:-$(LocateHsmLib)}"
[ -z "${HSM2_LIB}" ] && echo No SoftHSM PKCS11 Library found, ensure you have installed softhsm2 && exit 1

# create a softhsm2.conf file if one doesn't exist
if [ ! -f "${SOFTHSM2_CONF}" ]; then
  TMPDIR="${TMPDIR:-/tmp}"
  mkdir -p "${TMPDIR}/softhsm"
  echo "directories.tokendir = ${TMPDIR}/softhsm" > "${SOFTHSM2_CONF}"
fi

softhsm2-util --init-token --slot 0 --label 'ForFabric' --pin 98765432 --so-pin 1234 || true

# Update the client config file to point to the softhsm pkcs11 library
# which must be in $HOME/softhsm directory

CLIENT_CONFIG_TEMPLATE="${SCRIPT_DIR}/../ca-client-config/fabric-ca-client-config-template.yaml"
CLIENT_CONFIG="${SCRIPT_DIR}/../ca-client-config/fabric-ca-client-config.yaml"

CLIENT_CONFIG_CONTENT="$( sed "s+REPLACE_ME_HSMLIB+${HSM2_LIB}+g" "${CLIENT_CONFIG_TEMPLATE}" )"
echo "${CLIENT_CONFIG_CONTENT}" > "${CLIENT_CONFIG}"

# create the users, remove any existing users
CRYPTO_PATH="${SCRIPT_DIR}/../crypto-material/hsm"
[ -d "${CRYPTO_PATH}" ] && rm -fr "${CRYPTO_PATH}"

# user passed in as parameter
CAADMIN="admin"
CAADMIN_PW="adminpw"
HSMUSER="$1"

fabric-ca-client enroll \
  -c "${CLIENT_CONFIG}" \
  -u "https://${CAADMIN}:${CAADMIN_PW}@${CA_URL}" \
  --mspdir "${CRYPTO_PATH}/${CAADMIN}" \
  --tls.certfiles "${TLS_CERT}"

! fabric-ca-client register \
  -c "${CLIENT_CONFIG}" \
  --mspdir "${CRYPTO_PATH}/${CAADMIN}" \
  --id.name "${HSMUSER}" \
  --id.secret "${HSMUSER}" \
  --id.type client \
  --caname ca-org1 \
  --id.maxenrollments 0 \
  -m example.com \
  -u "https://${CA_URL}" \
  --tls.certfiles "${TLS_CERT}" \
  && echo user probably already registered, continuing

fabric-ca-client enroll \
  -c "${CLIENT_CONFIG}" \
  -u "https://${HSMUSER}:${HSMUSER}@${CA_URL}" \
  --mspdir "${CRYPTO_PATH}/${HSMUSER}" \
  --tls.certfiles "${TLS_CERT}"
