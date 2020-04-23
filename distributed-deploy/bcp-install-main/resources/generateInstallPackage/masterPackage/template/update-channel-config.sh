#!/bin/bash
# 这个文件在 cli 里执行

CHANNEL_NAME="$1"
ORDERER_CA="$2"
CORE_PEER_LOCALMSPID="$3"
CORE_PEER_ADDRESS="$4"
CORE_PEER_TLS_ROOTCERT_FILE="$5"
CORE_PEER_MSPCONFIGPATH="$6"

# cp /host/var/run/config_update_${CHANNEL_NAME}_in_envelope.pb ./

peer channel update -f config_update_${CHANNEL_NAME}_in_envelope.pb -c $CHANNEL_NAME -o $CORE_PEER_ADDRESS --tls --cafile $ORDERER_CA

