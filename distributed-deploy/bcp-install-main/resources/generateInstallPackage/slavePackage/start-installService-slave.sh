#!/bin/bash

function printHelp() {
  echo "用法:"
  echo "  start-installService-slave.sh -m http://ip:port -p path"
  echo "      -p  - 指定安装路径"
  echo "  start-installService-slave.sh (打印帮助信息)"
  echo
}

if [ $# == 0 ]; then
  printHelp
  exit 0
fi
INSTALL_PATH=""

while getopts "h?p:" opt; do
  case "$opt" in
  h | \?)
    printHelp
    exit 0
    ;;
  p)
    INSTALL_PATH=$OPTARG
    ;;
  esac
done

which java
if [ "$?" -ne 0 ]; then
  echo "请先按转JDK并将其可执行目录放入环境变量PATH中。结束运行"
  exit 1
fi

java -jar bcp-install.jar --global.master=0 --install.path=${INSTALL_PATH} --init.yes=0
