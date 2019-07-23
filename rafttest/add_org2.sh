#!/bin/bash
export FABRIC_CFG_PATH=$PWD
export ORDERER_CA=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.example.com/peers/orderer0.org1.example.com/msp/tlscacerts/tlsca.org1.example.com-cert.pem
export PATH=${PWD}/../bin:${PWD}:$PATH
# 生成org2组织信息
configtxgen --printOrg Org2MSP >channel-artifacts/org2.json

# 设置通道为系统通道
export CHANNEL_NAME=testchainid

# 获取系统通道配置块并转换成json格式
docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'peer channel fetch config config_block.pb -o orderer0.org1.example.com:7050 -c $CHANNEL_NAME --tls --cafile $ORDERER_CA'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'configtxlator proto_decode --input config_block.pb --type common.Block | jq .data.data[0].payload.data.config > config.json'

# 将组织org2相关信息org2.json添加到config.json orderer groups位置
docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'jq -s ".[0] * {"channel_group":{"groups":{"Orderer":{"groups": {"Org2MSP":.[1]}}}}}" config.json ./channel-artifacts/org2.json > modified_config.json'

# 将组织org2的orderer tls添加到config.json orderer consenters位置
export TLS_FILE=crypto/peerOrganizations/org2.example.com/peers/orderer0.org2.example.com/tls/server.crt

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" -e "TLS_FILE=$TLS_FILE" cli sh -c 'echo "{\"client_tls_cert\":\"$(cat $TLS_FILE | base64 |xargs echo | sed "s/ //g")\",\"host\":\"orderer0.org2.example.com\",\"port\":7050,\"server_tls_cert\":\"$(cat $TLS_FILE | base64 |xargs echo | sed "s/ //g")\"}" > org2consenter.json'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'jq ".channel_group.groups.Orderer.values.ConsensusType.value.metadata.consenters += [$(cat org2consenter.json)]" modified_config.json > modified_config_add.json'

# 转换成pb格式及计算增量差异
docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'configtxlator proto_encode --input config.json --type common.Config --output config.pb'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'configtxlator proto_encode --input modified_config_add.json --type common.Config --output modified_config.pb'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'configtxlator compute_update --channel_id $CHANNEL_NAME --original config.pb --updated modified_config.pb --output org2_update.pb'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'configtxlator proto_decode --input org2_update.pb --type common.ConfigUpdate | jq . > org2_update.json'

# 构建交易并签名
docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'echo "{\"payload\":{\"header\":{\"channel_header\":{\"channel_id\":\"testchainid\", \"type\":2}},\"data\":{\"config_update\":"$(cat org2_update.json)"}}}" | jq . > org2_update_in_envelope.json'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'configtxlator proto_encode --input org2_update_in_envelope.json --type common.Envelope --output org2_update_in_envelope.pb'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'peer channel update -f org2_update_in_envelope.pb -c $CHANNEL_NAME -o orderer0.org1.example.com:7050 --tls --cafile $ORDERER_CA'

# 获取最新配置块给org2的orderer作为启动块
docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'peer channel fetch config last_config_block.pb -o orderer0.org1.example.com:7050 -c $CHANNEL_NAME --tls --cafile $ORDERER_CA'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'cp last_config_block.pb ./channel-artifacts/last_config.block'

# 启动org2
sleep 5
docker-compose -f docker-compose-org2.yaml up -d
sleep 10
# 按照上述步骤添加到应用链mychannel
export CHANNEL_NAME=mychannel

# 获取系统通道配置块并转换成json格式
docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'peer channel fetch config config_block.pb -o orderer0.org1.example.com:7050 -c $CHANNEL_NAME --tls --cafile $ORDERER_CA'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'configtxlator proto_decode --input config_block.pb --type common.Block | jq .data.data[0].payload.data.config > config.json'

# 将组织org2相关信息org2.json添加到config.json orderer及Application groups位置
docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'jq -s ".[0] * {"channel_group":{"groups":{"Orderer":{"groups": {"Org2MSP":.[1]}}}}}" config.json ./channel-artifacts/org2.json > modified_config.json'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'jq -s ".[0] * {"channel_group":{"groups":{"Application":{"groups": {"Org2MSP":.[1]}}}}}" config.json ./channel-artifacts/org2.json > modified_config.json'

# 将组织org2的orderer tls添加到config.json orderer consenters位置
export TLS_FILE=crypto/peerOrganizations/org2.example.com/peers/orderer0.org2.example.com/tls/server.crt

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" -e "TLS_FILE=$TLS_FILE" cli sh -c 'echo "{\"client_tls_cert\":\"$(cat $TLS_FILE | base64 |xargs echo | sed "s/ //g")\",\"host\":\"orderer0.org2.example.com\",\"port\":7050,\"server_tls_cert\":\"$(cat $TLS_FILE | base64 |xargs echo | sed "s/ //g")\"}" > org2consenter.json'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'jq ".channel_group.groups.Orderer.values.ConsensusType.value.metadata.consenters += [$(cat org2consenter.json)]" modified_config.json > modified_config_add.json'

# 转换成pb格式及计算增量差异
docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'configtxlator proto_encode --input config.json --type common.Config --output config.pb'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'configtxlator proto_encode --input modified_config_add.json --type common.Config --output modified_config.pb'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'configtxlator compute_update --channel_id $CHANNEL_NAME --original config.pb --updated modified_config.pb --output org2_update.pb'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'configtxlator proto_decode --input org2_update.pb --type common.ConfigUpdate | jq . > org2_update.json'

# 构建交易及更新
docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'echo "{\"payload\":{\"header\":{\"channel_header\":{\"channel_id\":\"mychannel\", \"type\":2}},\"data\":{\"config_update\":"$(cat org2_update.json)"}}}" | jq . > org2_update_in_envelope.json'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'configtxlator proto_encode --input org2_update_in_envelope.json --type common.Envelope --output org2_update_in_envelope.pb'

docker exec -e "ORDERER_CA=$ORDERER_CA" -e "CHANNEL_NAME=$CHANNEL_NAME" cli sh -c 'peer channel update -f org2_update_in_envelope.pb -c $CHANNEL_NAME -o orderer0.org1.example.com:7050 --tls --cafile $ORDERER_CA'
