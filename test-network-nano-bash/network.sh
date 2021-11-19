#!/usr/bin/env sh
#
# SPDX-License-Identifier: Apache-2.0
#
set -eu

# Print the usage message
printHelp() {
  USAGE="${1:-}"
  if [ "$USAGE" = "start" ]; then
    echo "Usage: "
    echo "  network.sh start [Flags]"
    echo
    echo "  Starts the test network"
    echo
    echo "    Flags:"
    echo "    -d <delay> - CLI delays for a certain number of seconds (defaults to 3)"
    echo "    -h - Print this message"
  elif [ "$USAGE" = "clean" ]; then
    echo "Usage: "
    echo "  network.sh clean [Flags]"
    echo
    echo "  Cleans the test network configuration and data files"
    echo
    echo "    Flags:"
    echo "    -h - Print this message"
  else
    echo "Usage: "
    echo "  network.sh <Mode> [Flags]"
    echo "    Modes:"
    echo "      start - Starts the test network"
    echo "      clean - Cleans the test network configuration and data files"
    echo
    echo "    Flags:"
    echo "    -h - Print this message"
    echo
    echo " Examples:"
    echo "   network.sh start"
  fi
}

networkStop() {
  echo "Stopping Fabric network..."
  trap " " 0 1 2 3 15 && kill -- -$$
  wait
  echo "Fabric network stopped."
}

networkStart() {
  : "${CLI_DELAY:=5}"

  # shellcheck disable=SC2064
  trap networkStop 0 1 2 3 15

  if [ -d "${PWD}"/channel-artifacts ] && [ -d "${PWD}"/crypto-config ]; then
    echo "Using existing artifacts..."
    CREATE_CHANNEL=false
  else
    echo "Generating artifacts..."
    ./generate_artifacts.sh
    CREATE_CHANNEL=true
  fi

  echo "Creating logs directory..."
  mkdir -p "${PWD}"/logs

  echo "Starting orderers..."
  ./orderer1.sh > ./logs/orderer1.log 2>&1 &
  ./orderer2.sh > ./logs/orderer2.log 2>&1 &
  ./orderer3.sh > ./logs/orderer3.log 2>&1 &

  echo "Waiting ${CLI_DELAY}s..."
  sleep ${CLI_DELAY}

  echo "Starting peers..."
  ./peer1.sh > ./logs/peer1.log 2>&1 &
  ./peer2.sh > ./logs/peer2.log 2>&1 &
  ./peer3.sh > ./logs/peer3.log 2>&1 &
  ./peer4.sh > ./logs/peer4.log 2>&1 &

  echo "Waiting ${CLI_DELAY}s..."
  sleep ${CLI_DELAY}

  if [ "${CREATE_CHANNEL}" = "true" ]; then
    echo "Creating channel (peer1)..."
    . ./peer1admin.sh && ./create_channel.sh

    echo "Joining channel (peer2)..."
    . ./peer2admin.sh && ./join_channel.sh

    echo "Joining channel (peer3)..."
    . ./peer3admin.sh && ./join_channel.sh

    echo "Joining channel (peer4)..."
    . ./peer4admin.sh && ./join_channel.sh
  fi

  echo "Fabric network running. Use Ctrl-C to stop."

  wait
}

networkClean() {
  echo "Removing directories: channel-artifacts crypto-config data logs"
  rm -r "${PWD}"/channel-artifacts || true
  rm -r "${PWD}"/crypto-config || true
  rm -r "${PWD}"/data || true
  rm -r "${PWD}"/logs || true
}

# Parse commandline args

## Parse mode
if [ $# -lt 1 ] ; then
  printHelp
  exit 0
else
  MODE=$1
  shift
fi

# parse flags
while [ $# -ge 1 ] ; do
  key="$1"
  case $key in
  -d )
    CLI_DELAY="$2"
    shift
    ;;
  -h )
    printHelp "$MODE"
    exit 0
    ;;
  * )
    echo "Unknown flag: $key"
    printHelp
    exit 1
    ;;
  esac
  shift
done

if [ "$MODE" = "start" ]; then
  networkStart
elif [ "$MODE" = "clean" ]; then
  networkClean
else
  printHelp
  exit 1
fi
