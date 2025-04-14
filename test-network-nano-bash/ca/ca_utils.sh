#!/usr/bin/env sh
#
# SPDX-License-Identifier: Apache-2.0
#

######################################################################################
#  createEnrollment()
#
#  This is a convenience method for creating enrollments only
#  Primary purpose it to create enrollment certificates for CA admins.
######################################################################################

createEnrollment() {

  echo "createEnrollment $1 $2 $3 $4 $5 $6"

  local port=$1             # port of the CA used for creating the enrollment
  local username=$2         # username of the registered user on the CA 
  local password=$3         # password of the registered user on the CA
  local orgname=$4          # name of the org (e.g. Org1, Org2)  can be blank
  local component_dir=$5    # path of the component, this will be the directory where the artifacts will be created
  local tlscert=$6          # tls cert for connecting to the CA

  set -x

  # Enroll the identity

  fabric-ca-client enroll -u https://${username}:${password}@localhost:${port} --caname ca --mspdir "${component_dir}/msp" --tls.certfiles $tlscert

  if [ $? -ne 0 ]; then
    echo "fabric-ca-client admin enroll failed, make sure CA service is available. Exiting..."
    exit 1
  fi

  { set +x; } 2>/dev/null

  # Rename private key to mimic cryptogen
  find ${component_dir} -type f -name '*_sk'  | sed -e 'p;s/\(.*\)\/\(.*\)$/\1\/priv_sk/' | xargs -n2 mv -v

  # Rename the cacert to mimic cryptogen
  mv ${component_dir}/msp/cacerts/localhost-${port}-ca.pem ${component_dir}/msp/cacerts/ca.${orgname:+$orgname.}example.com-cert.pem

  echo "\n\n"

}

######################################################################################
#  createMSP()
#
#  This is a convenience method for creating the Membership Service Provider directories
#
######################################################################################

createMSP() {

  echo "createMSP $1 $2 $3"

  local caname=$1       # name of the ca (ordererca, org1ca, org2ca)
  local orgname=$2      # name of the org (org1, org2)  Ordering Org is blank
  local org_dir=$3          # directory of the organizatio

  mkdir -p ${org_dir}/msp/admincerts
  mkdir -p ${org_dir}/msp/cacerts
  mkdir -p ${org_dir}/msp/tlscacerts

  cp data_ca/${caname}/ca/ca-cert.pem ${org_dir}/msp/cacerts/ca.${orgname:+$orgname.}example.com-cert.pem
  cp data_ca/${caname}/tlsca/ca-cert.pem ${org_dir}/msp/tlscacerts/tlsca.${orgname:+$orgname.}example.com-cert.pem
  awk -v cacert_name="ca.${orgname:+$orgname.}example.com-cert" '{gsub(/ca.example.com-cert/,cacert_name)}1' ca/config.yaml > ${org_dir}/msp/config.yaml

  echo "\n\n"

}

######################################################################################
#  registerAndEnroll()
#
#  This is a convenience method for creating enrollments and TLS certificates
#  Primary purpose it to create enrollment certificates for org admin identities, and
#  enrollent and TLS certificates for peers and orderers.
######################################################################################

registerAndEnroll() {

  echo "registerAndEnroll $1 $2 $3 $4 $5 $6 $7 $8"

  local port=$1              # port of the CA used for creating the enrollment
  local username=$2          # username of the user to register on the CA 
  local password=$3          # password of the user to register on the CA 
  local type=$4              # type of registation, must be one of (peer, orderer, admin)
  local orgname=$5           # name of the org (e.g. Org1, Org2)  can be blank
  local component_dir=$6     # directory of the component, this will be the directory where the artifacts will be created
  local org_dir=$7           # directory of the organization, this is the directory that contains the credentials for the registration
  local tlscert=$8           # tls cert for connecting to the CA

  if [ "$type" = "admin" ]; then
    local attrs="hf.Registrar.Roles=client,hf.Registrar.Attributes=*,hf.Revoker=true,hf.GenCRL=true,admin=true:ecert,abac.init=true:ecert"
  else 
    local attrs=""
  fi

  set -x

  # Register the username
  fabric-ca-client register -u https://localhost:${port} --id.name ${username} --id.secret ${password} --id.type ${type} --id.attrs "${attrs}" --caname ca --tls.certfiles $tlscert --mspdir "${org_dir}/ca/msp"
  if [ $? -ne 0 ]; then
    echo "fabric-ca-client register failed, make sure CA service is available. Exiting..."
    exit 1
  fi

  # Enroll the identity
  fabric-ca-client enroll -u https://${username}:${password}@localhost:${port} --caname ca --mspdir "${component_dir}/msp" --tls.certfiles $tlscert
  if [ $? -ne 0 ]; then
    echo "fabric-ca-client enroll failed, make sure CA service is available. Exiting..."
    exit 1
  fi

  { set +x; } 2>/dev/null

  # Rename private key to mimic cryptogen
  find ${component_dir} -type f -name '*_sk'  | sed -e 'p;s/\(.*\)\/\(.*\)$/\1\/priv_sk/' | xargs -n2 mv -v

  # Rename the cacert to mimic cryptogen
  mv ${component_dir}/msp/cacerts/localhost-${port}-ca.pem ${component_dir}/msp/cacerts/ca.${orgname:+$orgname.}example.com-cert.pem

  # Set the cacert name and copy the config.json for NodeOU
  awk -v cacert_name="ca.${orgname:+$orgname.}example.com-cert" '{gsub(/ca.example.com-cert/,cacert_name)}1' ca/config.yaml > ${component_dir}/msp/config.yaml

  # If this is a peer or orderer type then create a TLS cert
  if [ "$type" = "peer" ] || [ "$type" = "orderer" ]; then

    set -x

    # Enroll the TLS cert
    fabric-ca-client enroll -u https://${username}:${password}@localhost:${port} --caname tlsca --mspdir "${component_dir}/tls" --tls.certfiles $tlscert --csr.hosts 'localhost,127.0.0.1'
    if [ $? -ne 0 ]; then
      echo "fabric-ca-client TLS enroll failed, make sure CA service is available. Exiting..."
      exit 1
    fi

    { set +x; } 2>/dev/null

    # Rename private key to mimic cryptogen
    find ${component_dir} -type f -name '*_sk'  | sed -e 'p;s/\(.*\)\/\(.*\)$/\1\/priv_sk/' | xargs -n2 mv -v

    # Copy and rename TLS certs and keys to mimic cryptogen
    cp ${component_dir}/tls/cacerts/localhost-${port}-tlsca.pem ${component_dir}/tls/ca.crt
    cp ${component_dir}/tls/keystore/priv_sk ${component_dir}/tls/server.key
    cp ${component_dir}/tls/signcerts/cert.pem ${component_dir}/tls/server.crt

    # Rename the tls cacert to mimic cryptogen
    mv ${component_dir}/tls/cacerts/localhost-${port}-tlsca.pem ${component_dir}/tls/cacerts/tlsca.${orgname:+$orgname.}example.com-cert.pem

  fi

  echo "\n\n"

}


