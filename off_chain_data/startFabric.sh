#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#
# Exit on first error
set -e pipefail


# don't rewrite paths for Windows Git Bash users
export MSYS_NO_PATHCONV=1
starttime=$(date +%s)
<<<<<<< HEAD
CC_SRC_LANGUAGE=${1:-"go"}
CC_SRC_LANGUAGE=`echo "$CC_SRC_LANGUAGE" | tr [:upper:] [:lower:]`
CC_RUNTIME_LANGUAGE=golang
CC_SRC_PATH=github.com/chaincode/marbles02/go

=======
CC_SRC_LANGUAGE=golang
CC_RUNTIME_LANGUAGE=golang
CC_SRC_PATH=github.com/hyperledger/fabric-samples/chaincode/marbles02/go
echo Vendoring Go dependencies ...
pushd ../chaincode/marbles02/go
GO111MODULE=on go mod vendor
popd
echo Finished vendoring Go dependencies
>>>>>>> 3dbe116a30d517e1e828afb61b2198763141f2e6

# clean the keystore
rm -rf ./hfc-key-store

# launch network; create channel and join peer to channel
pushd ../first-network
echo y | ./byfn.sh down
echo y | ./byfn.sh up -a -n -s couchdb
popd

CONFIG_ROOT=/opt/gopath/src/github.com/hyperledger/fabric/peer
ORG1_MSPCONFIGPATH=${CONFIG_ROOT}/crypto/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp
ORG1_TLS_ROOTCERT_FILE=${CONFIG_ROOT}/crypto/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt
ORG2_MSPCONFIGPATH=${CONFIG_ROOT}/crypto/peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp
ORG2_TLS_ROOTCERT_FILE=${CONFIG_ROOT}/crypto/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt
ORDERER_TLS_ROOTCERT_FILE=${CONFIG_ROOT}/crypto/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem

<<<<<<< HEAD
=======
echo "Packaging the marbles smart contract"
docker exec \
  cli \
  peer lifecycle chaincode package marbles.tar.gz  \
    --path $CC_SRC_PATH \
    --lang $CC_RUNTIME_LANGUAGE \
    --label marblesv1

>>>>>>> 3dbe116a30d517e1e828afb61b2198763141f2e6
echo "Installing smart contract on peer0.org1.example.com"
docker exec \
  -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_ADDRESS=peer0.org1.example.com:7051 \
  -e CORE_PEER_MSPCONFIGPATH=${ORG1_MSPCONFIGPATH} \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${ORG1_TLS_ROOTCERT_FILE} \
  cli \
<<<<<<< HEAD
  peer chaincode install \
    -n marbles \
    -v 1.0 \
    -p "$CC_SRC_PATH" \
    -l "$CC_RUNTIME_LANGUAGE"
=======
  peer lifecycle chaincode install marbles.tar.gz
>>>>>>> 3dbe116a30d517e1e828afb61b2198763141f2e6

echo "Installing smart contract on peer1.org1.example.com"
docker exec \
  -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_ADDRESS=peer1.org1.example.com:8051 \
  -e CORE_PEER_MSPCONFIGPATH=${ORG1_MSPCONFIGPATH} \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${ORG1_TLS_ROOTCERT_FILE} \
  cli \
<<<<<<< HEAD
  peer chaincode install \
    -n marbles \
    -v 1.0 \
    -p "$CC_SRC_PATH" \
    -l "$CC_RUNTIME_LANGUAGE"
=======
  peer lifecycle chaincode install marbles.tar.gz
>>>>>>> 3dbe116a30d517e1e828afb61b2198763141f2e6

echo "Installing smart contract on peer0.org2.example.com"
docker exec \
  -e CORE_PEER_LOCALMSPID=Org2MSP \
  -e CORE_PEER_ADDRESS=peer0.org2.example.com:9051 \
  -e CORE_PEER_MSPCONFIGPATH=${ORG2_MSPCONFIGPATH} \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${ORG2_TLS_ROOTCERT_FILE} \
  cli \
<<<<<<< HEAD
  peer chaincode install \
    -n marbles \
    -v 1.0 \
    -p "$CC_SRC_PATH" \
    -l "$CC_RUNTIME_LANGUAGE"
=======
  peer lifecycle chaincode install marbles.tar.gz
>>>>>>> 3dbe116a30d517e1e828afb61b2198763141f2e6

echo "Installing smart contract on peer1.org2.example.com"
docker exec \
  -e CORE_PEER_LOCALMSPID=Org2MSP \
  -e CORE_PEER_ADDRESS=peer1.org2.example.com:10051 \
  -e CORE_PEER_MSPCONFIGPATH=${ORG2_MSPCONFIGPATH} \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${ORG2_TLS_ROOTCERT_FILE} \
  cli \
<<<<<<< HEAD
  peer chaincode install \
    -n marbles \
    -v 1.0 \
    -p "$CC_SRC_PATH" \
    -l "$CC_RUNTIME_LANGUAGE"

