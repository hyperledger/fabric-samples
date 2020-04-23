#!/bin/bash

function printHelp() {
  echo "用法:"
  echo "  start-installService-master.sh -m newInstall -p path"
  echo "      -m  - 指定安装模式，newInstall--全新模式安装, updateNetwork--扩缩容模式安装"
  echo "      -p  - 指定安装路径"
  echo "  start-installService-master.sh -h (print this message)"
  echo
}

if [ $# == 0 ]; then
  printHelp
  exit 0
fi

INIT_ORG=1
INSTALL_PATH=""
INSTALL_MODE=""

while getopts "h?m:p:" opt; do
  case "$opt" in
  h | \?)
    printHelp
    exit 0
    ;;
  m)
    INSTALL_MODE=$OPTARG
    ;;
  p)
    INSTALL_PATH=$OPTARG
    ;;
  esac
done

if [ "${INSTALL_MODE}x" == "x" ]; then
  echo
  echo "请指定安装模式"
  printHelp
  exit 0
elif [[ "${INSTALL_MODE}" != "newInstall" && "${INSTALL_MODE}" != "updateNetwork" ]]; then
  echo
  echo "请指定正确的安装模式"
  printHelp
  exit 0
fi

if [ "${INSTALL_MODE}" == "newInstall" ]; then
  bash ./init.sh ${INSTALL_MODE}
fi

if [ "${INSTALL_PATH}x" == "x" ]; then
  echo
  echo "请指定安装路径"
  exit 0
fi

which java
if [ "$?" -ne 0 ]; then
  echo "请先按转JDK并将其可执行目录放入环境变量PATH中。结束运行"
  exit 1
fi

echo "initOrg=${INIT_ORG}"
java -jar bcp-install.jar --init.config=$(pwd)/initconfig.propertise --init.dir=$(pwd) --init.yes=0 --global.master=1 --install.mode=${INSTALL_MODE} --install.path=${INSTALL_PATH}
