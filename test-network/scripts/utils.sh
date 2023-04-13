#!/bin/bash

C_RESET='\033[0m'
C_RED='\033[0;31m'
C_GREEN='\033[0;32m'
C_BLUE='\033[0;34m'
C_YELLOW='\033[1;33m'

# Print the usage message
function printHelp() {
  USAGE="$1"
  if [ "$USAGE" == "up" ]; then
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
    println "    -ccs <sequence>  - Chaincode definition sequence. Must be an integer, 1 (default), 2, 3, etc"
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
    println "    -ccs <sequence>  - Chaincode definition sequence. Must be an integer, 1 (default), 2, 3, etc"
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
  else
    println "Usage: "
    println "  network.sh <Mode> [Flags]"
    println "    Modes:"
    println "      \033[0;32mup\033[0m - Bring up Fabric orderer and peer nodes. No channel is created"
    println "      \033[0;32mup createChannel\033[0m - Bring up fabric network with one channel"
    println "      \033[0;32mcreateChannel\033[0m - Create and join a channel after the network is created"
    println "      \033[0;32mdeployCC\033[0m - Deploy a chaincode to a channel (defaults to asset-transfer-basic)"
    println "      \033[0;32mdown\033[0m - Bring down the network"
    println
    println "    Flags:"
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
    println "    Used with \033[0;32mnetwork.sh deployCC\033[0m"
    println "    -c <channel name> - Name of channel to deploy chaincode to"
    println "    -ccn <name> - Chaincode name."
    println "    -ccl <language> - Programming language of the chaincode to deploy: go, java, javascript, typescript"
    println "    -ccv <version>  - Chaincode version. 1.0 (default), v2, version3.x, etc"
    println "    -ccs <sequence>  - Chaincode definition sequence. Must be an integer, 1 (default), 2, 3, etc"
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
  fi
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
