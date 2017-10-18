#!/bin/bash
#
# Copyright IBM Corp. All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0
#

source $(dirname "$0")/env.sh
initOrgVars $ORG

set -e

dowait "Root CA certificate file to be created" 10 $ROOT_CA_CERTFILE $ROOT_CA_LOGFILE

sleep 2

# Initialize the intermediate CA
fabric-ca-server init -b $BOOTSTRAP_USER_PASS -u $PARENT_URL

# Copy the intermediate CA's certificate chain to the data directory to be used by others
cp $FABRIC_CA_SERVER_HOME/ca-chain.pem $TARGET_CHAINFILE

# Start the intermediate CA
fabric-ca-server start
