#!/bin/bash

function printHelp() {
  echo "用法:"
  echo "  start-installService-master.sh -o 0 -m newInstall -p path"
  echo "      -c  - 指定本节点加入的链名"
  echo "      -o  - 指定orderer"
  echo "      -O  - 指定orderer的CA证书路径"
  echo "      -p  - 指定peer的域名与端口"
  echo "      -r  - 指定peer的Tls的CA证书路径"
  echo "      -e  - 指定peer的Tls的证书路径"
  echo "      -k  - 指定peer的Tls的私钥路径"
  echo "  newPeerJoinChannel.sh -h (print this message)"
  echo
}

if [ $# == 0 ]; then
  printHelp
  exit 0
fi

ORDERERADDRESS=""
CHANNEL_NAME=""
ORDERER_CA=""

PEER_ADDRESS=""
PEER_TLS_ROOTCERT_FILE=""
PEER_TLS_CERT_FILE=""
PEER_TLS_KEY_FILE=""

while getopts "h?c:o:O:p:r:e:k:" opt; do
  case "$opt" in
  h | \?)
    printHelp
    exit 0
    ;;
  c)
    CHANNEL_NAME=$OPTARG
    ;;
  o)
    ORDERERADDRESS=$OPTARG
    ;;
  O)
    ORDERER_CA=$OPTARG
    ;;
  p)
    PEER_ADDRESS=$OPTARG
    ;;
  r)
    PEER_TLS_ROOTCERT_FILE=$OPTARG
    ;;
  e)
    PEER_TLS_CERT_FILE=$OPTARG
    ;;
  k)
    PEER_TLS_KEY_FILE=$OPTARG
    ;;
  esac
done

# docker exec cli bash scripts/script.sh $CHANNEL_NAME $CLI_DELAY $LANGUAGE $CLI_TIMEOUT $VERBOSE $NO_CHAINCODE

export CORE_PEER_ADDRESS=${ORDERERADDRESS}
peer channel fetch 0 ${CHANNEL_NAME}.block -o ${ORDERERADDRESS} -c ${CHANNEL_NAME} --tls --cafile ${ORDERER_CA}

export CORE_PEER_ADDRESS=${PEER_ADDRESS}
export CORE_PEER_TLS_ROOTCERT_FILE=${PEER_TLS_ROOTCERT_FILE}
export CORE_PEER_TLS_CERT_FILE=${PEER_TLS_CERT_FILE}
export CORE_PEER_TLS_KEY_FILE=${PEER_TLS_KEY_FILE}
peer channel join -b ${CHANNEL_NAME}.block
