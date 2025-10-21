#!/usr/bin/env bash

C_RESET='\033[0m'
C_RED='\033[0;31m'
C_GREEN='\033[0;32m'
C_BLUE='\033[0;34m'
C_YELLOW='\033[1;33m'

# Print the usage message
function printHelp() {
  USAGE="$1"
  if [ "$USAGE" == "prereq" ]; then
    println "Usage: "
    println "  network.sh <Mode> [Flags]"
    println "    Modes:"
    println "      \033[0;32mprereq\033[0m - Install Fabric binaries and docker images"
    println
    println "    Flags:"
    println "    Used with \033[0;32mnetwork.sh prereq\033[0m:"
    println "    -i     FabricVersion (default: '2.5.14')"
    println "    -cai   Fabric CA Version (default: '1.5.15')"
    println
  elif [ "$USAGE" == "up" ]; then
    println "Usage: "
    println "  network.sh \033[0;32mup\033[0m [Flags]"
    println
    println "    Flags:"
    println "    -ca - Use Certificate Authorities to generate network crypto material"
    println "    -cfssl <use CFSSL> -  Use CFSSL CA to generate network crypto material"
    println "    -bft - Use Orderers with consensus type BFT (Not available in Fabric v2.x)"
    println "    -c <channel name> - Name of channel to create (defaults to \"mychannel\")"
    println "    -s <dbtype> - Peer state database to deploy: goleveldb (default) or couchdb"
    println "    -r <max retry> - CLI times out after certain number of attempts (defaults to 5)"
    println "    -d <delay> - CLI delays for a certain number of seconds (defaults to 3)"
    println "    -verbose - Verbose mode"
    println
    println "    -h - Print this message"
    println
    println " Possible Mode and flag combinations"
    println "   \033[0;32mup\033[0m -ca -r -d -s -verbose"
    println "   \033[0;32mup\033[0m -bft -r -d -s -verbose"
    println "   \033[0;32mup createChannel\033[0m -ca -c -r -d -s -verbose"
    println "   \033[0;32mup createChannel\033[0m -bft -c -r -d -s -verbose"
    println
    println " Examples:"
    println "   network.sh up createChannel -ca -c mychannel -s couchdb "
    println "   network.sh up createChannel -bft -c mychannel -s couchdb "
  elif [ "$USAGE" == "createChannel" ]; then
    println "Usage: "
    println "  network.sh \033[0;32mcreateChannel\033[0m [Flags]"
    println
    println "    Flags:"
    println "    -bft - Use Orderers with consensus type BFT (Not available in Fabric v2.x)"
    println "    -c <channel name> - Name of channel to create (defaults to \"mychannel\")"
    println "    -r <max retry> - CLI times out after certain number of attempts (defaults to 5)"
    println "    -d <delay> - CLI delays for a certain number of seconds (defaults to 3)"
    println "    -verbose - Verbose mode"
    println
    println "    -h - Print this message"
    println
    println " Possible Mode and flag combinations"
    println "   \033[0;32mcreateChannel\033[0m -bft -c -r -d -verbose"
    println
    println " Examples:"
    println "   network.sh createChannel -c channelName"
    println "   network.sh createChannel -bft"
  elif [ "$USAGE" == "deployCC" ]; then
    println "Usage: "
    println "  network.sh \033[0;32mdeployCC\033[0m [Flags]"
    println
    println "    Flags:"
    println "    -c <channel name> - Name of channel to deploy chaincode to"
    println "    -ccn <name> - Chaincode name."
    println "    -ccl <language> - Programming language of chaincode to deploy: go, java, javascript, typescript"
    println "    -ccv <version>  - Chaincode version. 1.0 (default), v2, version3.x, etc"
    println "    -ccs <sequence>  - Chaincode definition sequence.  Must be auto (default) or an integer, 1 , 2, 3, etc"
    println "    -ccp <path>  - File path to the chaincode."
    println "    -ccep <policy>  - (Optional) Chaincode endorsement policy using signature policy syntax. The default policy requires an endorsement from Org1 and Org2"
    println "    -cccg <collection-config>  - (Optional) File path to private data collections configuration file"
    println "    -cci <fcn name>  - (Optional) Name of chaincode initialization function. When a function is provided, the execution of init will be requested and the function will be invoked."
    println
    println "    -h - Print this message"
    println
    println " Possible Mode and flag combinations"
    println "   \033[0;32mdeployCC\033[0m -ccn -ccl -ccv -ccs -ccp -cci -r -d -verbose"
    println
    println " Examples:"
    println "   network.sh deployCC -ccn basic -ccp ../asset-transfer-basic/chaincode-javascript/ ./ -ccl javascript"
    println "   network.sh deployCC -ccn mychaincode -ccp ./user/mychaincode -ccv 1 -ccl javascript"
  elif [ "$USAGE" == "deployCCAAS" ]; then
    println "Usage: "
    println "  network.sh \033[0;32mdeployCCAAS\033[0m [Flags]"
    println
    println "    Flags:"
    println "    -c <channel name> - Name of channel to deploy chaincode to"
    println "    -ccn <name> - Chaincode name."
    println "    -ccv <version>  - Chaincode version. 1.0 (default), v2, version3.x, etc"
    println "    -ccs <sequence>  -  Chaincode definition sequence.  Must be auto (default) or an integer, 1 , 2, 3, etc"
    println "    -ccp <path>  - File path to the chaincode. (used to find the dockerfile for building the docker image only)"
    println "    -ccep <policy>  - (Optional) Chaincode endorsement policy using signature policy syntax. The default policy requires an endorsement from Org1 and Org2"
    println "    -cccg <collection-config>  - (Optional) File path to private data collections configuration file"
    println "    -cci <fcn name>  - (Optional) Name of chaincode initialization function. When a function is provided, the execution of init will be requested and the function will be invoked."
    println "    -ccaasdocker <true|false>  - (Optional) Default is true; the chaincode docker image will be built and containers started automatically. Set to false to control this manually"
    println
    println "    -h - Print this message"
    println
    println " Possible Mode and flag combinations"
    println "   \033[0;32mdeployCC\033[0m -ccn -ccv -ccs -ccp -cci -r -d -verbose"
    println
    println " Examples:"
    println "   network.sh deployCCAAS  -ccn basicj -ccp ../asset-transfer-basic/chaincode-java"
    println "   network.sh deployCCAAS  -ccn basict -ccp ../asset-transfer-basic/chaincode-typescript -ccaasdocker false"
  elif [ "$USAGE" == "cc" ] ; then
    println "Usage: "
    println "  network.sh cc <Mode> [Flags]"
    println
    println "    Modes:"
    println "      \033[0;32mlist\033[0m - list chaincodes installed on a peer and committed on a channel"
    println "      \033[0;32mpackage\033[0m - package a chaincode in tar format. Stores in directory packagedChaincode"
    println "      \033[0;32minvoke\033[0m - execute an invoke operation"
    println "      \033[0;32mquery\033[0m - execute an query operation"
    println
    println "    Flags:"
    println "    -org <number>     - Org number for the executing the command (1,2,etc) (default is 1)."
    println "    -c <channel name> - Name of channel"
    println "    -ccn <name>       - Chaincode name."
    println "    -ccl <language>   - Programming language of chaincode to deploy: go, java, javascript, typescript"
    println "    -ccv <version>    - Chaincode version. 1.0 (default), v2, version3.x, etc"
    println "    -ccp <path>       - File path to the chaincode."
    println "    -ccic <string>    - Chaincode invoke constructor."
    println "    -ccqc <string>    - Chaincode query constructor."
    println "    -h                - Print this message"
    println
    println "   Possible Mode and flag combinations"
    println "     \033[0;32mcc list\033[0m -org -verbose"
    println "     \033[0;32mcc package\033[0m -ccn -ccl -ccv -ccp -verbose"
    println "     \033[0;32mcc invoke\033[0m -org -c -ccic -verbose"
    println "     \033[0;32mcc query\033[0m -org -c -ccqc -verbose"
    println
    println " Examples:"
    println "   network.sh cc list -org 1"
    println "   network.sh cc package -ccn basic -ccp chaincode/asset-transfer-basic/go -ccv 1.0.0 -ccl go"
    println "   network.sh cc invoke -c channel1 -ccic '{\"Args\":[\"CreateAsset\",\"asset1\",\"red\",\"10\",\"fred\",\"500\"]}'"
    println "   network.sh cc query -c channel1 -ccqc '{\"Args\":[\"ReadAsset\",\"asset1\"]}'"
    println
    println " NOTE: Default settings can be changed in network.config"
    println
  else
    println "Usage: "
    println "  network.sh <Mode> [Flags]"
    println "    Modes:"
    println "      \033[0;32mprereq\033[0m - Install Fabric binaries and docker images"
    println "      \033[0;32mup\033[0m - Bring up Fabric orderer and peer nodes. No channel is created"
    println "      \033[0;32mup createChannel\033[0m - Bring up fabric network with one channel"
    println "      \033[0;32mcreateChannel\033[0m - Create and join a channel after the network is created"
    println "      \033[0;32mdeployCC\033[0m - Deploy a chaincode to a channel (defaults to asset-transfer-basic)"
    println "      \033[0;32mcc\033[0m - chaincode functions, use \"network.sh cc -h\" for options"
    println "      \033[0;32mdown\033[0m - Bring down the network"
    println
    println "    Flags:"
    println "    Used with \033[0;32mnetwork.sh prereq\033[0m"
    println "    -i     FabricVersion (default: '2.5.14')"
    println "    -cai   Fabric CA Version (default: '1.5.15')"
    println
    println "    Used with \033[0;32mnetwork.sh up\033[0m, \033[0;32mnetwork.sh createChannel\033[0m:"
    println "    -ca - Use Certificate Authorities to generate network crypto material"
    println "    -cfssl <use CFSSL> -  Use CFSSL CA to generate network crypto material"
    println "    -bft - Use Orderers with consensus type BFT (Not available in Fabric v2.x)"
    println "    -c <channel name> - Name of channel to create (defaults to \"mychannel\")"
    println "    -s <dbtype> - Peer state database to deploy: goleveldb (default) or couchdb"
    println "    -r <max retry> - CLI times out after certain number of attempts (defaults to 5)"
    println "    -d <delay> - CLI delays for a certain number of seconds (defaults to 3)"
    println "    -verbose - Verbose mode"
    println
    println "    Used with \033[0;32mnetwork.sh deployCC\033[0m:"
    println "    -c <channel name> - Name of channel to deploy chaincode to"
    println "    -ccn <name> - Chaincode name."
    println "    -ccl <language> - Programming language of the chaincode to deploy: go, java, javascript, typescript"
    println "    -ccv <version>  - Chaincode version. 1.0 (default), v2, version3.x, etc"
    println "    -ccs <sequence>  - Chaincode definition sequence.  Must be auto (default) or an integer, 1 , 2, 3, etc"
    println "    -ccp <path>  - File path to the chaincode."
    println "    -ccep <policy>  - (Optional) Chaincode endorsement policy using signature policy syntax. The default policy requires an endorsement from Org1 and Org2"
    println "    -cccg <collection-config>  - (Optional) File path to private data collections configuration file"
    println "    -cci <fcn name>  - (Optional) Name of chaincode initialization function. When a function is provided, the execution of init will be requested and the function will be invoked."
    println
    println "    -h - Print this message"
    println
    println " Possible Mode and flag combinations"
    println "   \033[0;32mup\033[0m -ca -r -d -s -verbose"
    println "   \033[0;32mup\033[0m -bft -r -d -s -verbose"
    println "   \033[0;32mup createChannel\033[0m -ca -c -r -d -s -verbose"
    println "   \033[0;32mup createChannel\033[0m -bft -c -r -d -s -verbose"
    println "   \033[0;32mcreateChannel\033[0m -bft -c -r -d -verbose"
    println "   \033[0;32mdeployCC\033[0m -ccn -ccl -ccv -ccs -ccp -cci -r -d -verbose"
    println
    println " Examples:"
    println "   network.sh up createChannel -ca -c mychannel -s couchdb"
    println "   network.sh createChannel -c channelName"
    println "   network.sh deployCC -ccn basic -ccp ../asset-transfer-basic/chaincode-javascript/ -ccl javascript"
    println "   network.sh deployCC -ccn mychaincode -ccp ./user/mychaincode -ccv 1 -ccl javascript"
    println
    println " NOTE: Default settings can be changed in network.config"
  fi
}

