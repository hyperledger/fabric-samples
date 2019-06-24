#!/bin/bash

echo
echo " ____    _____      _      ____    _____ "
echo "/ ___|  |_   _|    / \    |  _ \  |_   _|"
echo "\___ \    | |     / _ \   | |_) |   | |  "
echo " ___) |   | |    / ___ \  |  _ <    | |  "
echo "|____/    |_|   /_/   \_\ |_| \_\   |_|  "
echo
echo "Upgrade your first network (BYFN) from v1.3.x to v1.4.x end-to-end test"
echo
CHANNEL_NAME="$1"
DELAY="$2"
LANGUAGE="$3"
TIMEOUT="$4"
VERBOSE="$5"
: ${CHANNEL_NAME:="mychannel"}
: ${DELAY:="5"}
: ${LANGUAGE:="golang"}
: ${TIMEOUT:="10"}
: ${VERBOSE:="false"}
LANGUAGE=$(echo "$LANGUAGE" | tr [:upper:] [:lower:])
COUNTER=1
MAX_RETRY=5
SYS_CHANNEL=$SYS_CHANNEL

CC_SRC_PATH="github.com/chaincode/chaincode_example02/go/"
if [ "$LANGUAGE" = "node" ]; then
  CC_SRC_PATH="/opt/gopath/src/github.com/chaincode/chaincode_example02/node/"
fi

echo "System channel name : "$SYS_CHANNEL
echo "Channel name : "$CHANNEL_NAME

# import utils
. scripts/utils.sh

# addCapabilityToChannel <channel_id> <capabilities_group>
# This function pulls the current channel config, modifies it with capabilities
# for the specified group, computes the config update, signs, and submits it.
addCapabilityToChannel() {
        CH_NAME=$1
        GROUP=$2

        setOrdererGlobals

        # Get the current channel config, decode and write it to config.json
        fetchChannelConfig $CH_NAME config.json

        # Modify the correct section of the config based on capabilities group
        if [ $GROUP == "orderer" ]; then
                jq -s '.[0] * {"channel_group":{"groups":{"Orderer": {"values": {"Capabilities": .[1]}}}}}' config.json ./scripts/capabilities.json > modified_config.json
        elif [ $GROUP == "channel" ]; then
                jq -s '.[0] * {"channel_group":{"values": {"Capabilities": .[1]}}}' config.json ./scripts/capabilities.json > modified_config.json
        elif [ $GROUP == "application" ]; then
                jq -s '.[0] * {"channel_group":{"groups":{"Application": {"values": {"Capabilities": .[1]}}}}}' config.json ./scripts/capabilities.json > modified_config.json
        fi

        # Create a config updated for this channel based on the differences between config.json and modified_config.json
        # write the output to config_update_in_envelope.pb
        createConfigUpdate "$CH_NAME" config.json modified_config.json config_update_in_envelope.pb

        # Sign, and set the correct identity for submission.
        if [ $CH_NAME != $SYS_CHANNEL ] ; then
                if [ $GROUP == "orderer" ]; then
                      # Modifying the orderer group requires only the Orderer admin to sign.
                      # Prepare to sign the update as the OrdererOrg.Admin
                      setOrdererGlobals
                elif [ $GROUP == "channel" ]; then
                      # Modifying the channel group requires a majority of application admins and the orderer admin to sign.
                      # Sign with PeerOrg1.Admin
                      signConfigtxAsPeerOrg 1 config_update_in_envelope.pb
                      # Sign with PeerOrg2.Admin
                      signConfigtxAsPeerOrg 2 config_update_in_envelope.pb
                      # Prepare to sign the update as the OrdererOrg.Admin
                      setOrdererGlobals
                elif [ $GROUP == "application" ]; then
                      # Modifying the application group requires a majority of application admins to sign.
                      # Sign with PeerOrg1.Admin
                      signConfigtxAsPeerOrg 1 config_update_in_envelope.pb
                      # Prepare to sign the update as the PeerOrg2.Admin
                      setGlobals 0 2
                fi
        else
               # For the orderer system channel, only the orderer admin needs sign
               # which will be attached during the update
               setOrdererGlobals
        fi

        if [ -z "$CORE_PEER_TLS_ENABLED" -o "$CORE_PEER_TLS_ENABLED" = "false" ]; then
                set -x
                peer channel update -f config_update_in_envelope.pb -c $CH_NAME -o orderer.example.com:7050 --cafile $ORDERER_CA
                res=$?
                set +x
        else
                set -x
                peer channel update -f config_update_in_envelope.pb -c $CH_NAME -o orderer.example.com:7050 --tls true --cafile $ORDERER_CA
                res=$?
                set +x
        fi
        verifyResult $res "Config update for \"$GROUP\" on \"$CH_NAME\" failed"
        echo "===================== Config update for \"$GROUP\" on \"$CH_NAME\" is completed ===================== "

}

sleep $DELAY

#Config update for /Channel on $SYS_CHANNEL
echo "Config update for /Channel on \"$SYS_CHANNEL\""
addCapabilityToChannel $SYS_CHANNEL channel

sleep $DELAY

#Config update for /Channel/Orderer on $SYS_CHANNEL
echo "Config update for /Channel/Orderer on \"$SYS_CHANNEL\""
addCapabilityToChannel $SYS_CHANNEL orderer

sleep $DELAY

#Config update for /Channel on $CHANNEL_NAME
echo "Config update for /Channel on \"$CHANNEL_NAME\""
addCapabilityToChannel $CHANNEL_NAME channel

sleep $DELAY

#Config update for /Channel/Orderer
echo "Config update for /Channel/Orderer on \"$CHANNEL_NAME\""
addCapabilityToChannel $CHANNEL_NAME orderer

sleep $DELAY

#Config update for /Channel/Application
echo "Config update for /Channel/Application on \"$CHANNEL_NAME\""
addCapabilityToChannel $CHANNEL_NAME application

sleep $DELAY

#Query on chaincode on Peer0/Org1
echo "Querying chaincode on org1/peer0..."
chaincodeQuery 0 1 90

sleep $DELAY

#Invoke on chaincode on Peer0/Org1
echo "Sending invoke transaction on org1/peer0..."
chaincodeInvoke 0 1 0 2

sleep $DELAY

#Query on chaincode on Peer0/Org1
echo "Querying chaincode on org1/peer0..."
chaincodeQuery 0 1 80

echo
echo "===================== All GOOD, End-2-End UPGRADE Scenario execution completed ===================== "
echo

echo
echo " _____   _   _   ____            _____   ____    _____ "
echo "| ____| | \ | | |  _ \          | ____| |___ \  | ____|"
echo "|  _|   |  \| | | | | |  _____  |  _|     __) | |  _|  "
echo "| |___  | |\  | | |_| | |_____| | |___   / __/  | |___ "
echo "|_____| |_| \_| |____/          |_____| |_____| |_____|"
echo

exit 0
