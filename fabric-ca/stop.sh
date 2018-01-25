#!/bin/bash
#
# Copyright IBM Corp. All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0
#

set -e
SDIR=$(dirname "$0")
source $SDIR/scripts/env.sh

log "Stopping docker containers ..."
docker-compose down
# Stop chaincode containers and images as well
docker rm -f $(docker ps -aq --filter name=dev-peer)
docker rmi $(docker images | awk '$1 ~ /dev-peer/ { print $3 }')
log "Docker containers have been stopped"