echo "Instantiating smart contract on mychannel"
=======
  peer lifecycle chaincode install marbles.tar.gz

echo "Query the chaincode package id"
docker exec \
  -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_ADDRESS=peer0.org1.example.com:7051 \
  -e CORE_PEER_MSPCONFIGPATH=${ORG1_MSPCONFIGPATH} \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${ORG1_TLS_ROOTCERT_FILE} \
  cli \
  /bin/bash -c "peer lifecycle chaincode queryinstalled > log"
  PACKAGE_ID=`docker exec cli sed -nr '/Label: marblesv1/s/Package ID: (.*), Label: marblesv1/\1/p;' log`

echo "Approving the chaincode definition for org1.example.com"
docker exec \
  -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_ADDRESS=peer0.org1.example.com:7051 \
  -e CORE_PEER_MSPCONFIGPATH=${ORG1_MSPCONFIGPATH} \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${ORG1_TLS_ROOTCERT_FILE} \
  cli \
  peer lifecycle chaincode approveformyorg \
    -o orderer.example.com:7050 \
    --channelID mychannel \
    --name marbles \
    --version 1.0 \
    --init-required \
    --signature-policy AND"('Org1MSP.member','Org2MSP.member')" \
    --sequence 1 \
    --package-id $PACKAGE_ID \
    --tls \
    --cafile ${ORDERER_TLS_ROOTCERT_FILE}

echo "Approving the chaincode definition for org2.example.com"
docker exec \
  -e CORE_PEER_LOCALMSPID=Org2MSP \
  -e CORE_PEER_ADDRESS=peer0.org2.example.com:9051 \
  -e CORE_PEER_MSPCONFIGPATH=${ORG2_MSPCONFIGPATH} \
  -e CORE_PEER_TLS_ROOTCERT_FILE=${ORG2_TLS_ROOTCERT_FILE} \
  cli \
  peer lifecycle chaincode approveformyorg \
    -o orderer.example.com:7050 \
    --channelID mychannel \
    --name marbles \
    --version 1.0 \
    --init-required \
    --signature-policy AND"('Org1MSP.member','Org2MSP.member')" \
    --sequence 1 \
    --package-id $PACKAGE_ID \
    --tls \
    --cafile ${ORDERER_TLS_ROOTCERT_FILE}

echo "Waiting for the approvals to be committed ..."

sleep 10

echo "Commit the chaincode definition to the channel"
>>>>>>> 3dbe116a30d517e1e828afb61b2198763141f2e6
docker exec \
  -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_MSPCONFIGPATH=${ORG1_MSPCONFIGPATH} \
  cli \
<<<<<<< HEAD
  peer chaincode instantiate \
    -o orderer.example.com:7050 \
    -C mychannel \
    -n marbles \
    -l "$CC_RUNTIME_LANGUAGE" \
    -v 1.0 \
    -c '{"Args":[]}' \
    -P "AND('Org1MSP.member','Org2MSP.member')" \
    --tls \
    --cafile ${ORDERER_TLS_ROOTCERT_FILE} \
    --peerAddresses peer0.org1.example.com:7051 \
    --tlsRootCertFiles ${ORG1_TLS_ROOTCERT_FILE}

echo "Waiting for instantiation request to be committed ..."
=======
  peer lifecycle chaincode commit \
  -o orderer.example.com:7050 \
    --channelID mychannel \
    --name marbles \
    --version 1.0 \
    --init-required \
    --signature-policy AND"('Org1MSP.member','Org2MSP.member')" \
    --sequence 1 \
    --tls \
    --cafile ${ORDERER_TLS_ROOTCERT_FILE} \
    --peerAddresses peer0.org1.example.com:7051 \
    --tlsRootCertFiles ${ORG1_TLS_ROOTCERT_FILE} \
    --peerAddresses peer0.org2.example.com:9051 \
    --tlsRootCertFiles ${ORG2_TLS_ROOTCERT_FILE}

echo "Waiting for the chaincode to be committed ..."

sleep 10

echo "invoke the marbles chaincode init function ... "
docker exec \
  -e CORE_PEER_LOCALMSPID=Org1MSP \
  -e CORE_PEER_ADDRESS=peer0.org1.example.com:7051 \
  cli \
  peer chaincode invoke \
    -o orderer.example.com:7050 \
    -C mychannel \
    -n marbles \
    --isInit \
    -c '{"Args":["Init"]}' \
    --tls \
    --cafile ${ORDERER_TLS_ROOTCERT_FILE} \
    --peerAddresses peer0.org1.example.com:7051 \
    --tlsRootCertFiles ${ORG1_TLS_ROOTCERT_FILE} \
    --peerAddresses peer0.org2.example.com:9051 \
    --tlsRootCertFiles ${ORG2_TLS_ROOTCERT_FILE}

>>>>>>> 3dbe116a30d517e1e828afb61b2198763141f2e6
sleep 10

cat <<EOF

Total setup execution time : $(($(date +%s) - starttime)) secs ...

EOF
