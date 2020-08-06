# Asset-Transfer-Basic as an external service

See the "Chaincode as an external service" documentation for running chaincode as an external service.
This includes details of the external builder and launcher scripts which peers in your Fabric network will require.

The Asset-Transfer-Basic chaincode requires two environment variables to run, `CHAINCODE_SERVER_ADDRESS` and `CHAINCODE_ID`, which are described in the `chaincode.env.example` file. Copy this file to `chaincode.env` before continuing.

**Note:** each organization in a Asset-Transfer-Basic network will need to follow the instructions below to host their own instance of the Asset-Transfer-Basic external service.

## Packaging peer image

External Builders and Launchers is an advanced feature that will likely require custom packaging of the peer image. The following steps is the basic walk through to build a peer binary with external builders and launcher capability, please refer to ["External Builders and Launchers"](https://hyperledger-fabric.readthedocs.io/en/release-2.2/cc_launcher.html) for more details.

First of all, download the Hyperledger Fabric source code from ["Fabric github repository"](https://github.com/hyperledger/fabric/tree/master), a version greater than 2.0 is required.

Open the `core.yaml` configuration file under `fabric/sampleconfig`

Modify the field `externalBuilders` as the following:
```
externalBuilders:
    - path: /full path to fabric-samples directory/asset-transfer-basic/chaincode-external/sampleBuilder
      name: external 
      environmentWhitelist:
        - GOPROXY
```
This configuration sets the name of the external builder as `external`, and the path of the builder scripts to the sampleBuilder scripts provided in this sample.

With completing the configuration setup for peer. We are ready to build the peer binary using the command `make peer`. The peer binary will be located at `fabric/build/bin/peer`.

## Packaging and installing Chaincode

Make sure the value of `CHAINCODE_SERVER_ADDRESS` in `chaincode.env` is correct for the Asset-Transfer-Basic external service you will be running.

The peer needs a `connection.json` configuration file so that it can connect to the external Asset-Transfer-Basic service.
Use the `CHAINCODE_SERVER_ADDRESS` value in `chaincode.env` to create the `connection.json` file with the following command (requires [jq](https://stedolan.github.io/jq/)):

```
env $(cat chaincode.env | grep -v "#" | xargs) jq -n '{"address":env.CHAINCODE_SERVER_ADDRESS,"dial_timeout": "10s","tls_required": false}' > connection.json
```

Add this file to a `code.tar.gz` archive ready for adding to a Asset-Transfer-Basic external service package:

```
tar cfz code.tar.gz connection.json
```

Package the Asset-Transfer-Basic external service using the supplied `metadata.json` file:

```
tar cfz asset-transfer-basic-pkg.tgz metadata.json code.tar.gz
```

Install the `asset-transfer-basic-pkg.tgz` chaincode as usual, for example:

```
peer lifecycle chaincode install ./asset-transfer-basic-pkg.tgz
```

Query installed chaincode to get chaincode package-id:

```
peer lifecycle chaincode queryinstalled --peerAddresses {PEER_ADDRESS} 
```

Edit the `chaincode.env` file to configure the `CHAINCODE_ID` variable with obtained chaincode package-id.

## Running the Asset-Transfer-Basic external service

To run the service in a container, build a Asset-Transfer-Basic docker image:

```
docker build -t hyperledger/asset-transfer-basic .
```

Edit the `chaincode.env` file to configure the `CHAINCODE_ID` variable before starting a Asset-Transfer-Basic container using the following command:

```
docker run -it --rm --name asset-transfer-basic.org1.example.com --hostname asset-transfer-basic.org1.example.com --env-file chaincode.env --network=net_test hyperledger/asset-transfer-basic
```

## Starting the Asset-Transfer-Basic external service

Complete the remaining lifecycle steps to start the Asset-Transfer-Basic chaincode!
