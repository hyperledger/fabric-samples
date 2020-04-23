CHANNEL_NAME="$1"
ORDERER_CA="$2"
CORE_PEER_LOCALMSPID="$3"
CORE_PEER_ADDRESS="$4"
CORE_PEER_TLS_ROOTCERT_FILE="$5"
CORE_PEER_MSPCONFIGPATH="$6"

which jq
if [ "$?" -ne 0 ]; then
  yum -y install jq
fi

peer channel fetch config config_block_${CHANNEL_NAME}.pb -o $CORE_PEER_ADDRESS -c $CHANNEL_NAME --tls --cafile $ORDERER_CA
configtxlator proto_decode --input config_block_${CHANNEL_NAME}.pb --type common.Block | jq '.data.data[0].payload.data.config' > config_${CHANNEL_NAME}.json
cp ./config_${CHANNEL_NAME}.json /host/var/run/

