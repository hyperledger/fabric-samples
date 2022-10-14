# FabCar as an external service

See the "Chaincode as an external service" documentation for running chaincode as an external service.
This includes details of the external builder and launcher scripts which will peers in your Fabric network will require.

The FabCar chaincode requires two environment variables to run, `CHAINCODE_SERVER_ADDRESS` and `CORE_CHAINCODE_ID_NAME`, which are described in the `chaincode.env.example` file. Copy this file to `chaincode.env` before continuing.

**Note:** each organization in a Fabric network will need to follow the instructions below to host their own instance of the FabCar external service.

## Packaging and installing

Make sure the value of `CHAINCODE_SERVER_ADDRESS` in `chaincode.env` is correct for the FabCar external service you will be running.

The peer needs a `connection.json` configuration file so that it can connect to the external FabCar service.
Use the `CHAINCODE_SERVER_ADDRESS` value in `chaincode.env` to create the `connection.json` file with the following command (requires [jq](https://stedolan.github.io/jq/)):

```
env $(cat chaincode.env | grep -v "#" | xargs) jq -n '{"address":env.CHAINCODE_SERVER_ADDRESS,"dial_timeout": "10s","tls_required": false}' > connection.json
```

Add this file to a `code.tar.gz` archive ready for adding to a FabCar external service package:

```
tar cfz code.tar.gz connection.json
```

Package the FabCar external service using the supplied `metadata.json` file:

```
tar cfz fabcar-pkg.tgz metadata.json code.tar.gz
```

Install the `fabcar-pkg.tgz` chaincode as usual, for example:

```
peer lifecycle chaincode install ./fabcar-pkg.tgz
```

## Running the FabCar external service

To run the service in a container, build a FabCar docker image:

```
docker build -t hyperledger/fabcar-sample .
```

Edit the `chaincode.env` file to configure the `CHAINCODE_ID` variable before starting a FabCar container using the following command:

```
docker run -it --rm --name fabcar.org1.example.com --hostname fabcar.org1.example.com --env-file chaincode.env --network=net_test hyperledger/fabcar-sample
```

## Starting the FabCar external service

Complete the remaining lifecycle steps to start the FabCar chaincode!
