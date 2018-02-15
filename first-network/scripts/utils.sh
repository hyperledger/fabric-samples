#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

# This is a collection of bash functions used by different scripts


# verify the result of the end-to-end test
verifyResult () {
	if [ $1 -ne 0 ] ; then
		echo "!!!!!!!!!!!!!!! "$2" !!!!!!!!!!!!!!!!"
    echo "========= ERROR !!! FAILED to execute End-2-End Scenario ==========="
		echo
   		exit 1
	fi
}

setGlobals () {
	PEER=$1
	ORG=$2
	if [ $ORG -eq 1 ] ; then
		CORE_PEER_LOCALMSPID="Org1MSP"
		CORE_PEER_TLS_ROOTCERT_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt
		CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp
		if [ $PEER -eq 0 ]; then
			CORE_PEER_ADDRESS=peer0.org1.example.com:7051
		else
			CORE_PEER_ADDRESS=peer1.org1.example.com:7051
		fi
	elif [ $ORG -eq 2 ] ; then
		CORE_PEER_LOCALMSPID="Org2MSP"
		CORE_PEER_TLS_ROOTCERT_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt
		CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp
		if [ $PEER -eq 0 ]; then
			CORE_PEER_ADDRESS=peer0.org2.example.com:7051
		else
			CORE_PEER_ADDRESS=peer1.org2.example.com:7051
		fi

	elif [ $ORG -eq 3 ] ; then
		CORE_PEER_LOCALMSPID="Org3MSP"
		CORE_PEER_TLS_ROOTCERT_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org3.example.com/peers/peer0.org3.example.com/tls/ca.crt
		CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org3.example.com/users/Admin@org3.example.com/msp
		if [ $PEER -eq 0 ]; then
			CORE_PEER_ADDRESS=peer0.org3.example.com:7051
		else
			CORE_PEER_ADDRESS=peer1.org3.example.com:7051
		fi
	else
		echo "================== ERROR !!! ORG Unknown =================="
	fi

	env |grep CORE
}


updateAnchorPeers() {
  PEER=$1
  ORG=$2
  setGlobals $PEER $ORG

  if [ -z "$CORE_PEER_TLS_ENABLED" -o "$CORE_PEER_TLS_ENABLED" = "false" ]; then
		peer channel update -o orderer.example.com:7050 -c $CHANNEL_NAME -f ./channel-artifacts/${CORE_PEER_LOCALMSPID}anchors.tx >&log.txt
  else
		peer channel update -o orderer.example.com:7050 -c $CHANNEL_NAME -f ./channel-artifacts/${CORE_PEER_LOCALMSPID}anchors.tx --tls $CORE_PEER_TLS_ENABLED --cafile $ORDERER_CA >&log.txt
  fi
	res=$?
	cat log.txt
	verifyResult $res "Anchor peer update failed"
	echo "===================== Anchor peers for org \"$CORE_PEER_LOCALMSPID\" on \"$CHANNEL_NAME\" is updated successfully ===================== "
	sleep $DELAY
	echo
}

## Sometimes Join takes time hence RETRY at least for 5 times
joinChannelWithRetry () {
	PEER=$1
	ORG=$2
	setGlobals $PEER $ORG

	peer channel join -b $CHANNEL_NAME.block  >&log.txt
	res=$?
	cat log.txt
	if [ $res -ne 0 -a $COUNTER -lt $MAX_RETRY ]; then
		COUNTER=` expr $COUNTER + 1`
		echo "peer${PEER}.org${ORG} failed to join the channel, Retry after $DELAY seconds"
		sleep $DELAY
		joinChannelWithRetry $PEER $ORG
	else
		COUNTER=1
	fi
	verifyResult $res "After $MAX_RETRY attempts, peer${PEER}.org${ORG} has failed to Join the Channel"
}

installChaincode () {
	PEER=$1
	ORG=$2
	setGlobals $PEER $ORG
	VERSION=${3:-1.0}
	peer chaincode install -n mycc -v ${VERSION} -l ${LANGUAGE} -p ${CC_SRC_PATH} >&log.txt
	res=$?
	cat log.txt
	verifyResult $res "Chaincode installation on peer${PEER}.org${ORG} has Failed"
	echo "===================== Chaincode is installed on peer${PEER}.org${ORG} ===================== "
	echo
}

