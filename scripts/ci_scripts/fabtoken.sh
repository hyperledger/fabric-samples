#!/bin/bash
#
# SPDX-License-Identifier: Apache-2.0
#

# docker container list - Check these from basic-network/docker-compose.yaml
CONTAINER_LIST=(peer0.org1 orderer ca)

logs() {

for CONTAINER in ${CONTAINER_LIST[*]}; do
    docker logs $CONTAINER.example.com >& $WORKSPACE/$CONTAINER-$1.log
    echo
done
# Write couchdb container logs into couchdb.log file
docker logs couchdb >& couchdb.log

}

copy_logs() {

# Call logs function
logs $2 $3

if [ $1 != 0 ]; then
    echo -e "\033[31m $2 test case is FAILED" "\033[0m"
    exit 1
fi
}

cd $WORKSPACE/$BASE_DIR/fabtoken || exit
export PATH=gopath/src/github.com/hyperledger/fabric-samples/bin:$PATH

LANGUAGE="javascript"

echo -e "\033[32m starting fabtoken test (${LANGUAGE})" "\033[0m"
./startFabric.sh
copy_logs $? fabtoken-start-script-${LANGUAGE}

pushd ${LANGUAGE}
npm install
node fabtoken.js
copy_logs $? fabtoken-${LANGUAGE}
popd

docker ps -aq | xargs docker rm -f
docker rmi -f $(docker images -aq dev-*)
echo -e "\033[32m finished fabtoken tests (${LANGUAGE})" "\033[0m"
