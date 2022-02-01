# Running Chaincode as Service with the Test Network

The chaincode-as-a-service feature is a very useful and practical way to run 'Smart Contracts'. Traditionally the Fabric Peer has taken on the role of orchestrating the complete lifecycle of the chaincode. It required access to the Docker Daemon to create images, and start containers. Java, Node.js and Go chaincode frameworks were explicitly known to the peer including how they should be built and started.

As a result this makes it very hard to deploy into Kubernetes (K8S) style environments, or to run in any form of debug mode. Additionally, the code is being rebuilt by the peer therefore there is some degree of uncertainty about what dependencies have been pulled in.

Chaincode-as-service requires you to orchestrate the build and deployment phase yourself. Whilst this is an additional step, it gives control back. The Peer still requires a 'chaincode package' to be installed. In this case this doesn't contain code, but the information about where the chaincode is hosted. (Hostname,Port,TLS config etc)

## Fabric v2.4.1 Improvements

We need to use the latest 2.4.1 release as this contains some improvements to make this process easier. The core functionality is available in earlier releases but requires more configuration.

- The docker image for the peer contains a builder for chaincode-as-a-service preconfigured. This is named 'ccaasbuilder'. This removes the need to build your own external builder and repackage and configure the peer
- The `ccaasbuilder` applications are included in the binary tgz archive download for use in other circumstances. The `sampleconfig/core.yaml` is updated as well to refer to 'ccaasbuilder'
- The 2.4.1 Java Chaincode release has been updated to remove the need to write a custom bootstrap main class, similar  to the Node.js Chaincode. It is intended that this will be added to the go chaincode as well.

## End-to-end with the the test-network

The `test-network` and some of the chaincodes have been updated to support running chaincode-as-a-service. The commands below assume that you've got the latest fabric-samples cloned, along with the latest Fabric docker images.

It's useful to have two terminal windows open, one for starting the Fabric Network, and a second for monitoring all the docker containers.

In your 'monitoring' window, run this to watch all activity from the all the docker containers on the `fabric_test` network; this will monitor all the docker containers that are added to the `fabric-test` network. The network is usually created by the `./network.sh up` command, so remember to delay running this until at least the network is created. It is possible to precreate the network with `docker network create fabric-test` if you wish.

```bash
# from the fabric-samples repo
./test-network/monitordocker.sh
```

In the 'Fabric Network' window, start the test network

```bash
cd test-network
./network.sh up createChannel
```

You can run other variants of this command, eg to use CouchDB or CAs, without affecting the '-as-a-service' feature. The three keys steps are:

- Build a docker image of the contract. Both `/asset-transfer-basic/chaincode-typescript` and `/asset-transfer-basic/chaincode-java` have been updated with Dockerfiles
- Install, Approve and Commit a chaincode definition. This is unchanged, but the chaincode package contains connection information (hostname,port,tls certificates etc.), not code
- Start the docker container(s) containing the contract

Note that the order listed isn't mandatory. The key thing is that the containers are running before the first transaction is set by the peer. Remember that this could be on the `commit` if the `initRequired` flag is set.

This sequence can be run as follows

```bash
./network.sh deployCCAAS  -ccn basicts -ccp ../asset-transfer-basic/chaincode-typescript 
```

This is very similar to the `deployCC` command, it needs the name, and path. But also needs to have the port the chaincode container is going use. As each container is on the `fabric-test` network, you might wish to alter this so there are no collisions with other chaincode containers.

You should be able to see the contract starting in the monitoring window. There will be two containers running, one for org1 and one for org2. The container names contain the organization/peer and the name of the chaincode.

