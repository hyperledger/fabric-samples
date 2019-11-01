#!/bin/bash
#
# SPDX-License-Identifier: Apache-2.0

function _exit(){
    printf "Exiting:%s\n" "$1"
    exit -1
}

# Where am I?
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"

cd "${DIR}/organization/magnetocorp/configuration/cli"
docker-compose -f docker-compose.yml up -d cliMagnetoCorp

echo "
 Install and Instantiate a Smart Contract in either langauge

 JavaScript Contract:

 docker exec cliMagnetoCorp peer chaincode install -n papercontract -v 0 -p /opt/gopath/src/github.com/contract -l node
 docker exec cliMagnetoCorp peer chaincode instantiate -n papercontract -v 0 -l node -c '{\"Args\":[\"org.papernet.commercialpaper:instantiate\"]}' -C mychannel -P \"AND ('Org1MSP.member')\"

 Java Contract:

 docker exec cliMagnetoCorp peer chaincode install -n papercontract -v 0 -p /opt/gopath/src/github.com/contract-java -l java
 docker exec cliMagnetoCorp peer chaincode instantiate -n papercontract -v 0 -l java -c '{\"Args\":[\"org.papernet.commercialpaper:instantiate\"]}' -C mychannel -P \"AND ('Org1MSP.member')\"

 Go Contract:
 
 docker exec cliMagnetoCorp bash -c \"cd /opt/gopath/src/github.com/hyperledger/fabric-samples/commercial-paper/organization/magnetocorp/contract-go GO111MODULE=on GOCACHE=on go mod vendor\"
 docker exec cliMagnetoCorp peer chaincode install -n papercontract -v 0 -p github.com/hyperledger/fabric-samples/commercial-paper/organization/magnetocorp/contract-go -l golang
 docker exec cliMagnetoCorp peer chaincode instantiate -n papercontract -v 0 -l golang -c '{\"Args\":[\"org.papernet.commercialpaper:instantiate\"]}' -C mychannel -P \"AND ('Org1MSP.member')\"

 Run Applications in any langauage (can be different from the Smart Contract)

 JavaScript Client Aplications:

 To add identity to the wallet:   node addToWallet.js
 To issue the paper           :   node issue.js

 Java Client Applications:

 (remember to build the Java first with 'mvn')

 To add identity to the wallet:   java addToWallet
 To issue the paper           :   java issue
"

echo "Suggest that you change to this dir>  cd ${DIR}/organization/magnetocorp/"
