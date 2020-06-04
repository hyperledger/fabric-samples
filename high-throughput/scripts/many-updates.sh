#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

source scripts/setenv.sh

for (( i = 0; i < 1000; ++i ))
do
	peer chaincode invoke -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ../test-network/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n bigdatacc -c '{"Args":["update","'$1'","'$2'","'$3'"]}'
done