To test things are working you can invoke the 'Contract Metadata' function. For information on how to work as different organizations see [Interacting with the network](https://hyperledger-fabric.readthedocs.io/en/latest/test_network.html#interacting-with-the-network)

```bash
# Environment variables for Org1

export CORE_PEER_TLS_ENABLED=true
export CORE_PEER_LOCALMSPID="Org1MSP"
export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp
export CORE_PEER_ADDRESS=localhost:7051
export FABRIC_CFG_PATH=${PWD}/../config

# invoke the function
peer chaincode query -C mychannel -n basicts -c '{"Args":["org.hyperledger.fabric:GetMetadata"]}' | jq
```

If you don't have `jq` installed omit `| jq`.  The metadata shows the details of the deployed contract and is JSON, so jq makes it easier to read.  You can repeat the above commands for org2 to confirm that is working.

To run the Java example, change the `deployCCAAS` command as follows, This will create two new containers.

```bash
./network.sh deployCCAAS  -ccn basicj -ccp ../asset-transfer-basic/chaincode-java
```

### Troubleshooting

If the JSON structure passed in is badly formatted JSON this error will be in the peer log:

```
::Error: Failed to unmarshal json: cannot unmarshal string into Go value of type map[string]interface {} command=build
```

## How to configure each language

Each language can work in the '-as-a-service' mode. Note that the approaches here are based on the very latest libraries.
When starting the image you can also specify any of the TLS options or additional logging options for the respective chaincode libraries.

### Java

With the v2.4.1 Java Chaincode libraries, there are no code changes to make or build changes. The '-as-a-service' mode will be used if the environment variable `CHAINCODE_SERVER_ADDRESS` is set.

A sample docker run command could be as follows. The two key variables that are needed are the `CHAINCODE_SERVER_ADDRESS` and `CORE_CHAICODE_ID_NAME`

```bash
    docker run --rm -d --name peer0org1_assettx_ccaas  \
                  --network fabric_test \
                  -e CHAINCODE_SERVER_ADDRESS=0.0.0.0:9999 \
                  -e CORE_CHAINCODE_ID_NAME=<use package id here> \
                   assettx_ccaas_image:latest
```

### Node.js

For Node.js (JavaScript or TypeScript) chaincode, typically the `package.json` has `fabric-chaincode-node start` as the main start command. To run in the '-as-a-service' mode change this to `fabric-chaincode-node server --chaincode-address=$CHAINCODE_SERVER_ADDRESS --chaincode-id=$CHAINCODE_ID`

## Debugging the Chaincode

Running in the '-as-a-service' mode offers options, similar to how the Fabric 'dev' mode works on debugging code. The restrictions of the 'dev' mode don't apply.

There is an option `-ccaasdr false` that can be provided on the `deployCCAAS` command. This will _not_ build the docker image or start a docker container. It does output the commands it would have run.

Run this command, and you'll see similar output

```bash
./network.sh deployCCAAS  -ccn basicj -ccp ../asset-transfer-basic/chaincode-java -ccaasdr false
#....
Not building docker image; this the command we would have run
docker build -f ../asset-transfer-basic/chaincode-java/Dockerfile -t basicj_ccaas_image:latest --build-arg CC_SERVER_PORT=9999 ../asset-transfer-basic/chaincode-java
#....
Not starting docker containers; these are the commands we would have run
    docker run --rm -d --name peer0org1_basicj_ccaas                    --network fabric_test                   -e CHAINCODE_SERVER_ADDRESS=0.0.0.0:9999                   -e CHAINCODE_ID=basicj_1.0:59dcd73a14e2db8eab7f7683343ce27ac242b93b4e8075605a460d63a0438405 -e CORE_CHAINCODE_ID_NAME=basicj_1.0:59dcd73a14e2db8eab7f7683343ce27ac242b93b4e8075605a460d63a0438405                     basicj_ccaas_image:latest
```

Depending on your directory, and what you need to debug you might need to adjust these commands.

### Building the docker image

The first thing needed is to build the docker image. Remember that so long as the peer can connect to the hostname:port given in the `connection.json` the actual packaging of the chaincode is not important to the peer. You are at liberty to adjust the dockerfiles given hgere.

To manually build the docker image for the `asset-transfer-basic/chaincode-java`

```bash
docker build -f ../asset-transfer-basic/chaincode-java/Dockerfile -t basicj_ccaas_image:latest --build-arg CC_SERVER_PORT=9999 ../asset-transfer-basic/chaincode-java
```

### Starting the docker container

You need to start the docker container.

NodeJs for example, could be started like this

```bash
 docker run --rm -it -p 9229:9229 --name peer0org2_basic_ccaas --network fabric_test -e DEBUG=true -e CHAINCODE_SERVER_ADDRESS=0.0.0.0:9999 -e CHAINCODE_ID=basic_1.0:7c7dff5cdc43c77ccea028c422b3348c3c1fb5a26ace0077cf3cc627bd355ef0 -e CORE_CHAINCODE_ID_NAME=basic_1.0:7c7dff5cdc43c77ccea028c422b3348c3c1fb5a26ace0077cf3cc627bd355ef0 basic_ccaas_image:latest
```

Java for example, could be started like this

```bash
 docker run --rm -it --name peer0org1_basicj_ccaas -p 8000:8000 --network fabric_test -e DEBUG=true -e CHAINCODE_SERVER_ADDRESS=0.0.0.0:9999 -e CHAINCODE_ID=basicj_1.0:b014a03d8eb1898535e25b4dfeeb3f8244c9f07d91a06aec03e2d19174c45e4f -e CORE_CHAINCODE_ID_NAME=basicj_1.0:b014a03d8e
b1898535e25b4dfeeb3f8244c9f07d91a06aec03e2d19174c45e4f  basicj_ccaas_image:latest
```

For all languages please note:

- the name of the container needs to match what the peer has in the `connection.json`
- the peer is connecting to the chaincode container via the docker network. Therefore port 9999 does not need to be forwarded to the host
- If you are going to single step in a debugger, then you are likely to hit the Fabric transaction timeout value. By default this is 30 seconds, meaning the chaincode has to complete transactions in 30 seconds or less. In the `test-network/docker/docker-composer-test-net.yml` add `CORE_CHAINCODE_EXECUTETIMEOUT=300s` to the environment options of each peer.
- In the command above, the `-d` option has been removed from the command the test-network would have used, and has been replaced with `-it`. This means that docker container will not run in detached mode, and will run in the foreground.

For Node.js please note:

- Port 9229 is forwarded however - this is the debug port used by Node.js
- `-e DEBUG=true` will trigger the node runtime to be started in debug mode. This is encoded in the `docker/docker-entrypoint.sh` script - this is an example and you may wish to remove this in production images for security
- If you are using typescript, ensure that the typescript has been compiled with sourcemaps, otherwise a debugger will struggle matching up the source code.

For Java please note:

- Port 800 is forwarded, the debug port for the JVM
- `-e DEBUG=true` will trigger the node runtime to be started in debug mode. This is encoded in the `docker/docker-entrypoint.sh` script - this is an example and you may wish to remove this in production images for security
- In the java command with the option to start the debugger is `java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:8000 -jar /chaincode.jar`   Note the `0.0.0.0` as the debug port needs to be bound to all network adapters so the debugger can be attached from outside the container

## Running with multiple peers

In the traditional approach, each peer that the chaincode is approved on will have a container running the chaincode. With the '-as-a-service' approach we need to achieve the same architecture.

As the `connection.json` contains the address of the running chaincode container, it can be updated to ensure that each peer connects to a different container. However the as the `connection.json` in the chaincode package, Fabric mandates that the package id is consistent amongst all peers in an organization. To achieve that
the the external builder supports a template capability. The context from this template is taken from an environment variable set on each Peer. `CHAINCODE_AS_A_SERVICE_BUILDER_CONFIG`

We can define the address to be a template in the `connection.json`

```json
{
  "address": "{{.peername}}_assettransfer_ccaas:9999",
  "dial_timeout": "10s",
  "tls_required": false
}
```

In the peer's environment configuration we then set for org1's peer1

```bash
CHAINCODE_AS_A_SERVICE_BUILDER_CONFIG="{\"peername\":\"org1peer1\"}"
```

The external builder will then resolve this address to be `org1peer1_assettransfer_ccaas:9999` for the peer to use.

Each peer can have their own separate configuration, and therefore different addresses. The JSON string that is set can have any structure, so long as the templates (in golang template syntax) match.

Any value in the `connection.json` can be templated - but only the values and not the keys.
