#!/bin/bash
#
# SPDX-License-Identifier: Apache-2.0
#
function _exit(){
    printf "Exiting:%s\n" "$1"
    exit -1
}

# Where am I?
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"

cd "${DIR}/organization/digibank/configuration/cli"
docker-compose -f docker-compose.yml up -d cliDigiBank

echo "

 Install and Instantiate a Smart Contract as 'Magnetocorp'

 
 Run Applications in either langauage (can be different from the Smart Contract)

 JavaScript Client Aplications:

 To add identity to the wallet:   node addToWallet.js
    < issue the paper run as Magnetocorp>
 To buy the paper             :   node buy.js
 To redeem the paper          :   node redeem.js

 Java Client Applications:

 (remember to build the Java first with 'mvn clean package')

    < issue the paper run as Magnetocorp>
 To buy the paper             :   node buy.js
 To redeem the paper          :   node redeem.js

"
echo "Suggest that you change to this dir>  cd ${DIR}/organization/digibank"