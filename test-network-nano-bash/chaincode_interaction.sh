#!/usr/bin/env sh

# The sleep commands were added because the queries depend on each other and have some latency.

. peer1admin.sh

peer chaincode invoke -o 127.0.0.1:6050 -C mychannel -n basic -c '{"Args":["CreateAsset","1","blue","35","tom","1000"]}' --tls --cafile "${PWD}"/crypto-config/ordererOrganizations/example.com/orderers/orderer.example.com/tls/ca.crt >> ./logs/chaincode_interaction.log 2>&1
sleep 2

peer chaincode query -C mychannel -n basic -c '{"Args":["ReadAsset","1"]}' >> ./logs/chaincode_interaction.log 2>&1
sleep 2

peer chaincode invoke -o 127.0.0.1:6050 -C mychannel -n basic -c '{"Args":["UpdateAsset","1","blue","35","jerry","1000"]}' --tls --cafile "${PWD}"/crypto-config/ordererOrganizations/example.com/orderers/orderer.example.com/tls/ca.crt >> ./logs/chaincode_interaction.log 2>&1
sleep 2

peer chaincode query -C mychannel -n basic -c '{"Args":["ReadAsset","1"]}' >> ./logs/chaincode_interaction.log 2>&1