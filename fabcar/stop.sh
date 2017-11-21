#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#
# Exit on first error
set -e

# don't rewrite paths for Windows Git Bash users
export MSYS_NO_PATHCONV=1

starttime=$(date +%s)

#delete hfc-key-store file
rm -rf hfc-key-store

# launch network; create channel and join peer to channel
cd ../basic-network
./stop.sh

printf "\nTotal setup execution time : $(($(date +%s) - starttime)) secs ...\n\n\n"
