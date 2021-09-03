# Asset-Transfer-Basic as an external service

This sample provides an introduction to how to use external builder and launcher scripts to run chaincode as an external service to your peer. For more information, see the [Chaincode as an external service](https://hyperledger-fabric.readthedocs.io/en/latest/cc_service.html) topic in the Fabric documentation.

**Note:** each organization in a real network would need to setup and host their own instance of the external service. In this tutorial, we use the same instance for both organizations for simplicity.

## Setting up the external builder and launcher

External Builders and Launchers is an advanced feature that typically requires custom packaging of the peer image so that it contains all the tools your builder and launcher require. This sample uses very simple (and crude) shell scripts that can be run directly within the default Fabric peer images.

Open the `config/core.yaml` file at the top of the `fabric-samples` directory. If you do not have this file, follow the instructions to [Install the Samples, Binaries and Docker Images](https://hyperledger-fabric.readthedocs.io/en/latest/install.html) to download the Fabric binaries and configuration files alongside the Fabric samples.

Modify the `externalBuilders` field in the `core.yaml` file to resemble the configuration below:
```
externalBuilders:
    - path: /opt/gopath/src/github.com/hyperledger/fabric-samples/asset-transfer-basic/chaincode-external/sampleBuilder
      name: external-sample-builder
```
This update sets the name of the external builder as `external-sample-builder`, and the path of the builder to the scripts provided in this sample. Note that this is the path within the peer container, not your local machine.

To set the path within the peer container, you will need to modify the docker compose file to mount a couple of additional volumes. Open the file `test-network/docker/docker-compose-test-net.yaml`, and add to the `volumes` section of both `peer0.org1.example.com` and `peer0.org2.example.com` the following two lines:

```
        - ../..:/opt/gopath/src/github.com/hyperledger/fabric-samples
        - ../../config/core.yaml:/etc/hyperledger/fabric/core.yaml
```

This update will mount the `core.yaml` that you modified into the peer container and override the configuration file within the peer image. The update also mounts the fabric-sample builder so that it can be found at the location that you specified in `core.yaml`. You also have the option of commenting out the line `- /var/run/docker.sock:/host/var/run/docker.sock`, since we no longer need to access the docker daemon from inside the peer container to launch the chaincode.

## Packaging and installing Chaincode

The Asset-Transfer-Basic external chaincode requires two environment variables to run, `CHAINCODE_SERVER_ADDRESS` and `CHAINCODE_ID`, which are described and set in the `chaincode.env` file.

You need to provide a `connection.json` configuration file to your peer in order to connect to the external Asset-Transfer-Basic service. The address specified in the `connection.json` must correspond to the `CHAINCODE_SERVER_ADDRESS` value in `chaincode.env`, which is `asset-transfer-basic.org1.example.com:9999` in our example.

Because we will run our chaincode as an external service, the chaincode itself does not need to be included in the chaincode
package that gets installed to each peer. Only the configuration and metadata information needs to be included
in the package. Since the packaging is trivial, we can manually create the chaincode package.

Open a new terminal and navigate to the `fabric-samples/asset-transfer-basic/chaincode-external` directory.
```
cd fabric-samples/asset-transfer-basic/chaincode-external
```

First, create a `code.tar.gz` archive containing the `connection.json` file:
```
tar cfz code.tar.gz connection.json
```

Then, create the chaincode package, including the `code.tar.gz` file and the supplied `metadata.json` file:
```
tar cfz asset-transfer-basic-external.tgz metadata.json code.tar.gz
```

You are now ready to deploy the external chaincode sample.

## Starting the test network

We will use the Fabric test network to run the external chaincode. Open a new terminal and navigate to the `fabric-samples/test-network` directory.
```
cd fabric-samples/test-network
```

Run the following command to deploy the test network and create a new channel:
```
./network.sh up createChannel -c mychannel -ca
```

We are now ready to deploy the external chaincode.

## Installing the external chaincode

We can't use the test network script to install an external chaincode so we will have to do a bit more work. However, we can still leverage part of the test-network scripts to make this easier.

From the `test-network` directory, set the following environment variables to use the Fabric binaries:
```
export PATH=${PWD}/../bin:$PATH
export FABRIC_CFG_PATH=$PWD/../config/
```

Run the following command to import functions from the `envVar.sh` script into your terminal. These functions allow you to act as either test network organization.
```
. ./scripts/envVar.sh
```

Run the following commands to install the `asset-transfer-basic-external.tar.gz` chaincode on org1. The `setGlobals` function simply sets the environment variables that allow you to act as org1 or org2.
```
setGlobals 1
peer lifecycle chaincode install ../asset-transfer-basic/chaincode-external/asset-transfer-basic-external.tgz
```

Install the chaincode package on the org2 peer:
```
setGlobals 2
peer lifecycle chaincode install ../asset-transfer-basic/chaincode-external/asset-transfer-basic-external.tgz
```

Run the following command to query the package ID of the chaincode that you just installed:
```
setGlobals 1
peer lifecycle chaincode queryinstalled --peerAddresses localhost:7051 --tlsRootCertFiles organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt
```

The command will return output similar to the following:
```
Installed chaincodes on peer:
Package ID: basic_1.0:ecfc83f251b7c2d9ef376bc3fc20fc6b9744c0fc0a8923092af6542af94790c3, Label: basic_1.0
```

Save the package ID that was returned by the command as an environment variable. The ID will not be the same for all users, so you need to set the variable using the ID from your command window:
```
export CHAINCODE_ID=basic_1.0:ecfc83f251b7c2d9ef376bc3fc20fc6b9744c0fc0a8923092af6542af94790c3
```


## Running the Asset-Transfer-Basic external service

We are going to run the smart contract as an external service by first building and then starting a docker container. Open a new terminal and navigate back to the `chaincode-external` directory:
```
cd fabric-samples/asset-transfer-basic/chaincode-external
```

In this directory, open the `chaincode.env` file to set the `CHAINCODE_ID` variable to the same package ID that was returned by the `peer lifecycle chaincode queryinstalled` command. The value should be the same as the environment variable that you set in the previous terminal.

After you edit the `chaincode.env` file, you can use the `Dockerfile` to build an image of the external Asset-Transfer-Basic chaincode:
```
docker build -t hyperledger/asset-transfer-basic .
```

You can then run the image to start the Asset-Transfer-Basic service:
```
docker run -it --rm --name asset-transfer-basic.org1.example.com --hostname asset-transfer-basic.org1.example.com --env-file chaincode.env --network=fabric_test hyperledger/asset-transfer-basic
```

This will start and run the external chaincode service within the container.

## Deploy the Asset-Transfer-Basic external chaincode definition to the channel

Navigate back to the `test-network` directory to finish deploying the chaincode definition of the external smart contract to the channel. Make sure that your environment variables are still set.

```
setGlobals 2
peer lifecycle chaincode approveformyorg -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile "$PWD/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem" --channelID mychannel --name basic --version 1.0 --package-id $CHAINCODE_ID --sequence 1

setGlobals 1
peer lifecycle chaincode approveformyorg -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile "$PWD/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem" --channelID mychannel --name basic --version 1.0 --package-id $CHAINCODE_ID --sequence 1

peer lifecycle chaincode commit -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile "$PWD/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem" --channelID mychannel --name basic --peerAddresses localhost:7051 --tlsRootCertFiles "$PWD/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt" --peerAddresses localhost:9051 --tlsRootCertFiles organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt --version 1.0 --sequence 1
```

The commands above approve the chaincode definition for the external chaincode and commits the definition to the channel. The resulting output should be similar to the following:

```
2020-08-05 15:41:44.982 PDT [chaincodeCmd] ClientWait -> INFO 001 txid [6bdbe040b99a45cc90a23ec21f02ea5da7be8b70590eb04ff3323ef77fdedfc7] committed with status (VALID) at localhost:7051
2020-08-05 15:41:44.983 PDT [chaincodeCmd] ClientWait -> INFO 002 txid [6bdbe040b99a45cc90a23ec21f02ea5da7be8b70590eb04ff3323ef77fdedfc7] committed with status (VALID) at localhost:9051
```

Now that we have started the chaincode service and deployed it to the channel, we can submit transactions as we would with a normal chaincode.

## Using the Asset-Transfer-Basic external chaincode

Open yet another terminal and navigate to the `fabric-samples/asset-transfer-basic/application-javascript` directory:
```
cd fabric-samples/asset-transfer-basic/application-javascript
```

Run the following commands to use the node application in this directory to test the external smart contract:
```
rm -rf wallet # in case you ran this before
npm install
node app.js
```

If all goes well, the program should run exactly the same as described in the "Writing Your First Application" tutorial.

## Enabling TLS for chaincode and peer communication

**Note:** This section uses an example of self-signed certificate. You may use your organization hosted CA to issue the certificate and generate a key for production deployment.

In the sample so far, you connected both peers in `test-network` to the single instance of chaincode server. However, if you would like to enable TLS between the peer nodes and the chaincode server, each peer node needs to have its own CA certificate. Enabling TLS is made possible at runtime in the chaincode.

- As a first step generate a keypair that can be used. Run these commands from the `fabric-samples/asset-transfer-basic/chaincode-external` directory.

*Find instructions to install `openssl` in [openssl.org](https://www.openssl.org/)*

For `org1.example.com`

```
openssl req -nodes -x509 -newkey rsa:4096 -keyout crypto/key1.pem -out crypto/cert1.pem -subj "/C=IN/ST=KA/L=Bangalore/O=example Inc/OU=Developer/CN=asset-transfer-basic.org1.example.com/emailAddress=dev@asset-transfer-basic.org1.example.com"
```

For `org2.example.com`

```
openssl req -nodes -x509 -newkey rsa:4096 -keyout crypto/key2.pem -out crypto/cert2.pem -subj "/C=IN/ST=KA/L=Bangalore/O=example Inc/OU=Developer/CN=asset-transfer-basic.org2.example.com/emailAddress=dev@asset-transfer-basic.org2.example.com"
```

- Copy the CA file contents for both `org1.example.com` & `org2.example.com`

```
cp ../../test-network/organizations/peerOrganizations/org1.example.com/ca/ca.org1.example.com-cert.pem crypto/rootcert1.pem
cp ../../test-network/organizations/peerOrganizations/org2.example.com/ca/ca.org2.example.com-cert.pem crypto/rootcert2.pem
```

- Generate a client key and cert for auth purpose. You need a key and cert generated from the CA of each organization. Peer nodes act as clients to chaincode server.

- Change the `connection.json` with the below contents. The `root_cert` parameter is the root CA certificate which the chaincode server is run with. You may run the below commands to get the certificate file contents as strings and copy them when needed.

```
awk 'NF {sub(/\r/, ""); printf "%s\\n",$0;}' crypto/cert1.pem
awk 'NF {sub(/\r/, ""); printf "%s\\n",$0;}' crypto/cert2.pem
```

Similarly, replace the `client_key` and the `client_cert` contents with the values from the previous step.

```
{
  "address": "asset-transfer-basic.org1.example.com:9999",
  "dial_timeout": "10s",
  "tls_required": true,
  "client_auth_required": true,
  "client_key": "-----BEGIN PRIVATE KEY----- ... -----END PRIVATE KEY-----",
  "client_cert": "-----BEGIN CERTIFICATE---- ... -----END CERTIFICATE-----",
  "root_cert": "-----BEGIN CERTIFICATE---- ... -----END CERTIFICATE-----"
}
```

- Follow the instructions in [Package](#packaging-and-installing-chaincode) and [Install](#installing-the-external-chaincode) steps for each organization. Remember that the chaincode server's address for the second organization is `asset-transfer-basic.org2.example.com:9999`.

- Copy the appropriate `CHAINCODE_ID` to both [chaincode1.env](./chaincode1.env) and [chaincode2.env](./chaincode2.env) files. Bring up the chaincode containers using the docker-compose command below

```
docker-compose up -f docker-compose-chaincode.yaml up --build -d
```

- Follow the instructions in [Finish Deployment](#finish-deploying-the-asset-transfer-basic-external-chaincode-) for each organization seperately.
