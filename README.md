# Hyperledger Fabric Samples

Please visit the installation instructions to ensure you have the correct prerequisites installed. Please use the version of the documentation that matches the version of the software you intend to use to ensure alignment.

Below is the sacc chaincode install, update, query example.Please go through to each step and first of all start the three terminals.

## Terminal 1 Start the Fabric Network ##
*To start the fabric network on your local machine, First navigate to the chaincode-docker-devmode directory and tag the all images as latest in the docker-compose-simple.yaml file.*

**docker-compose -f docker-compose-simple.yaml up**

*Above command download the all required images and start the network with the SingleSampleMSPSolo orderer profile and launches the peer in development mode. It also launches two additional containers one is chaincode and a CLI to interact with the chaincode.Running the command you will see the following*

>D:\fabric-samples-release-1.1\chaincode-docker-devmode>docker-compose -f docker-compose-simple.yaml  up
>Pulling orderer (hyperledger/fabric-orderer:x86_64-1.0.0)...
>x86_64-1.0.0: Pulling from hyperledger/fabric-orderer
>aafe6b5e13de: Pull complete
>0a2b43a72660: Pull complete

## Terminal 2 Run ChainCode Container. Build and Start ChainCode ##

**docker exec -it chaincode bash**

*You should see the following:*

 root@d2629980e76b:/opt/gopath/src/chaincode#
 
*Now, compile your chaincode:*

 > .chaincode/sacc# go build
 
*Now mention and run the chaincode on the peer:*

**CORE_PEER_ADDRESS=peer:7051 CORE_CHAINCODE_ID_NAME=xyz:0 ./sacc**

##  Terminal 3 - Use the chaincode ## 

We’ll leverage the CLI container to drive these calls.

**docker exec -it cli bash**

*You should see the following:*

 root@d2629980e76b:/opt/gopath/src/chaincode#

**peer chaincode install -p chaincodedev/chaincode/sacc -n xyz -v 0**

**peer chaincode instantiate -n xyz -v 0 -c '{"Args":["a","50"]}' -C myc**

*Now issue an invoke to change the value of “a” to “500”.*

**peer chaincode invoke -n xyz -c '{"Function":"set","Args":["a", "500"]}' -C myc**

*Finally, query a. We should see a value of 500.*

**peer chaincode query -n xyz -c '{"Function":"get","Args":["query","a"]}' -C myc**


## License <a name="license"></a>

Hyperledger Project source code files are made available under the Apache License, Version 2.0 (Apache-2.0), located in the [LICENSE](LICENSE) file. Hyperledger Project documentation files are made available under the Creative Commons Attribution 4.0 International License (CC-BY-4.0), available at http://creativecommons.org/licenses/by/4.0/.
