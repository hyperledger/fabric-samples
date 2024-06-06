# Asset transfer secured agreement sample

The asset transfer events sample demonstrates how to transfer a private asset between two organizations without publicly sharing data .

## About the sample

This sample includes smart contract and application code in multiple languages. This sample shows how Fabric features state based endorsement, private data, and access control to provide secured transactions.

### Application

Refer [Secured asset transfer in Fabric](https://hyperledger-fabric.readthedocs.io/en/latest/secured_asset_transfer/secured_private_asset_transfer_tutorial.html) for application details .

### Smart Contract

The smart contract (in folder `chaincode-go`) implements the following functions to support the application:

- CreateAsset
- ChangePublicDescription
- AgreeToSell
- AgreeToBuy
- VerifyAssetProperties
- TransferAsset
- ReadAsset
- GetAssetPrivateProperties
- GetAssetSalesPrice
- GetAssetBidPrice
- GetAssetHashId
- QueryAssetSaleAgreements
- QueryAssetBuyAgreements
- QueryAssetHistory

## Running the sample

Like other samples, the Fabric test network is used to deploy and run this sample. Follow these steps in order:

1. Create the test network and a channel (from the `test-network` folder).
   ```
   ./network.sh up createChannel -c mychannel -ca
   ```

1. Deploy the smart contract implementations.
   ```
   # To deploy the go chaincode implementation
   ./network.sh deployCC -ccn secured -ccp ../asset-transfer-secured-agreement/chaincode-go/ -ccl go -ccep "OR('Org1MSP.peer','Org2MSP.peer')"
   ```

1. Run the application (from the `asset-transfer-secured-agreement` folder).
   ```
   # To run the Typescript sample application
   cd application-gateway-typescript
   npm install
   npm start

   # To run the Javascript sample application
   cd application-javascript
   node app.js
   ```

## Clean up

When you are finished, you can bring down the test network (from the `test-network` folder). The command will remove all the nodes of the test network, and delete any ledger data that you created.

```
./network.sh down
```