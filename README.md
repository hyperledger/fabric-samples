# Fabric REST sample

Prototype sample REST server to demonstrate good Fabric Node SDK practices for parts of [FAB-18511](https://jira.hyperledger.org/browse/FAB-18511)

The primary aim of this sample is to show how to write a long running client application using the Fabric Node SDK

The REST API is intended to work with the [basic asset transfer example](https://github.com/hyperledger/fabric-samples/tree/main/asset-transfer-basic)

To install the basic asset transfer chaincode on a local Fabric network, follow the [Using the Fabric test network](https://hyperledger-fabric.readthedocs.io/en/release-2.2/test_network.html) tutorial

## Usage

**Note:** these instructions should work with the release-2.2 branch of `fabric-samples` but later versions require some changes

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

## REST API

If everything went well, you can now make basic asset transfer REST calls!

The examples below require a `SAMPLE_APIKEY` environment variable which must be set to an API key from the `.env` file created above.

For example, to use the ORG1_APIKEY...

```
SAMPLE_APIKEY=$(grep ORG1_APIKEY .env | cut -d '=' -f 2-)
```

### Get all assets...

```shell
curl --header "X-Api-Key: ${SAMPLE_APIKEY}" http://localhost:3000/api/assets
```

### Check whether an asset exists...

```shell
curl --include --header "X-Api-Key: ${SAMPLE_APIKEY}" --request OPTIONS http://localhost:3000/api/assets/asset7
```

### Create an asset...

```shell
curl --include --header "Content-Type: application/json" --header "X-Api-Key: ${SAMPLE_APIKEY}" --request POST --data '{"id":"asset7","color":"red","size":42,"owner":"Jean","appraisedValue":101}' http://localhost:3000/api/assets
```

### Read transaction status...

```shell
curl --header "X-Api-Key: ${SAMPLE_APIKEY}" http://localhost:3000/api/transactions/__transaction_id__
```

### Read an asset...

```shell
curl --header "X-Api-Key: ${SAMPLE_APIKEY}" http://localhost:3000/api/assets/asset7
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
## Steps to run the application using docker:

Move to directory fabric-rest-sample/asset-transfer-basic/rest-api-typescript

### Build docker image 
    docker build -t fabricapp .

### Generate .env file 
    TEST_NETWORK_HOME=$HOME/fabric-samples/test-network ./scripts/generateEnv.sh 

    Note: Connection profile need to use the peer container’s hostname instead of localhost. 
    
### Run docker containers 
    docker-compose up -d 





