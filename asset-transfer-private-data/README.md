# Asset transfer private data sample

The asset transfer private data sample demonstrates:

- Usage of organization private data collections
- Read data from the organization private data collection.
- Store data in organization private data collection.

For more information about private data, visit the
[Private Data](https://hyperledger-fabric.readthedocs.io/en/latest/private-data-arch.html)
page in the Fabric documentation.

## About the sample

This sample includes smart contract and application code in multiple languages. In a use-case similar to basic asset transfer (see [asset-transfer-basic](../asset-transfer-basic) folder) this sample shows sending and receiving of asset along with its private data owned by organizations during create / delete of an asset , and during transfer of an asset to a new owner.

### Application

Please refer the below link to understand the application flow.
https://hyperledger-fabric.readthedocs.io/en/latest/private-data/private-data.html#example-scenario-asset-transfer-using-private-data-collections

### Smart Contract

The smart contract (in folder `chaincode-xyz`) implements the following functions to support the application:

CreateAsset
AgreeToTransfer
TransferAsset
DeleteAsset
DeleteTranferAgreement

ReadAsset
ReadAssetPrivateDetails
ReadTransferAgreement
GetAssetByRange
QueryAssetByOwner
QueryAssets
getQueryResultForQueryString

## Running the sample

Like other samples, the Fabric test network is used to deploy and run this sample. Follow these steps in order:

1. Create the test network and a channel (from the `test-network` folder).
   ```
   ./network.sh up createChannel -c mychannel -ca
   ```

2. Deploy one of the smart contract implementations (from the `test-network` folder).
   ```
   # To deploy the Java chaincode implementation
   ./network.sh deployCC -ccn private -ccp ../asset-transfer-private-data/chaincode-java  -ccl java -ccep "OR('Org1MSP.peer','Org2MSP.peer')"  -cccg '../asset-transfer-private-data/chaincode-java/collections_config.json' -ccep "OR('Org1MSP.peer','Org2MSP.peer')"

   # To deploy the go chaincode implementation
   ./network.sh deployCC -ccn private -ccp ../asset-transfer-private-data/chaincode-go  -ccl go -ccep "OR('Org1MSP.peer','Org2MSP.peer')"  -cccg '../asset-transfer-private-data/chaincode-go/collections_config.json' -ccep "OR('Org1MSP.peer','Org2MSP.peer')"
   
   # To deploy the typescript chaincode implementation
   ./network.sh deployCC -ccn private -ccp ../asset-transfer-private-data/chaincode-typescript/ -ccl typescript  -ccep "OR('Org1MSP.peer','Org2MSP.peer')" -cccg ../asset-transfer-private-data/chaincode-typescript/collections_config.json 
   ```

3. Run the application (from the `asset-transfer-private-data` folder).
   ```
   # To run the Javascript sample application
   cd application-javascript
   npm install
   node app.js

   # To run the Typescript sample application
   cd application-gateway-typescript
   npm install
   npm start

   ```

## Clean up

When you are finished, you can bring down the test network (from the `test-network` folder). The command will remove all the nodes of the test network, and delete any ledger data that you created.

```
./network.sh down
```