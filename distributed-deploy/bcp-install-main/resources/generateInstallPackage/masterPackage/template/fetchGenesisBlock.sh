CHANNEL_NAME="$1"
ORDERER_CA="$2"
CORE_PEER_LOCALMSPID="$3"
CORE_PEER_ADDRESS="$4"
CORE_PEER_TLS_ROOTCERT_FILE="$5"
CORE_PEER_MSPCONFIGPATH="$6"

export CORE_PEER_LOCALMSPID=${CORE_PEER_LOCALMSPID}
export CORE_PEER_MSPCONFIGPATH=${CORE_PEER_MSPCONFIGPATH}

peer channel fetch config channel-artifacts/genesis.block -o $CORE_PEER_ADDRESS -c $CHANNEL_NAME --tls --cafile $ORDERER_CA

cp -r channel-artifacts/genesis.block /host/var/run/genesis.block