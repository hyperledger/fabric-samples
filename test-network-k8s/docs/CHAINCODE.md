# Working with Chaincode 

In this guide we will launch the Asset Transfer Basic chaincode "as a service" on the Kubernetes test network.
In addition, we will demonstrate how to connect the test network to a chaincode process running on your local 
machine as a local binary, attached in an IDE debugger, or in a Docker container. 

## TL/DR : 
```shell
$ ./network chaincode deploy asset-transfer-basic ../asset-transfer-basic/chaincode-java 
Deploying chaincode
✅ - Building chaincode image asset-transfer-basic ...
✅ - Publishing chaincode image localhost:5000/asset-transfer-basic ...
✅ - Packaging ccaas chaincode asset-transfer-basic ...
✅ - Launching chaincode container "localhost:5000/asset-transfer-basic" ...
✅ - Launching chaincode container "localhost:5000/asset-transfer-basic" ...
✅ - Installing chaincode for org org1 peer peer1 ...
✅ - Installing chaincode for org org1 peer peer2 ...
✅ - Approving chaincode asset-transfer-basic with ID asset-transfer-basic:eb972467417ca827115c3a6a2629fc14e18741c0613a2a0a78290e3b643a50 ...
✅ - Committing chaincode asset-transfer-basic ...
🏁 - Chaincode is ready.
```

```shell
$ ./network chaincode invoke asset-transfer-basic '{"Args":["CreateAsset","1","blue","35","tom","1000"]}' 
2022-06-23 06:12:13.150 UTC 0001 INFO [chaincodeCmd] chaincodeInvokeOrQuery -> Chaincode invoke successful. result: status:200 payload:"{\"owner\":\"tom\",\"color\":\"blue\",\"size\":35,\"appraisedValue\":1000,\"assetID\":\"1\"}"

$ ./network chaincode query asset-transfer-basic '{"Args":["ReadAsset","1"]}' | jq  
{
  "ID": "1",
  "color": "blue",
  "size": 35,
  "owner": "tom",
  "appraisedValue": 1000
}
```

## Running Smart Contracts on Kubernetes 

In the Kubernetes Test Network, smart contracts are developed with the [Chaincode as a Service](https://hyperledger-fabric.readthedocs.io/en/latest/cc_service.html)
pattern, relying on an embedded [External Builder](https://hyperledger-fabric.readthedocs.io/en/latest/cc_launcher.html) to avoid the use of a Docker daemon. With 
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

1.  Read the `connection.json` and `metadata.json` files from the `/chaincode/${TEST_NETWORK_CHAINCODE_NAME}` 
    folder, bundling the files into a chaincode tar.gz archive.

2.  Install the chaincode archive on a peer in the organization: 
```shell
  export CORE_PEER_ADDRESS='${org}'-peer1:7051
  peer lifecycle chaincode install chaincode/asset-transfer-basic.tgz
```


3. In typical Fabric operations, the output of the `chaincode install` command includes a generated ID of the 
   chaincode archive printed to standard out.  This ID is manually inspected and transcribed by the 
   network operator when executing subsequent commands with the network peers.  To avoid scraping the 
   output of the installation command, the test network scripts precompute the chaincode ID 
   as the `sha256` checksum of the tar.gz archive. 


4. The chaincode docker [image is launched using the yaml template](../kube/org1/org1-cc-template.yaml) as a Kubernetes 
   `Deployment` specifying _CHAINCODE_ID=sha-256(archive)_ in the environment and binding a `Service` port 9999 
   within the namespace.  When the network sends messages to the chaincode process, it will use the host URL as 
   defined in the `connection.json`, connecting to the kubernetes `Service` URL and `Deployment`.
   

5.  Finally, the Admin CLI issues a series of peer commands to approve and commit the chaincode for the org: 

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
$ ./network chaincode invoke asset-transfer-basic '{"Args":["CreateAsset","1","blue","35","tom","1000"]}' 
2022-06-23 06:12:13.150 UTC 0001 INFO [chaincodeCmd] chaincodeInvokeOrQuery -> Chaincode invoke successful. result: status:200 payload:"{\"owner\":\"tom\",\"color\":\"blue\",\"size\":35,\"appraisedValue\":1000,\"assetID\":\"1\"}"
```

### Query
```shell
$ ./network chaincode query asset-transfer-basic '{"Args":["ReadAsset","1"]}' | jq 
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
$ ./network chaincode metadata asset-transfer-basic | jq | head 
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
```go
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
container within Kubernetes, the chaincode process can be launched locally as a native binary in a debugger, an IDE, 
or a docker image bound to the host network. 

For additional details, see the [debugging chaincode](CHAINCODE_AS_A_SERVICE.md) guide for running the basic asset 
transfer chaincode in an interactive development workflow. 


## Next Steps: 

[Writing a Blockchain Application](APPLICATIONS.md)