#!/usr/bin/env bash

# This script uses the logspout and http stream tools to let you watch the docker containers
# in action.
#
# More information at https://github.com/gliderlabs/logspout/tree/master/httpstream

if [ -z "$1" ]; then
   DOCKER_NETWORK=fabric_test
else
   DOCKER_NETWORK="$1"
fi

if [ -z "$2" ]; then
   PORT=8000
else
   PORT="$2"
fi

echo Starting monitoring on all containers on the network ${DOCKER_NETWORK}

docker kill logspout 2> /dev/null 1>&2 || true
docker rm logspout 2> /dev/null 1>&2 || true

trap "docker kill logspout" SIGINT

docker run -d --rm --name="logspout" \
	--volume=/var/run/docker.sock:/var/run/docker.sock \
	--publish=127.0.0.1:${PORT}:80 \
	--network  ${DOCKER_NETWORK} \
	gliderlabs/logspout
sleep 3
curl http://127.0.0.1:${PORT}/logs
