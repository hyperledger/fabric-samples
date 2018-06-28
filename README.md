# Hyperledger Fabric Samples

First of all start the three terminals.

## Terminal 1 Start the Fabric Network##
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



## License <a name="license"></a>

Hyperledger Project source code files are made available under the Apache License, Version 2.0 (Apache-2.0), located in the [LICENSE](LICENSE) file. Hyperledger Project documentation files are made available under the Creative Commons Attribution 4.0 International License (CC-BY-4.0), available at http://creativecommons.org/licenses/by/4.0/.
