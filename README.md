# Fabric REST sample

Prototype sample REST server to demonstrate good Fabric Node SDK practices

The primary aim of this sample is to show how to write a long running client application using the Fabric Node SDK

The REST API is intended to work with the [basic asset transfer example](https://github.com/hyperledger/fabric-samples/tree/main/asset-transfer-basic)

To install the basic asset transfer chaincode on a local Fabric network, follow the [Using the Fabric test network](https://hyperledger-fabric.readthedocs.io/en/release-2.2/test_network.html) tutorial

To build and start the sample REST server, you'll need to [download and install an LTS version of node](https://nodejs.org/en/download/)

Clone this repository and change to the `fabric-rest-sample/asset-transfer-basic/rest-api-typescript` directory before running the following commands

Install dependencies

```shell
npm install
```

Build the REST server

```shell
npm run build
```

Create a `.env` file to configure the server for the test network (make sure TEST_NETWORK_HOME is set to the fully qualified `test-network` directory)

```shell
TEST_NETWORK_HOME=$HOME/fabric-samples/test-network npm run generateEnv
```

Start a Redis server

```shell
npm run start:redis
```

Start the sample REST server

```shell
npm run start:dev
```

If everything went well, you can now make REST calls!

For example, check whether an asset exists...

```shell
curl -v -X OPTIONS http://localhost:3000/api/assets/asset7
```

Create an asset...

```shell
curl --header "Content-Type: application/json" --request POST --data '{"id":"asset7","color":"red","size":42,"owner":"Jean","appraisedValue":101}' http://localhost:3000/api/assets
```

Get an asset...

```shell
curl -v http://localhost:3000/api/assets/asset7
```