instantiateChaincode () {
	PEER=$1
	ORG=$2
	setGlobals $PEER $ORG
	VERSION=${3:-1.0}

	# while 'peer chaincode' command can get the orderer endpoint from the peer (if join was successful),
	# lets supply it directly as we know it using the "-o" option
	if [ -z "$CORE_PEER_TLS_ENABLED" -o "$CORE_PEER_TLS_ENABLED" = "false" ]; then
		peer chaincode instantiate -o orderer.example.com:7050 -C $CHANNEL_NAME -n mycc -l ${LANGUAGE} -v ${VERSION} -c '{"Args":["init","a","100","b","200"]}' -P "OR	('Org1MSP.peer','Org2MSP.peer')" >&log.txt
	else
		peer chaincode instantiate -o orderer.example.com:7050 --tls $CORE_PEER_TLS_ENABLED --cafile $ORDERER_CA -C $CHANNEL_NAME -n mycc -l ${LANGUAGE} -v 1.0 -c '{"Args":["init","a","100","b","200"]}' -P "OR	('Org1MSP.peer','Org2MSP.peer')" >&log.txt
	fi
	res=$?
	cat log.txt
	verifyResult $res "Chaincode instantiation on peer${PEER}.org${ORG} on channel '$CHANNEL_NAME' failed"
	echo "===================== Chaincode Instantiation on peer${PEER}.org${ORG} on channel '$CHANNEL_NAME' is successful ===================== "
	echo
}

upgradeChaincode () {
    PEER=$1
    ORG=$2
    setGlobals $PEER $ORG

    peer chaincode upgrade -o orderer.example.com:7050 --tls $CORE_PEER_TLS_ENABLED --cafile $ORDERER_CA -C $CHANNEL_NAME -n mycc -v 2.0 -c '{"Args":["init","a","90","b","210"]}' -P "OR ('Org1MSP.peer','Org2MSP.peer','Org3MSP.peer')"
    res=$?
    cat log.txt
    verifyResult $res "Chaincode upgrade on org${ORG} peer${PEER} has Failed"
    echo "===================== Chaincode is upgraded on org${ORG} peer${PEER} ===================== "
    echo
}

chaincodeQuery () {
  PEER=$1
  ORG=$2
  setGlobals $PEER $ORG
  EXPECTED_RESULT=$3
  echo "===================== Querying on peer${PEER}.org${ORG} on channel '$CHANNEL_NAME'... ===================== "
  local rc=1
  local starttime=$(date +%s)

  # continue to poll
  # we either get a successful response, or reach TIMEOUT
  while test "$(($(date +%s)-starttime))" -lt "$TIMEOUT" -a $rc -ne 0
  do
     sleep $DELAY
     echo "Attempting to Query peer${PEER}.org${ORG} ...$(($(date +%s)-starttime)) secs"
     peer chaincode query -C $CHANNEL_NAME -n mycc -c '{"Args":["query","a"]}' >&log.txt
     test $? -eq 0 && VALUE=$(cat log.txt | awk '/Query Result/ {print $NF}')
     test "$VALUE" = "$EXPECTED_RESULT" && let rc=0
  done
  echo
  cat log.txt
  if test $rc -eq 0 ; then
	echo "===================== Query on peer${PEER}.org${ORG} on channel '$CHANNEL_NAME' is successful ===================== "
  else
	echo "!!!!!!!!!!!!!!! Query result on peer${PEER}.org${ORG} is INVALID !!!!!!!!!!!!!!!!"
        echo "================== ERROR !!! FAILED to execute End-2-End Scenario =================="
	echo
	exit 1
  fi
}

chaincodeInvoke () {
	PEER=$1
	ORG=$2
	setGlobals $PEER $ORG
	# while 'peer chaincode' command can get the orderer endpoint from the peer (if join was successful),
	# lets supply it directly as we know it using the "-o" option
	if [ -z "$CORE_PEER_TLS_ENABLED" -o "$CORE_PEER_TLS_ENABLED" = "false" ]; then
		peer chaincode invoke -o orderer.example.com:7050 -C $CHANNEL_NAME -n mycc -c '{"Args":["invoke","a","b","10"]}' >&log.txt
	else
		peer chaincode invoke -o orderer.example.com:7050  --tls $CORE_PEER_TLS_ENABLED --cafile $ORDERER_CA -C $CHANNEL_NAME -n mycc -c '{"Args":["invoke","a","b","10"]}' >&log.txt
	fi
	res=$?
	cat log.txt
	verifyResult $res "Invoke execution on peer${PEER}.org${ORG} failed "
	echo "===================== Invoke transaction on peer${PEER}.org${ORG} on channel '$CHANNEL_NAME' is successful ===================== "
	echo
}
