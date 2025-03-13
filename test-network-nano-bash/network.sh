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
    echo "    -o <orderer_type>  - Specify the orderer type. BFT or etcdraft. (defaults to etcdraft)"
    echo "    -ca                - Use CAs instead of cryptogen.  (defaults to cryptogen)"
    echo "    -h                 - Print this message"
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
  # shellcheck disable=SC2064
  trap networkStop 0 1 2 3 15

  echo "Creating logs directory..."
  mkdir -p "${PWD}"/logs

  if [ "${INCLUDE_CA}" = true ]; then
    echo "Starting CAs..."
    ./ordererca.sh  > ./logs/ordererca.log 2>&1 &
    ./org1ca.sh  > ./logs/org1ca.log 2>&1 &
    ./org2ca.sh  > ./logs/org2ca.log 2>&1 &

    echo 'Waiting for CAs to start...'
    waitForHealthzOK 9843 9844 9845
  fi

  if [ -d "${PWD}"/channel-artifacts ] && [ -d "${PWD}"/crypto-config ]; then
    echo "Using existing artifacts..."
    CREATE_CHANNEL=false
  else
    echo "Generating artifacts..."
    INCLUDE_CA_PARAM=""
    if [ "${INCLUDE_CA}" = true ]; then
      INCLUDE_CA_PARAM="-ca"
    fi
    ./generate_artifacts.sh "${ORDERER_TYPE}" "${INCLUDE_CA_PARAM}"
    CREATE_CHANNEL=true
  fi

  echo "Starting orderers..."
  ./orderer1.sh "${ORDERER_TYPE}" > ./logs/orderer1.log 2>&1 &
  ./orderer2.sh "${ORDERER_TYPE}" > ./logs/orderer2.log 2>&1 &
  ./orderer3.sh "${ORDERER_TYPE}" > ./logs/orderer3.log 2>&1 &

  ORDERER_HEALTHZ_PORTS="8443 8444 8445"

  #start one additional orderer for BFT consensus
  if [ "$ORDERER_TYPE" = "BFT" ]; then
    ./orderer4.sh "${ORDERER_TYPE}" > ./logs/orderer4.log 2>&1 &
    ORDERER_HEALTHZ_PORTS="${ORDERER_HEALTHZ_PORTS} 8450"
  fi

  echo 'Waiting for orderers to start...'
  # shellcheck disable=SC2086
  waitForHealthzOK ${ORDERER_HEALTHZ_PORTS}

  echo "Starting peers..."
  ./peer1.sh > ./logs/peer1.log 2>&1 &
  ./peer2.sh > ./logs/peer2.log 2>&1 &
  ./peer3.sh > ./logs/peer3.log 2>&1 &
  ./peer4.sh > ./logs/peer4.log 2>&1 &

  echo 'Waiting for peers to start...'
  waitForHealthzOK 8446 8447 8448 8449

  if [ "${CREATE_CHANNEL}" = "true" ]; then
    echo "Joining orderers to channel..."
    if [ "$ORDERER_TYPE" = "BFT" ]; then
      ./join_orderers.sh BFT
    else
      ./join_orderers.sh
    fi

    echo "Creating channel (peer1)..."
    . ./peer1admin.sh && ./join_channel.sh

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

waitForHealthzOK() {
  while [ $# -ge 1 ]; do
    HEALTHZ_STATUS=
    HEALTHZ_PORT="$1"
    shift

    while [ "${HEALTHZ_STATUS}" != 'OK' ]; do
      # Sleep before a retry
      if [ "${HEALTHZ_STATUS}" ]; then
        [ "${HEALTHZ_STATUS}" != 'OFFLINE' ] && echo "Healthz port ${HEALTHZ_PORT}: ${HEALTHZ_RESPONSE}"
        sleep 0.5
      fi

      HEALTHZ_RESPONSE=$(curl --silent "http://127.0.0.1:${HEALTHZ_PORT}/healthz" || echo '{"status":"OFFLINE"}')
      HEALTHZ_STATUS=$(echo "${HEALTHZ_RESPONSE}" | jq --raw-output '.status')
    done
  done
}

networkClean() {
  echo "Removing directories: channel-artifacts crypto-config data logs"
  rm -r "${PWD}"/channel-artifacts || true
  rm -r "${PWD}"/crypto-config || true
  rm -r "${PWD}"/data || true
  rm -r "${PWD}"/data_ca || true
  rm -r "${PWD}"/logs || true
}

# Parse commandline args

## Parse mode
if [ $# -lt 1 ] ; then
  printHelp
  exit 0
else
  MODE="$1"
  shift
fi

ORDERER_TYPE="etcdraft"
INCLUDE_CA=false

# parse flags
while [ $# -ge 1 ] ; do
  key="$1"
  case $key in
  -o )
    ORDERER_TYPE="$(echo "$2" | tr '[:upper:]' '[:lower:]')"
    shift

    if [ "${ORDERER_TYPE}" = bft ]; then
      ORDERER_TYPE=BFT
    fi

    if [ "${ORDERER_TYPE}" != etcdraft ] && [ "${ORDERER_TYPE}" != BFT ]; then
      echo "Unsupported orderer type: ${ORDERER_TYPE}" >&2
      printHelp "$MODE"
      exit 1
    fi
    ;;
  -ca )
    INCLUDE_CA=true
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

# Ensure peers are configured with the correct docker socket location, otherwise
# health checks will fail.
export CORE_VM_ENDPOINT
if [ -z "${DOCKER_HOST:-}" ]; then
  CORE_VM_ENDPOINT="$(docker context inspect --format '{{.Endpoints.docker.Host}}')"
else
  CORE_VM_ENDPOINT="${DOCKER_HOST}"
fi

if [ "$MODE" = "start" ]; then
  networkStart
elif [ "$MODE" = "clean" ]; then
  networkClean
else
  printHelp
  exit 1
fi
