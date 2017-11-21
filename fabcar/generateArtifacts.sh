#!/bin/bash
#
# Copyright jinwoochu
# email : chaindevchu@gmail.com or cjw0672@naver.com
# If you have any questions, please email me
#
# SPDX-License-Identifier: Apache-2.0
#
# Exit on first error
set -e

# don't rewrite paths for Windows Git Bash users
export MSYS_NO_PATHCONV=1

starttime=$(date +%s)

# launch network; create channel and join peer to channel
cd ../basic-network
./generate.sh

printf "\nTotal setup execution time : $(($(date +%s) - starttime)) secs ...\n\n\n"

printf "#############################################\n"
printf "|   Plese update docker-compose.yaml file   |\n"
printf "#############################################\n\n"

printf "##########################################################################################################################################################################\n"
printf "1. cd ../basic-network/crypto-config/peerOrganizations/org1.example.com/ca/                                                                                              #\n"
printf "2. copy blabla_sk'file name(ex> 3a37b287f75176ab817f436b7bdd97559c73813028eab8226b12781f7a8a7ba4_sk)                                                                     #\n"
printf "3. open your docker-compose.yaml in basic-network directory                                                                                                              #\n"
printf "4. and edit FABRIC_CA_SERVER_CA_KEYFILE environment                                                                                                                      #\n"
printf "ex) FABRIC_CA_SERVER_CA_KEYFILE=/etc/hyperledger/fabric-ca-server-config/3a37b287f75176ab817f436b7bdd97559c73813028eab8226b12781f7a8a7ba4_sk << this is  blabla_sk file  #\n"
printf "                                                                                                                                                                         #\n"
printf "Thanks!! If you have any questions, please email chaindevchu@gmail.com                                                                                                   #\n"
printf "##########################################################################################################################################################################\n"
