# Debugging Chaincode

In this sample we will employ the [Kubernetes Test Network](../README.md) to illustrate a scenario of
building, running, and debugging chaincode on a development workstation.

While this guide targets the Java [asset-transfer-basic](../../asset-transfer-basic/chaincode-java) sample, the approach 
may be applied to any sample and chaincode implementation language.

When debugging chaincode as a service, the chaincode process is launched on the local system, binding to a port 
on the host's network interface.  In this mode the developer has complete flexibility in determining how and where the 
process runs - it can be launched as a native binary from a CLI, attached to an active debugging session from an IDE,
as a Docker container, or even behind a reverse network proxy for diagnosing issues in a remote / cloud-based Fabric 
network.


## TL/DR

```
export PATH=${PWD}/test-network-k8s:$PATH

cd asset-transfer-basic/chaincode-java 

network kind
network cluster init 
```
```
network up 
network channel create
```
```
network chaincode deploy    asset-transfer-basic ${PWD}
```
```
network chaincode metadata  asset-transfer-basic
network chaincode invoke    asset-transfer-basic '{"Args":["InitLedger"]}'
network chaincode query     asset-transfer-basic '{"Args":["ReadAsset","asset1"]}' | jq 
```

## Detailed Guide

```shell
network down
network up
network channel create
```

```shell
# Build the chaincode docker image 
docker build -t fabric-samples/asset-transfer-basic/chaincode-java .

# Load the docker image directly to the KIND control plane.  
# (Alternately, build/tag/push the image to a remote container registry, e.g. localhost:5000 or ghcr.io) 
kind load docker-image fabric-samples/asset-transfer-basic/chaincode-java
```

```shell
# Assemble the chaincode package archive 
network chaincode package asset-transfer-basic asset-transfer-basic $PWD/build/asset-transfer.tgz

# Determine the ID for the chaincode package 
CORE_CHAINCODE_ID_NAME=$(network chaincode id $PWD/build/asset-transfer.tgz)

# Launch the chaincode in k8s as Deployment + Service 
network chaincode launch asset-transfer-basic $CORE_CHAINCODE_ID_NAME fabric-samples/asset-transfer-basic/chaincode-java

# Complete the chaincode lifecycle 
network chaincode install $PWD/build/asset-transfer.tgz 
network chaincode approve asset-transfer-basic $CORE_CHAINCODE_ID_NAME
network chaincode commit  asset-transfer-basic
```

```shell
# execute the smart contract by name 
network chaincode metadata  asset-transfer-basic
network chaincode invoke    asset-transfer-basic '{"Args":["InitLedger"]}'
network chaincode query     asset-transfer-basic '{"Args":["ReadAsset","asset1"]}'
```

```shell
kubectl -n test-network logs -f deployment/org1peer1-ccaas-asset-transfer-basic
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

Set the "address" attribute in the package connection.json descriptor and assemble the chaincode package:
```shell
export TEST_NETWORK_CHAINCODE_ADDRESS=host.docker.internal:9999

network cc package basic_1.0 asset-transfer-debug $PWD/build/asset-transfer-debug.tgz
```

*NOTE:* The Docker host alias `host.docker.internal` is not yet supported for Linux.
In Linux environments, as a workaround, you should specify the IP address of the host directly instead of `host.docker.internal`:

```shell
export TEST_NETWORK_CHAINCODE_ADDRESS=<YOUR_HOST_IP_ADDRESS>:9999

network cc package basic_1.0 asset-transfer-debug $PWD/build/asset-transfer-debug.tgz
```

### Launch

When chaincode is launched locally, it must declare the package ID in the environment as if the process had been managed
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
network cc activate asset-transfer-debug $PWD/build/asset-transfer-debug.tgz 
```

```shell
# execute the smart contract by name 
network cc metadata asset-transfer-debug
network cc invoke   asset-transfer-debug '{"Args":["InitLedger"]}'
network cc query    asset-transfer-debug '{"Args":["ReadAsset","asset1"]}'
```

## Tear Down

```shell
network down 
```
or
```shell
network unkind 
```


