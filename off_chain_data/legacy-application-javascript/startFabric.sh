#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#
# Exit on first error
set -e pipefail

starttime=$(date +%s)

# launch network; create channel and join peer to channel
pushd ../../test-network

# Fixes the issue of <sh: cd: line 1: can't cd to /data: No such file or directory> when running busybox in network down command on windows git bash
case "$(uname -s)" in
    CYGWIN*|MINGW32*|MSYS*|MINGW*)
     echo 'Running on MS Windows'
     export MSYS_NO_PATHCONV=1
     ./network.sh down
     unset MSYS_NO_PATHCONV
     ;;
    *)
     ./network.sh down
     ;;
esac
./network.sh up createChannel -ca -s couchdb
./network.sh deployCC -ccn basic -ccp ../asset-transfer-basic/chaincode-go/ -ccl go

popd

cat <<EOF

Total setup execution time : $(($(date +%s) - starttime)) secs ...

EOF
