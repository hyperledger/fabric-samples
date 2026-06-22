# Asset transfer ledger queries sample

The asset transfer ledger queries sample demonstrates the ledger query capabilities of Hyperledger Fabric, including range queries, rich queries (when using CouchDB), and retrieving asset history.

## About the sample

This sample includes smart contract and application code in multiple languages. It demonstrates several key features:

- **Range Queries**: Retrieve a range of assets from the ledger.
- **Transaction Updates with Range Queries**: Safely update assets based on the results of a range query.
- **History Queries**: Use `GetHistoryForKey` to retrieve the entire history of an asset, including all previous owners and deletions.
- **Rich Queries**: Use the state database (CouchDB) to perform complex JSON queries.
- **CouchDB Indexes**: Package indexes with your chaincode to support efficient rich queries and sorting.
- **Pagination**: Handle large result sets using bookmarks and page sizes for both range and rich queries.

For a detailed walk-through of this sample, refer to the [Using CouchDB as your state database](https://hyperledger-fabric.readthedocs.io/en/latest/couchdb_tutorial.html) tutorial in the Hyperledger Fabric documentation.

### GetAssetHistory

The `GetAssetHistory` function in the smart contract uses the `GetHistoryForKey` API to return every change made to an asset since it was first created on the ledger, providing full auditability for asset transfers.

## Running the sample

The Fabric test network is used to deploy and run this sample. Follow these steps in order:

1. Create the test network and a channel (from the `test-network` folder). Use the `-s couchdb` flag to enable rich query support.

   ```shell
   ./network.sh up createChannel -c mychannel -ca -s couchdb
   ```

2. Deploy one of the smart contract implementations (from the `test-network` folder).

   - To deploy the **Go** chaincode implementation:

     ```shell
     ./network.sh deployCC -ccn ledger -ccp ../asset-transfer-ledger-queries/chaincode-go/ -ccl go
     ```

   - To deploy the **JavaScript** chaincode implementation:

     ```shell
     ./network.sh deployCC -ccn ledger -ccp ../asset-transfer-ledger-queries/chaincode-javascript/ -ccl javascript
     ```

   - To deploy the **TypeScript** chaincode implementation:

     ```shell
     ./network.sh deployCC -ccn ledger -ccp ../asset-transfer-ledger-queries/chaincode-typescript/ -ccl typescript
     ```

3. Run the application (from the `asset-transfer-ledger-queries/application-javascript` folder).

   ```shell
   cd application-javascript
   npm install
   node app.js
   ```

## Clean up

When you are finished, you can bring down the test network (from the `test-network` folder). The command will remove all the nodes of the test network, and delete any ledger data that you created.

```shell
./network.sh down
```
