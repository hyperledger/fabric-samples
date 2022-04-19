# Asset Transfer REST API Sample

Sample REST server to demonstrate good Fabric Node SDK practices.

The REST API is only intended to work with the [basic asset transfer example](https://github.com/hyperledger/fabric-samples/tree/main/asset-transfer-basic).

To install the basic asset transfer chaincode on a local Fabric network, follow the [Using the Fabric test network](https://hyperledger-fabric.readthedocs.io/en/release-2.4/test_network.html) tutorial. You need to go at least as far as the step where the ledger gets initialized with assets.

## Overview

The primary aim of this sample is to show how to write a long running client application using the Fabric Node SDK.
In particular, client applications **should not** create new connections for every transaction.

The sample also demonstrates possible error handling approaches and handles multiple requests from multiple identities.

The following sections describe the structure of the sample, or [skip to the usage section](#usage) to try it out first.

### Fabric network connections

The sample creates two long lived connections to a Fabric network in order to submit and evaluate transactions using two different identities.

The connections are made when the application starts and are retained for the life of the REST server.

Related files:

- [src/fabric.ts](src/fabric.ts)  
  All the sample code which interacts with the Fabric network via the Fabric SDK.
  For example, the `createGateway` function which connects to the Fabric network.
- [src/index.ts](src/index.ts)  
  The primary entry point for the sample.
  Connects to the Fabric network and starts the REST server.

### Error handling

In this sample submit transactions are retried if they fail with any error, **except** for errors from the smart contract, or duplicate transaction errors.

Alternatively you might prefer to modify the sample to only retry transactions which fail with specific errors instead, for example:

- MVCC_READ_CONFLICT
- PHANTOM_READ_CONFLICT
- ENDORSEMENT_POLICY_FAILURE
- CHAINCODE_VERSION_CONFLICT
- EXPIRED_CHAINCODE

Related files:
- [src/errors.ts](src/errors.ts)  
  All the Fabric transaction error handling and retry logic.

### Asset REST API

While the basic asset transfer chaincode maps well to an `/api/assets` REST API, response times when submitting transactions to a Fabric network are problematic for REST APIs.

A common approach to handle long running operations in REST APIs is to immediately return `202 ACCEPTED`, with the operation being represented by another resource, namely a `job` in this sample.

Jobs are used for submitting transactions to create, update, delete, or transfer an asset.
The `202 ACCEPTED` response includes a `jobId` which can be used with the `/api/jobs` endpoint to get the results of the create, update, delete, or transfer request.

Jobs are not used to get assets, because evaluating transactions is typically much faster.

Related files:
- [src/assets.router.ts](src/assets.router.ts)  
  Defines the main `/api/assets` endpoint.
- [src/fabric.ts](src/fabric.ts)  
  All the sample code which interacts with the Fabric network via the Fabric SDK.
- [src/jobs.router.ts](src/jobs.router.ts)  
  Defines the `/api/jobs` endpoint for getting job status.
- [src/jobs.ts](src/jobs.ts)
  Job queue implementation details.
- [src/transactions.router.ts](src/transactions.router.ts)  
  Defines the `/api/transactions` endpoint for getting transaction status.

**Note:** If you are not specifically interested in REST APIs, you should only need to look at the files in the [Fabric network connections](#fabric-network-connections) and [Error handling](#error-handling) sections above.

### REST server

The remaining sample files are related to the REST server aspects of the sample, rather than Fabric itself:

- [src/auth.ts](src/auth.ts)  
  Basic API key authentication strategy used for the sample.
- [src/config.ts](src/config.ts)  
  Descriptions of all the available configuration environment variables.
- [src/jobs.ts](src/jobs.ts)  
  Job queue implementation details.
- [src/logger.ts](src/logger.ts)  
  Logging implementation details.
- [src/redis.ts](src/redis.ts)  
  Redis implementation details.
- [src/server.ts](src/server.ts)
  Express server implementation details.

**Note:** If you are not specifically interested in REST APIs, you should only need to look at the files in the [Fabric network connections](#fabric-network-connections) and [Error handling](#error-handling) sections above.

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

**Note:** see [src/config.ts](src/config.ts) for details of configuring the sample

Start a Redis server (Redis is used to store the queue of submit transactions)

```shell
export REDIS_PASSWORD=$(uuidgen)
npm run start:redis
```

Start the sample REST server

```shell
npm run start:dev
```

### Docker image

It's also possible to use the [published docker image](https://github.com/hyperledger/fabric-samples/pkgs/container/fabric-rest-sample) to run the sample

Clone the `fabric-samples` repository and change to the `fabric-samples/asset-transfer-basic/rest-api-typescript` directory before running the following commands

Create a `.env` file to configure the server for the test network (make sure `TEST_NETWORK_HOME` is set to the fully qualified `test-network` directory and `AS_LOCAL_HOST` is set to `false` so that the server works inside the Docker Compose network)

```shell
TEST_NETWORK_HOME=$HOME/fabric-samples/test-network AS_LOCAL_HOST=false npm run generateEnv
```

**Note:** see [src/config.ts](src/config.ts) for details of configuring the sample

Start the sample REST server and Redis server

```shell
export REDIS_PASSWORD=$(uuidgen)
docker-compose up -d
```

## REST API demo

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
curl --include --header "Content-Type: application/json" --header "X-Api-Key: ${SAMPLE_APIKEY}" --request POST --data '{"ID":"asset7","Color":"red","Size":42,"Owner":"Jean","AppraisedValue":101}' http://localhost:3000/api/assets
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
curl --include --header "Content-Type: application/json" --header "X-Api-Key: ${SAMPLE_APIKEY}" --request PUT --data '{"ID":"asset7","Color":"red","Size":11,"Owner":"Jean","AppraisedValue":101}' http://localhost:3000/api/assets/asset7
```

### Transfer an asset...

```shell
curl --include --header "Content-Type: application/json" --header "X-Api-Key: ${SAMPLE_APIKEY}" --request PATCH --data '[{"op":"replace","path":"/Owner","value":"Ashleigh"}]' http://localhost:3000/api/assets/asset7
```

### Delete an asset...

```shell
curl --include --header "X-Api-Key: ${SAMPLE_APIKEY}" --request DELETE http://localhost:3000/api/assets/asset7
```