function installPrereqs() {

  infoln "installing prereqs"

  FILE=../install-fabric.sh
  if [ ! -f $FILE ]; then
    curl -sSLO https://raw.githubusercontent.com/hyperledger/fabric/main/scripts/install-fabric.sh && chmod +x install-fabric.sh
    cp install-fabric.sh ..
  fi

  IMAGE_PARAMETER=""
  if [ "$IMAGETAG" != "default" ]; then
    IMAGE_PARAMETER="-f ${IMAGETAG}"
  fi

  CA_IMAGE_PARAMETER=""
  if [ "$CA_IMAGETAG" != "default" ]; then
    CA_IMAGE_PARAMETER="-c ${CA_IMAGETAG}"
  fi

  cd ..
  ./install-fabric.sh ${IMAGE_PARAMETER} ${CA_IMAGE_PARAMETER} docker binary

}

# println echos string
function println() {
  echo -e "$1"
}

# errorln echos i red color
function errorln() {
  println "${C_RED}${1}${C_RESET}"
}

# successln echos in green color
function successln() {
  println "${C_GREEN}${1}${C_RESET}"
}

# infoln echos in blue color
function infoln() {
  println "${C_BLUE}${1}${C_RESET}"
}

# warnln echos in yellow color
function warnln() {
  println "${C_YELLOW}${1}${C_RESET}"
}

# fatalln echos in red color and exits with fail status
function fatalln() {
  errorln "$1"
  exit 1
}

export -f errorln
export -f successln
export -f infoln
export -f warnln
