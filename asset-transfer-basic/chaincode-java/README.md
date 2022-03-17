
## Basic asset transfer 

This project demonstrates the use of the Java SDK, running a basic asset transfer contract using the "chaincode as a service" 
pattern.


## Setup 

In this sample we will employ the [Kubernetes Test Network](../../test-network-k8s) to illustrate a scenario of 
building, running, and debugging chaincode on a development workstation.  

This project is also compatible with the legacy chaincode builder pipeline and the compose based test-network.
For additional details, see the [End-to-end with the test-network](../../test-network/CHAINCODE_AS_A_SERVICE_TUTORIAL.md#end-to-end-with-the-the-test-network)
documentation.

## [Quickstart](../../test-network-k8s#quickstart)

```shell
export PATH=${PWD}/../../test-network-k8s:$PATH 

network kind 

network up 
network channel create
```

```shell
network chaincode deploy  ${PWD}

network chaincode invoke  asset-transfer-basic '{"Args":["InitLedger"]}'
network chaincode query   asset-transfer-basic '{"Args":["ReadAsset","asset1"]}' | jq 
```

## Detailed Guide 

```shell
network down
network up
network channel create
```

```shell
# Build the chaincode docker image 
docker build -t hyperledger/fabric-samples/asset-transfer-basic/chaincode-java .

# Load the docker image directly to the KIND control plane.  
# (Alternately, build/tag/push the image to a remote container registry, e.g. localhost:5000) 
kind load docker-image hyperledger/fabric-samples/asset-transfer-basic/chaincode-java
```

```shell
# Assemble the chaincode package archive 
network chaincode package $PWD/ccpackage/ $PWD/build/asset-transfer.tgz

# Determine the ID for the chaincode package 
CORE_CHAINCODE_ID_NAME=$(network chaincode id $PWD/build/asset-transfer.tgz)

# Launch the chaincode in k8s as Deployment + Service 
network chaincode launch $PWD/build/asset-transfer.tgz

# Complete the chaincode lifecycle 
network chaincode install $PWD/build/asset-transfer.tgz 
network chaincode approve asset-transfer-basic $CORE_CHAINCODE_ID_NAME
network chaincode commit  asset-transfer-basic
```

```shell
# execute the smart contract by name 
network chaincode invoke  asset-transfer-basic '{"Args":["InitLedger"]}'
network chaincode query   asset-transfer-basic '{"Args":["ReadAsset","asset1"]}'
```

```shell
kubectl -n test-network logs -f deployment/org1peer1-cc-asset-transfer-basic
```

## Debugging 

### Build

```shell
./gradlew shadowJar
```
or 
```shell
docker build -t fabric-samples/asset-transfer-basic/chaincode-java . 
```


### Package

By instructing the peer to connect to chaincode at the Docker host alias `host.docker.internal`, pods running in 
Kubernetes will access the local process via a special loopback interface established by KIND. 

Set the "address" attribute in the project's [ccpackage/connection.json](ccpackage/connection.json) descriptor and assemble the chaincode package: 
```json
{
  "address": "host.docker.internal:9999",
}
```

```shell
network chaincode package $PWD/ccpackage/ $PWD/build/asset-transfer-debug.tgz
```

### Launch

When chaincode is launched locally, it must declare the package ID in the enviroment as if the process had been managed 
by the peer's chaincode lifecycle manager.  Calculate the package ID and start the chaincode, binding to port 9999
on the local system:

```shell
export CHAINCODE_SERVER_ADDRESS=0.0.0.0:9999
export CORE_CHAINCODE_ID_NAME=$(network chaincode id $PWD/build/asset-transfer-debug.tgz)

java -jar build/libs/chaincode.jar  
```

Or using the editor/debugger/IDE of your choice, create a launch target for `ContractMain.main()`, specifying the 
environment as above. 

Or launch the chaincode in a Docker container, binding to port 9999 on the host system:  

```shell
docker run \
  --rm \
  --name basic_1.0 \
  -p 9999:9999 \
  -e CHAINCODE_SERVER_ADDRESS \
  -e CORE_CHAINCODE_ID_NAME \
  fabric-samples/asset-transfer-basic/chaincode-java
```

### Approve, Invoke, and Query

After the contract main has launched, install, approve, commit, and invoke the chaincode:

```shell
# Complete the chaincode lifecycle 
export CORE_CHAINCODE_ID_NAME=$(network chaincode id $PWD/build/asset-transfer-debug.tgz)

network chaincode install $PWD/build/asset-transfer-debug.tgz 
network chaincode approve asset-transfer-debug $CORE_CHAINCODE_ID_NAME
network chaincode commit  asset-transfer-debug
```

```shell
# execute the smart contract by name 
network chaincode invoke  asset-transfer-debug '{"Args":["InitLedger"]}'
network chaincode query   asset-transfer-debug '{"Args":["ReadAsset","asset1"]}'
```

## Tear Down 

```shell
network down 
```
or 
```shell
network unkind 
```


