# Working with Chaincode 

In this guide we will launch the Asset Transfer Basic chaincode "as a service" on the Kubernetes test network.
In addition, we will demonstrate how to connect the test network to a chaincode process running on your local 
machine as a local binary, attached in an IDE debugger, or in a Docker container. 

## TL/DR : 
```shell
$ ./network chaincode deploy 
✅ - Packaging chaincode folder chaincode/asset-transfer-basic ...
✅ - Transferring chaincode archive to org1 ...
✅ - Installing chaincode for org org1 ...
✅ - Launching chaincode container "ghcr.io/hyperledgendary/fabric-ccaas-asset-transfer-basic" ...
✅ - Activating chaincode basic_1.0:5e0c4db62c1f91599f58dcbfe2c37566453b1e02933646c49ba46f196723cc30 ...
🏁 - Chaincode is ready.
```

```shell
$ ./network chaincode invoke '{"Args":["CreateAsset","1","blue","35","tom","1000"]}' 
2021-10-03 17:23:43.508 UTC [chaincodeCmd] chaincodeInvokeOrQuery -> INFO 001 Chaincode invoke successful. result: status:200 

$ ./network chaincode query '{"Args":["ReadAsset","1"]}' | jq 
{
  "ID": "1",
  "color": "blue",
  "size": 35,
  "owner": "tom",
  "appraisedValue": 1000
}
```

## Running Smart Contracts on Kubernetes 

In the Kubernetes Test Network, smart contracts are developed with the [Chaincode as a Service](link)
pattern, relying on an embedded [External Builder](link) to avoid the use of a Docker daemon. With 
Chaincode-as-a-Service, smart contracts are deployed to Kubernetes as `Services`,
`Deployments`, and `Pods`.  When invoking smart contracts, the Peer network connects to the grpc receiver
through the port exposed by the chaincode's Kube `Service` as described in the chaincode 
connection.json. 

Before installing chaincode to the network, a smart contract must: 

