#!/bin/bash

chmod +x $(pwd)/tools/linux/configtxgen
chmod +x $(pwd)/tools/linux/cryptogen
chmod +x $(pwd)/tools/linux/configtxlator

INSTALL_MODE=$1

which java
if [ "$?" -ne 0 ]; then
  echo "请先按转JDK并将其可执行目录放入环境变量PATH中。结束运行"
  exit 1
fi

if [ "${INSTALL_MODE}" == "newInstall" ]; then
  rm -rf ./fabric-net/cryptoAndConfig/crypto-config
  echo "rm -rf ./fabric-net/cryptoAndConfig/crypto-config"
  rm -rf ./fabric-net/cryptoAndConfig/configtx.yaml
  echo "rm -rf ./fabric-net/cryptoAndConfig/configtx.yaml"
  rm -rf ./fabric-net/cryptoAndConfig/crypto-config.yaml
  echo "rm -rf ./fabric-net/cryptoAndConfig/crypto-config.yaml"
  rm -rf ./fabric-net/dockerFile/*
  echo "rm -rf ./fabric-net/dockerFile/*"
  rm -rf ./bcp-install.mv.db
  echo "rm -rf ./bcp-install.mv.db"
fi

java -jar bcp-install.jar --init.config=$(pwd)/initconfig.propertise --init.dir=$(pwd) --init.yes=1 --global.master=1
