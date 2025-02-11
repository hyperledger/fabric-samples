#!/usr/bin/env sh
#
# SPDX-License-Identifier: Apache-2.0
#
export PATH="${PWD}"/../../fabric-ca/bin:"${PWD}"/../bin:"${PATH}"
export FABRIC_CFG_PATH="${PWD}"/../config

#Configure the CA_NAME, CA_PORT, OPERATIONS_PORT and CSR_HOSTS for the CA
export CA_NAME=org1ca
export CA_PORT=5053
export OPERATIONS_PORT=9844
export CSR_HOSTS=org1ca,localhost,127.0.0.1

export CA_DIRECTORY="${PWD}"/data_ca/"${CA_NAME}"
export CA_HOME="${CA_DIRECTORY}"/ca
export TLSCA_HOME="${CA_DIRECTORY}"/tlsca
export DB_HOME="${CA_DIRECTORY}"/db
export TEMPLATE_DIR="${PWD}"/ca/ca_config

# Check to see if the CA directory exists
if [ ! -d "${CA_DIRECTORY}" ]; then

  # Create the new CA directory
  mkdir -p "${CA_HOME}"
  mkdir -p "${TLSCA_HOME}"
  mkdir -p "${DB_HOME}"

  # Copy the CA template files
  cp "${TEMPLATE_DIR}"/ca/fabric-ca-server-config.yaml "${CA_HOME}"/fabric-ca-server-config.yaml
  cp "${TEMPLATE_DIR}"/tlsca/fabric-ca-server-config.yaml "${TLSCA_HOME}"/fabric-ca-server-config.yaml

fi

export FABRIC_CA_SERVER_TLS_ENABLED=true 
export FABRIC_CA_SERVER_CSR_CN="${CA_NAME}" 
export FABRIC_CA_SERVER_CSR_HOSTS="${CSR_HOSTS}" 
export FABRIC_CA_SERVER_DEBUG=true 
export FABRIC_CA_SERVER_OPERATIONS_LISTENADDRESS=localhost:"${OPERATIONS_PORT}" 
fabric-ca-server start -d -b admin:adminpw --port "${CA_PORT}" --home "${CA_HOME}"