- Utilize the `ChaincodeServer` grpc receiver, as described in the [Fabric Operations
  Guide](https://hyperledger-fabric.readthedocs.io/en/latest/cc_service.html#writing-chaincode-to-run-as-an-external-service).

- Run as a Docker image published to a container registry.

- Maintain a connection.json and metadata.json files in the `chaincode/$CHAINCODE_NAME` folder.  

- Accept the `CHAINCODE_ID` environment variable: _CHAINCODE_LABEL:sha_256(chaincode.tar.gz)_.


## Deploying Chaincode to the Network 
```shell
✅ - Packaging chaincode "asset-transfer-basic" archive ... 
✅ - Deploying chaincode "asset-transfer-basic" for org org1 ...
```

When working with chaincode, the `./network` script includes two parameters that define the Docker image 
launched in the cluster and the chaincode metadata: 

- `${TEST_NETWORK_CHAINCODE_NAME:-asset-transfer-basic}` refers to the _name_ associated with the chaincode.  
  While packaging and deploying to the network, the `scripts/chaincode.sh` script uses this string to search 
  the local `/chaincode` folder for associated metadata and connection json descriptor files.


- `${TEST_NETWORK_CHAINCODE_IMAGE:-ghcr.io/hyperledgendary/fabric-ccaas-asset-transfer-basic}` defines the 
  container image that will be used when running the chaincode in Kubernetes. 
  

To deploy the chaincode, the network script will: 

1.  Read the `connection.json` and `metadata.json` files from the `/chaincode/${TEST_NETWORK_CHAINCODE_NAME` 
    folder, bundling the files into a chaincode tar.gz archive.
    

2.  `kubectl cp` the chaincode archive from the local file system to the organization's persistent volume storage. 


3.  Install the chaincode archive on a peer in the organization: 
```shell
  export CORE_PEER_ADDRESS='${org}'-peer1:7051
  peer lifecycle chaincode install chaincode/asset-transfer-basic.tgz
```


4. In typical Fabric operations, the output of the `chaincode install` command includes a generated ID of the 
   chaincode archive printed to standard out.  This ID is manually inspected and transcribed by the 
   network operator when executing subsequent commands with the network peers.  To avoid scraping the 
   output of the installation command, the test network scripts precompute the chaincode ID 
   as the `sha256` checksum of the tar.gz archive. 


5. The chaincode docker [image is launched](../kube/org1/org1-cc-asset-transfer-basic.yaml) as a Kubernetes 
   `Deployment` specifying _CHAINCODE_ID=sha-256(archive)_ in the environment and binding a `Service` port 9999 
   within the namespace.  When the network sends messages to the chaincode process, it will use the host URL as 
   defined in the `connection.json`, connecting to the kubernetes `Service` URL and `Deployment`.
   

6.  Finally, the Admin CLI issues a series of peer commands to approve and commit the chaincode for the org: 

```shell
  export CORE_PEER_ADDRESS='${org}'-peer1:7051
  
  peer lifecycle \
    chaincode approveformyorg \
    --channelID '${CHANNEL_NAME}' \
    --name '${CHAINCODE_NAME}' \
    --version 1 \
    --package-id '${cc_id}' \
    --sequence 1 \
    -o org0-orderer1:6050 \
    --tls --cafile /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/msp/tlscacerts/org0-tls-ca.pem
  
  peer lifecycle \
    chaincode commit \
    --channelID '${CHANNEL_NAME}' \
    --name '${CHAINCODE_NAME}' \
    --version 1 \
    --sequence 1 \
    -o org0-orderer1:6050 \
    --tls --cafile /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/msp/tlscacerts/org0-tls-ca.pem
```

## Invoking and Querying the Chaincode 

Once the chaincode service has been deployed to the cluster, and the peers have approved the chaincode,
the test scripts can issue adhoc invoke, query, and metadata requests to the network: 

### Invoke 
```shell
$ ./network chaincode invoke '{"Args":["CreateAsset","1","blue","35","tom","1000"]}' 
2021-10-03 17:23:43.508 UTC [chaincodeCmd] chaincodeInvokeOrQuery -> INFO 001 Chaincode invoke successful. result: status:200 
```

### Query
```shell
$ ./network chaincode query '{"Args":["ReadAsset","1"]}' | jq 
{
  "ID": "1",
  "color": "blue",
  "size": 35,
  "owner": "tom",
  "appraisedValue": 1000
}
```

### Describe 
```shell
$ ./network chaincode metadata | jq | head 
{
  "info": {
    "title": "undefined",
    "version": "latest"
  },
  "contracts": {
    "SmartContract": {
      "info": {
        "title": "SmartContract",
        "version": "latest"
```


## Build a Chaincode Docker Image

Before chaincode can be started in the network, it must be compiled, linked with the grpc `ChaincodeServer`,
embedded into a Docker image, and pushed to a container registry visible to the Kubernetes cluster.

By default, the `./network` script will launch the [asset-transfer-basic](../../asset-transfer-basic/chaincode-external)
chaincode.  When the test network installs this chaincode, there is no need to build a custom Docker image as it
has previously been uploaded to a public container registry.

As an exercise, we recommend making some updates to the asset transfer basic chaincode and then running the
modified smart contract on your local Kubernetes cluster.  For instance, the current version of the
[assetTransfer.go](../../asset-transfer-basic/chaincode-external/assetTransfer.go) code is completely
silent, printing nothing to the log when functions are invoked in the container.  Try adding some debugging
information to the stdout of this process, bundling into a Docker image, and pushing the docker
image to the local development container registry.

1. Add some print statements to assetTransfer.go.  E.g.:
```java 
    fmt.Printf("reading asset %s\n", id)
```

2.  Build the docker image locally with:
```shell
docker build -t asset-transfer-basic ../asset-transfer-basic/chaincode-external 
```

3. Override the test network's default chaincode image, pointing to our local container registry: 
```shell
export TEST_NETWORK_CHAINCODE_IMAGE=localhost:5000/asset-transfer-basic
```

3. Publish the custom image to the local registry: 

```shell
docker tag asset-transfer-basic $TEST_NETWORK_CHAINCODE_IMAGE 
docker push $TEST_NETWORK_CHAINCODE_IMAGE
```


## Debugging Chaincode

One of the most compelling features of Fabric's _Chaincode-as-a-Service_ pattern is that when the peer connects to a 
chaincode URL, it can connect back to a port on the local host.  Instead of connecting to a pod running in a 
container within Kubernetes, we can simply connect to a native binary running in a debugger, an IDE, or docker image
running locally!

Using a singular framework, we can employ this method to enable _rapid_ **edit/test/debug cycles** when authoring 
code, **verify** docker images generated by a CI/CD pipeline, and run integration tests on a local Kubernetes.

For example, we can deploy the basic asset transfer smart contract with a [connection.json](../chaincode/asset-transfer-basic-debug/connection.json)
referencing a service bound to the Docker network's IP address for the local host: 
```json
{
  "address": "host.docker.internal:9999",
}
```
When the test network opens a TCP socket to the chaincode process, the connection will be made from containers 
running within Kubernetes to the port opened on the local system.  Let's employ this to technique by running a 
chaincode endpoint in a local Docker container, native binary, or IDE debugger: 


0.  Edit assetTransfer.go and [Build the Chaincode Image](#build-a-chaincode-docker-image)


1.  Bring up the test network with: 
```shell
$ ./network up 
$ ./network channel create 
```

2.  Install the debug chaincode archive, using a connection to localhost:9999 : 
```shell
$ export TEST_NETWORK_CHAINCODE_NAME=asset-transfer-basic-debug
$ export TEST_NETWORK_CHAINCODE_IMAGE=localhost:5000/asset-transfer-basic

$ ./network chaincode install
Installing chaincode "asset-transfer-basic-debug":
✅ - Packaging chaincode folder chaincode/asset-transfer-basic-debug ...
✅ - Transferring chaincode archive to org1 ...
✅ - Installing chaincode for org org1 ...
🏁 - Chaincode is installed with CHAINCODE_ID=basic_1.0:159ed2f227586f40c5804e157919903fda2b861488f35eefb365eb9d85a73da3
```

3. Set the `CHAINCODE_ID` and launch the chaincode binding to localhost:9999: 
```shell
$ export CHAINCODE_ID=basic_1.0:159ed2f227586f40c5804e157919903fda2b861488f35eefb365eb9d85a73da3

$ docker run \
  --rm \
  --name asset-transfer-basic-debug \
  -e CHAINCODE_ID \
  -e CHAINCODE_SERVER_ADDRESS=0.0.0.0:9999 \
  -p 9999:9999 \
  localhost:5000/asset-transfer-basic
```

4.  Activate the chaincode (commit and approve on the peer): 
```shell
$ ./network chaincode activate 
```

When the peer communicates with chaincode in this fashion, the network will reach out to the grpc server 
bound to the localhost:9999, rather than connecting to services locked up behind the wall of Kubernetes 
networking. 

As an exercise, try using this approach to: 

- introduce some `fmt.Printf` logging output to the chaincode, attaching to a process running locally in an IDE / debugger.
- build your local modifications into a docker container, publishing locally to localhost:5000/asset-transfer-basic 
- test your local modifications by running a chaincode referencing the image hosted in the local container registry.


## Next Steps: 

[Writing a Blockchain Application](APPLICATIONS.md)