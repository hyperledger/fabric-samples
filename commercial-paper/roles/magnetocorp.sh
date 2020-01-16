#!/bin/bash
#
# SPDX-License-Identifier: Apache-2.0

function _exit(){
    printf "Exiting:%s\n" "$1"
    exit -1
}

# Where am I?
# DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"

# cd "${DIR}/organization/magnetocorp/configuration/cli"
# docker-compose -f docker-compose.yml up -d cliMagnetoCorp

echo "
 Install and Instantiate a Smart Contract in either langauge

 JavaScript Contract:

 docker exec cliMagnetoCorp peer chaincode install -n papercontract -v 0 -p /opt/gopath/src/github.com/hyperledger/fabric-samples/commercial-paper/organization/magnetocorp/contract -l node
 docker exec cliMagnetoCorp peer chaincode instantiate -n papercontract -v 0 -l node -c '{\"Args\":[\"org.papernet.commercialpaper:instantiate\"]}' -C mychannel -P \"AND ('Org1MSP.member')\"

 Java Contract:

 docker exec cliMagnetoCorp peer chaincode install -n papercontract -v 0 -p /opt/gopath/src/github.com/hyperledger/fabric-samples/commercial-paper/organization/magnetocorp/contract-java -l java
 docker exec cliMagnetoCorp peer chaincode instantiate -n papercontract -v 0 -l java -c '{\"Args\":[\"org.papernet.commercialpaper:instantiate\"]}' -C mychannel -P \"AND ('Org1MSP.member')\"

 Go Contract:
 docker exec cliMagnetoCorp bash -c 'cd /opt/gopath/src/github.com/hyperledger/fabric-samples/commercial-paper/organization/magnetocorp/contract-go && go mod vendor'

 docker exec cliMagnetoCorp peer lifecycle chaincode package cp.tar.gz --lang golang --path github.com/hyperledger/fabric-samples/commercial-paper/organization/magnetocorp/contract-go --label cp_0
 docker exec cliMagnetoCorp peer lifecycle chaincode install cp.tar.gz
 export PACKAGE_ID=\$(docker exec cliMagnetoCorp peer lifecycle chaincode queryinstalled 2>&1 | awk -F \"[, ]+\" '/Label: /{print \$3}')

 docker exec cliMagnetoCorp peer lifecycle chaincode approveformyorg --channelID mychannel --name papercontract -v 0 --package-id \$PACKAGE_ID --sequence 1 --signature-policy \"AND ('Org1MSP.member')\"
 docker exec cliMagnetoCorp peer lifecycle chaincode commit -o orderer.example.com:7050 --channelID mychannel --name papercontract -v 0 --sequence 1 --waitForEvent --signature-policy \"AND ('Org1MSP.member')\"
 docker exec cliMagnetoCorp peer chaincode invoke -o orderer.example.com:7050 --channelID mychannel --name papercontract -c '{\"Args\":[\"org.papernet.commercialpaper:instantiate\"]}' --waitForEvent

 Run Applications in either langauage (can be different from the Smart Contract)

 JavaScript Client Aplications:

 To add identity to the wallet:   node addToWallet.js
 To issue the paper           :   node issue.js

 Java Client Applications:

 (remember to build the Java first with 'mvn')

 To add identity to the wallet:   java addToWallet
 To issue the paper           :   java issue
"

echo "Suggest that you change to this dir>  cd ${DIR}/organization/magnetocorp/"
