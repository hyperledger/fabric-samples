# Off-chain data store sample

The off-chain data store sample demonstrates:

- Receiving block events in a client application.
- Using a checkpointer to resume event listening after a failure or application restart.
- Extracting ledger updates from block events in order to build an off-chain data store.

## About the sample

This sample shows how to replicate the data in your blockchain network to an off-chain data store. Using an off-chain data store allows you to analyze the data from your network or build a dashboard without degrading the performance of your application.

This sample uses the block event listening capability of the [Fabric Gateway client API](https://hyperledger.github.io/fabric-gateway/) for Fabric v2.4 and later.

### Application

The client application provides several "commands" that can be invoked using the command-line:

- **getAllAssets**: Retrieve the current details of all assets recorded on the ledger. See:
  - TypeScript: [application-typescript/src/getAllAssets.ts](application-typescript/src/getAllAssets.ts)
  - Java: [application-java/app/src/main/java/GetAllAssets.java](application-java/app/src/main/java/GetAllAssets.java)
- **listen**: Listen for block events, and use them to replicate ledger updates in an off-chain data store. See:
  - TypeScript: [application-typescript/src/listen.ts](application-typescript/src/listen.ts)
  - Java: [application-java/app/src/main/java/Listen.java](application-java/app/src/main/java/Listen.java)
- **transact**: Submit a set of transactions to create, modify and delete assets. See:
  - TypeScript: [application-typescript/src/transact.ts](application-typescript/src/transact.ts)
  - Java: [application-java/app/src/main/java/Transact.java](application-java/app/src/main/java/Transact.java)

To keep the sample code concise, the **listen** command writes ledger updates to an output file named `store.log` in the current working directory (which for the Java sample is the `application-java/app` directory). A real implementation could write ledger updates directly to an off-chain data store of choice. You can inspect the information captured in this file as you run the sample.

Note that the **listen** command is is restartable and will resume event listening after the last successfully processed block / transaction. This is achieved using a checkpointer to persist the current listening position. Checkpoint state is persisted to a file named `checkpoint.json` in the current working directory. If no checkpoint state is present, event listening begins from the start of the ledger (block number zero).

### Smart Contract

The asset-transfer-basic smart contract is used to generate transactions and associated ledger updates.

## Running the sample

The Fabric test network is used to deploy and run this sample. Follow these steps in order:

1. Create the test network and a channel (from the `test-network` folder).

   ```bash
   ./network.sh up createChannel -c mychannel -ca
   ```

1. Deploy one of the asset-transfer-basic smart contract implementations (from the `test-network` folder).

   ```bash
   # To deploy the TypeScript chaincode implementation
   ./network.sh deployCC -ccn basic -ccp ../asset-transfer-basic/chaincode-typescript/ -ccl typescript

   # To deploy the Go chaincode implementation
   ./network.sh deployCC -ccn basic -ccp ../asset-transfer-basic/chaincode-go/ -ccl go

   # To deploy the Java chaincode implementation
   ./network.sh deployCC -ccn basic -ccp ../asset-transfer-basic/chaincode-java/ -ccl java
   ```

1. Populate the ledger with some assets and use eventing to capture ledger updates (from the `off_chain_data` folder).

   ```bash
   # To run the TypeScript sample application
   cd application-typescript
   npm install
   npm start transact listen

   # To run the Java sample application
   cd application-java
   ./gradlew run --quiet --args='transact listen'
   ```

1. Interrupt the listener process using **Control-C**.

1. View the current world state of the blockchain (from the `off_chain_data` folder). You may want to compare the results to the ledger updates captured by the listener in the `store.log` file.

   ```bash
   # To run the TypeScript sample application
   cd application-typescript
   npm --silent start getAllAssets

   # To run the Java sample application
   cd application-java
   ./gradlew run --quiet --args=getAllAssets
   ```

1. Make some more ledger updates, then observe listener resume capability (from the `off_chain_data` folder). Note from the transaction IDs recorded to the console that the listener resumes from exactly after the last successfully processed transaction.

   ```bash
   # To run the TypeScript sample application
   cd application-typescript
   npm start transact
   SIMULATED_FAILURE_COUNT=5 npm start listen
   npm start listen

   # To run the Java sample application
   cd application-java
   ./gradlew run --quiet --args=transact
   SIMULATED_FAILURE_COUNT=5 ./gradlew run --quiet --args=listen
   ./gradlew run --quiet --args=listen
   ```

1. Interrupt the listener process using **Control-C**.

## Clean up

The persisted event checkpoint position can be removed by deleting the `checkpoint.json` file while the listener is stopped.

The recorded ledger updates can be removed by deleting the `store.log` file.

When you are finished, you can bring down the test network (from the `test-network` folder). The command will remove all the nodes of the test network, and delete any ledger data that you created. Be sure to remove the `checkpoint.json` and `store.log` files before attempting to run the application with a new network.

```
./network.sh down
```