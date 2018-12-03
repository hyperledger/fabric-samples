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

cd $BASE_FOLDER/fabric-samples/fabcar || exit
export PATH=gopath/src/github.com/hyperledger/fabric-samples/bin:$PATH

LANGUAGES="go javascript typescript"
for LANGUAGE in ${LANGUAGES}; do
    echo -e "\033[32m starting fabcar test (${LANGUAGE})" "\033[0m"
    # Start Fabric, and deploy the smart contract
    ./startFabric.sh ${LANGUAGE}
    copy_logs $? fabcar-${LANGUAGE}
    # If an application exists for this language, test it
    if [ -d ${LANGUAGE} ]; then
        pushd ${LANGUAGE}
        if [ ${LANGUAGE} = "javascript" ]; then
            COMMAND=node
            PREFIX=
            SUFFIX=.js
            npm install
        elif [ ${LANGUAGE} = "typescript" ]; then
            COMMAND=node
            PREFIX=dist/
            SUFFIX=.js
            npm install
            npm run build
        fi
        ${COMMAND} ${PREFIX}enrollAdmin${SUFFIX}
        copy_logs $? fabcar-${LANGUAGE}-enrollAdmin
        ${COMMAND} ${PREFIX}registerUser${SUFFIX}
        copy_logs $? fabcar-${LANGUAGE}-registerUser
        ${COMMAND} ${PREFIX}query${SUFFIX}
        copy_logs $? fabcar-${LANGUAGE}-query
        ${COMMAND} ${PREFIX}invoke${SUFFIX}
        copy_logs $? fabcar-${LANGUAGE}-invoke
        popd
    fi
    docker ps -aq | xargs docker rm -f
    docker rmi -f $(docker images -aq dev-*)
    echo -e "\033[32m finished fabcar test (${LANGUAGE})" "\033[0m"
done
