#!/bin/bash

NODEHOST="$1"

docker stop $(docker ps -a | grep ${NODEHOST}* | awk '{print $1}')

docker rm $(docker ps -a | grep ${NODEHOST}* | awk '{print $1}')

docker rmi $(docker images | grep ${NODEHOST}* | awk '{print $1}')

docker volume rm $(docker volume list | grep ${NODEHOST}* | awk '{print $2}')