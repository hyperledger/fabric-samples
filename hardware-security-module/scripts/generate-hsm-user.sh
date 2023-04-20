#!/usr/bin/env bash
set -eo pipefail
# script directory
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
# define the CA setup
CA_HOST=localhost
CA_URL=${CA_HOST}:7054

TLS_CERT="${SCRIPT_DIR}/../../test-network/organizations/fabric-ca/org1/tls-cert.pem"

LocateHsmLib() {
  if [[ -n "${PKCS11_LIB}" && -f "${PKCS11_LIB}" ]]; then
    echo "${PKCS11_LIB}"
    return
  fi

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

HSM2_LIB=$(LocateHsmLib)
[ -z "$HSM2_LIB" ] && echo No SoftHSM PKCS11 Library found, ensure you have installed softhsm2 && exit 1

# create a softhsm2.conf file if one doesn't exist
HSM2_CONF=$HOME/softhsm2.conf
[ ! -f "$HSM2_CONF" ] && echo directories.tokendir = /tmp > "$HSM2_CONF"

# Update the client config file to point to the softhsm pkcs11 library
# which must be in $HOME/softhsm directory

CLIENT_CONFIG_TEMPLATE=${SCRIPT_DIR}/../ca-client-config/fabric-ca-client-config-template.yaml
CLIENT_CONFIG=${SCRIPT_DIR}/../ca-client-config/fabric-ca-client-config.yaml
cp $CLIENT_CONFIG_TEMPLATE $CLIENT_CONFIG

if [[ "$OSTYPE" == "darwin"* ]]; then
  sed -i '' s+REPLACE_ME_HSMLIB+"${HSM2_LIB}"+g $CLIENT_CONFIG
else
  sed -i s+REPLACE_ME_HSMLIB+"${HSM2_LIB}"+g $CLIENT_CONFIG
fi

# create the users, remove any existing users
CRYPTO_PATH=$SCRIPT_DIR/../crypto-material/hsm
[ -d "$CRYPTO_PATH" ] && rm -fr "$CRYPTO_PATH"

# user passed in as parameter
CAADMIN="admin"
CAADMIN_PW="adminpw"
HSMUSER=$1

SOFTHSM2_CONF=$HSM2_CONF fabric-ca-client enroll -c $CLIENT_CONFIG -u https://$CAADMIN:$CAADMIN_PW@$CA_URL --mspdir "$CRYPTO_PATH"/$CAADMIN --tls.certfiles "${TLS_CERT}"
! SOFTHSM2_CONF=$HSM2_CONF fabric-ca-client register -c $CLIENT_CONFIG --mspdir "$CRYPTO_PATH"/$CAADMIN --id.name "$HSMUSER" --id.secret "$HSMUSER" --id.type client --caname ca-org1 --id.maxenrollments 0 -m example.com -u https://$CA_URL --tls.certfiles "${TLS_CERT}" && echo user probably already registered, continuing
SOFTHSM2_CONF=$HSM2_CONF  fabric-ca-client enroll -c $CLIENT_CONFIG -u https://"$HSMUSER":"$HSMUSER"@$CA_URL --mspdir "$CRYPTO_PATH"/"$HSMUSER" --tls.certfiles "${TLS_CERT}"
