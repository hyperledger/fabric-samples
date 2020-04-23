#!/bin/bash
# 这个脚本需要在 /var/run/ 目录下执行，因为 cli 获取通道配置文件后会复制到容器的 /host/var/run/ 下，也就是宿主机的 /var/run/

CHANNEL_NAME="$1"

which jq
if [ "$?" -ne 0 ]; then
  yum -y install jq
fi

cp -r /host/var/run/config_${CHANNEL_NAME}.json ./
cp -r /host/var/run/config_${CHANNEL_NAME}_modified.json ./

configtxlator proto_encode --input config_${CHANNEL_NAME}.json --type common.Config --output config_${CHANNEL_NAME}.pb

configtxlator proto_encode --input config_${CHANNEL_NAME}_modified.json --type common.Config --output config_${CHANNEL_NAME}_modified.pb

configtxlator compute_update --channel_id $CHANNEL_NAME --original config_${CHANNEL_NAME}.pb --updated config_${CHANNEL_NAME}_modified.pb --output config_update_${CHANNEL_NAME}.pb

configtxlator proto_decode --input config_update_${CHANNEL_NAME}.pb --type common.ConfigUpdate | jq '.' > config_update_${CHANNEL_NAME}.json

echo '{"payload":{"header":{"channel_header":{"channel_id":"'${CHANNEL_NAME}'","type":2}},"data":{"config_update":'$(cat config_update_${CHANNEL_NAME}.json)'}}}' | jq '.' > config_update_${CHANNEL_NAME}_in_envelope.json

configtxlator proto_encode --input config_update_${CHANNEL_NAME}_in_envelope.json --type common.Envelope --output config_update_${CHANNEL_NAME}_in_envelope.pb

