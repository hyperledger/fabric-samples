#!/usr/bin/env bash
set -eo pipefail

# define the CA setup
CA_HOST=localhost
CA_URL=${CA_HOST}:7054
TLS_CERT='../../test-network/organizations/fabric-ca/org1/tls-cert.pem'

# try to locate the Soft HSM library
POSSIBLE_LIB_LOC=('/usr/lib/softhsm/libsofthsm2.so' \
'/usr/lib/x86_64-linux-gnu/softhsm/libsofthsm2.so' \
'/usr/local/lib/softhsm/libsofthsm2.so' \
'/usr/lib/libacsp-pkcs11.so'
)
for TEST_LIB in "${POSSIBLE_LIB_LOC[@]}"
do
  if [ -f $TEST_LIB ]; then
     HSM2_LIB=$TEST_LIB
     break
  fi
done
[ -z $HSM2_LIB ] && echo No SoftHSM PKCS11 Library found, ensure you have installed softhsm2 && exit 1

# create a softhsm2.conf file if one doesn't exist
HSM2_CONF=$HOME/softhsm2.conf
[ ! -f $HSM2_CONF ] && echo directories.tokendir = /tmp > $HSM2_CONF

# Update the client config file to point to the softhsm pkcs11 library
# which must be in $HOME/softhsm directory
echo 'directory' $PWD

CLIENT_CONFIG_TEMPLATE=../ca-client-config/fabric-ca-client-config-template.yaml
CLIENT_CONFIG=../ca-client-config/fabric-ca-client-config.yaml
cp $CLIENT_CONFIG_TEMPLATE $CLIENT_CONFIG
sed -i '' -e s+REPLACE_ME_HSMLIB+${HSM2_LIB}+g $CLIENT_CONFIG

# create the users, remove any existing users
CRYPTO_PATH=$PWD/../crypto-material/hsm
[ -d $CRYPTO_PATH ] && rm -fr $CRYPTO_PATH

# user passed in as parameter
CAADMIN=admin
CAADMIN_PW=adminpw
HSMUSER=$1

SOFTHSM2_CONF=$HSM2_CONF fabric-ca-client enroll -c $CLIENT_CONFIG -u https://$CAADMIN:$CAADMIN_PW@$CA_URL --mspdir $CRYPTO_PATH/$CAADMIN --csr.hosts example.com --tls.certfiles ${TLS_CERT}
! SOFTHSM2_CONF=$HSM2_CONF fabric-ca-client register -c $CLIENT_CONFIG --mspdir $CRYPTO_PATH/$CAADMIN --id.name $HSMUSER --id.secret $HSMUSER --id.type client --caname ca-org1 --id.maxenrollments 0 -m example.com -u https://$CA_URL --tls.certfiles ${TLS_CERT} && echo user probably already registered, continuing
SOFTHSM2_CONF=$HSM2_CONF  fabric-ca-client enroll -c $CLIENT_CONFIG -u https://$HSMUSER:$HSMUSER@$CA_URL --mspdir $CRYPTO_PATH/$HSMUSER --csr.hosts example.com --tls.certfiles ${TLS_CERT}
