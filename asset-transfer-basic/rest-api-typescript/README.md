# Asset Transfer REST API Sample

Prototype sample REST server to demonstrate good Fabric Node SDK practices

The primary aim of this sample is to show how to write a long running client application using the Fabric Node SDK

The REST API is intended to work with the [basic asset transfer example](https://github.com/hyperledger/fabric-samples/tree/main/asset-transfer-basic)

To install the basic asset transfer chaincode on a local Fabric network, follow the [Using the Fabric test network](https://hyperledger-fabric.readthedocs.io/en/release-2.4/test_network.html) tutorial

## Overview

The sample creates two long lived connections to a Fabric network in order to submit and evaluate transactions using two different identities

To ensure requests respond quickly enough to avoid timeouts, all submit transactions are queued for processing and will be retried if they fail

Submit transactions are retried if they fail with any error, except for errors from the smart contract, or duplicate transaction errors

Alternatively you might prefer to modify the sample to only retry transactions which fail with specific errors instead, for example:
- MVCC_READ_CONFLICT
- PHANTOM_READ_CONFLICT
- ENDORSEMENT_POLICY_FAILURE
- CHAINCODE_VERSION_CONFLICT
- EXPIRED_CHAINCODE

See [src/index.ts](src/index.ts) for a description of the sample code structure, and [src/config.ts](src/config.ts) for details of configuring the sample using environment variables. 

## Usage

To build and start the sample REST server, you'll need to [download and install an LTS version of node](https://nodejs.org/en/download/)

Clone the `fabric-samples` repository and change to the `fabric-samples/asset-transfer-basic/rest-api-typescript` directory before running the following commands

**Note:** these instructions should work with the main branch of `fabric-samples`

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

Start a Redis server (Redis is used to store the queue of submit transactions)

```shell
npm run start:redis
```

Start the sample REST server

```shell
npm run start:dev
```

### Docker image

Alternatively, run the following commands in the `fabric-rest-sample/asset-transfer-basic/rest-api-typescript` directory to start the sample in a Docker container

Build the Docker image

```shell
docker build -t fabric-rest-sample .
```

Create a `.env` file to configure the server for the test network (make sure `TEST_NETWORK_HOME` is set to the fully qualified `test-network` directory and `AS_LOCAL_HOST` is set to `false` so that the server works inside the Docker Compose network)

```shell
TEST_NETWORK_HOME=$HOME/fabric-samples/test-network AS_LOCAL_HOST=false npm run generateEnv
```

Start the sample REST server and Redis server

```shell
docker-compose up -d
```

## REST API

If everything went well, you can now open a new terminal and try out some basic asset transfer REST calls!

The examples below require a `SAMPLE_APIKEY` environment variable which must be set to an API key from the `.env` file created above.

For example, to use the ORG1_APIKEY...

```
SAMPLE_APIKEY=$(grep ORG1_APIKEY .env | cut -d '=' -f 2-)
```

### Get all assets...

```shell
curl --header "X-Api-Key: ${SAMPLE_APIKEY}" http://localhost:3000/api/assets
```

You should see all the available assets, for example

```
[{"AppraisedValue":300,"Color":"blue","ID":"asset1","Owner":"Tomoko","Size":5},{"AppraisedValue":400,"Color":"red","ID":"asset2","Owner":"Brad","Size":5},{"AppraisedValue":500,"Color":"green","ID":"asset3","Owner":"Jin Soo","Size":10},{"AppraisedValue":600,"Color":"yellow","ID":"asset4","Owner":"Max","Size":10},{"AppraisedValue":700,"Color":"black","ID":"asset5","Owner":"Adriana","Size":15},{"AppraisedValue":800,"Color":"white","ID":"asset6","Owner":"Michel","Size":15}]
```

### Check whether an asset exists...

```shell
curl --include --header "X-Api-Key: ${SAMPLE_APIKEY}" --request OPTIONS http://localhost:3000/api/assets/asset7
```

### Create an asset...

```shell
curl --include --header "Content-Type: application/json" --header "X-Api-Key: ${SAMPLE_APIKEY}" --request POST --data '{"id":"asset7","color":"red","size":42,"owner":"Jean","appraisedValue":101}' http://localhost:3000/api/assets
```

The response should include a `jobId` which you can use to check the job status in next step

```
{"status":"Accepted","jobId":"1","timestamp":"2021-10-22T16:27:09.426Z"}
```

### Read job status...

```shell
curl --header "X-Api-Key: ${SAMPLE_APIKEY}" http://localhost:3000/api/jobs/__job_id__
```

The response should include a list of `transactionIds` which you can use to check the transaction status in next step, for example

```
{"jobId":"1","transactionIds":["1dd35c2e5d840fec1dccc6e8cfce886c660c103de3e7b93dd774d04f39eef82a"],"transactionPayload":""}
```

There may be more transaction IDs if the job was retried

### Read transaction status...

```shell
curl --header "X-Api-Key: ${SAMPLE_APIKEY}" http://localhost:3000/api/transactions/__transaction_id__
```

The response will show the validation code of the transaction, for example

```
{"transactionId":"1dd35c2e5d840fec1dccc6e8cfce886c660c103de3e7b93dd774d04f39eef82a","validationCode":"VALID"}
```

Alternatively, you will get a 404 not found response if the transaction was not committed

### Read an asset...

```shell
curl --header "X-Api-Key: ${SAMPLE_APIKEY}" http://localhost:3000/api/assets/asset7
```

You should see the newly created asset, for example

```
{"AppraisedValue":101,"Color":"red","ID":"asset7","Owner":"Jean","Size":42}
```

### Update an asset...

```shell
curl --include --header "Content-Type: application/json" --header "X-Api-Key: ${SAMPLE_APIKEY}" --request PUT --data '{"id":"asset7","color":"red","size":11,"owner":"Jean","appraisedValue":101}' http://localhost:3000/api/assets/asset7
```

### Transfer an asset...

```shell
curl --include --header "Content-Type: application/json" --header "X-Api-Key: ${SAMPLE_APIKEY}" --request PATCH --data '[{"op":"replace","path":"/owner","value":"Ashleigh"}]' http://localhost:3000/api/assets/asset7
```

### Delete an asset...

```shell
curl --include --header "X-Api-Key: ${SAMPLE_APIKEY}" --request DELETE http://localhost:3000/api/assets/asset7
```
