#!/bin/bash

function printHelp() {
  echo "在调用前，需要设置 CRYPTTOGEN_FILE_PATH 环境变量，使其指向 cryptogen 工具路径"
  echo "用法:"
  echo "  generate.sh <mode>"
  echo "    <mode> - one of 'up', 'down'"
  echo "      - 'up'   - 生成 crypto-config 目录及证书"
  echo "      - 'down' - 删除 crypto-config 目录及证书"
  echo "      - 'extend' - 添加新节点的证书"
  echo "  generate.sh -h (print this message)"
  echo
  echo "示例:"
  echo "	export CRYPTTOGEN_FILE_PATH=$(pwd)../cryptogen && generate.sh up"
  echo "	export CRYPTTOGEN_FILE_PATH=$(pwd)../cryptogen && generate.sh down"
}

# Generates Org certs using cryptogen tool
function generateCerts() {
  echo ${CRYPTTOGEN_FILE_PATH}

  if [ ! -e "${CRYPTTOGEN_FILE_PATH}" ]; then
    echo "cryptogen 工具不存在. 结束运行"
    exit 1
  fi
  echo
  echo "##########################################################"
  echo "############### 使用 cryptogen 工具创建证书 ##############"
  echo "##########################################################"

  chmod +x ${CRYPTTOGEN_FILE_PATH}

  if [ -d "crypto-config" ]; then
    rm -Rf crypto-config
  fi
  set -x
  ${CRYPTTOGEN_FILE_PATH} generate --config=./crypto-config.yaml
  res=$?
  set +x
  if [ $res -ne 0 ]; then
    echo "创建证书失败..."
    exit 1
  fi
}

# Generates Org certs using cryptogen tool
function extendCerts() {
  echo ${CRYPTTOGEN_FILE_PATH}

  if [ ! -e "${CRYPTTOGEN_FILE_PATH}" ]; then
    echo "cryptogen 工具不存在. 结束运行"
    exit 1
  fi
  echo
  echo "##########################################################"
  echo "############### 使用 cryptogen 工具创建新节点证书 ##############"
  echo "##########################################################"

  chmod +x ${CRYPTTOGEN_FILE_PATH}

  #if [ -d "crypto-config" ]; then
  #  rm -Rf crypto-config
  #fi
  set -x
  ${CRYPTTOGEN_FILE_PATH} extend --config=./crypto-config.yaml
  res=$?
  set +x
  if [ $res -ne 0 ]; then
    echo "创建新节点证书失败..."
    exit 1
  fi
}

function removeCerts() {
  if [ -d "crypto-config" ]; then
    rm -Rf crypto-config
  fi
}

while getopts "h?" opt; do
  case "$opt" in
  h | \?)
    printHelp
    exit 0
    ;;
  esac
done

# Parse commandline args
MODE=$1

if [ "${MODE}" == "up" ]; then
  generateCerts
elif [ "${MODE}" == "down" ]; then ## Clear the network
  removeCerts
elif [ "${MODE}" == "extend" ]; then
  extendCerts
else
  printHelp
  exit 1
fi
