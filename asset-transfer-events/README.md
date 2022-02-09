# Asset transfer events sample

The asset transfer events sample demonstrates:

- Emitting chaincode events from smart contract transaction functions.
- Receiving chaincode events in a client application.
- Replaying previous chaincode events in a client application.

Events are published when a block is committed to the ledger.

For more information about event services on per-channel basis, visit the
[Channel-based event service](https://hyperledger-fabric.readthedocs.io/en/latest/peer_event_services.html)
page in the Fabric documentation.


## About the sample

This sample includes smart contract and application code in multiple languages. In a use-case similar to basic asset transfer (see [asset-transfer-basic](../asset-transfer-basic) folder) this sample shows sending and receiving of events during create / update / delete of an asset, and during transfer of an asset to a new owner.

### Application

Follow the execution flow in the client application code, and corresponding output on running the application. Pay attention to the sequence of:

- Transaction invocations (console output like "**--> Submit transaction**").
- Events received by the application (console output like "**<-- Chaincode event received**").

Notice that events will be received by the listener after the application code submits the transaction and it is committed to the ledger, but during other application activity unrelated to the event.

### Smart Contract

The smart contract (in folder `chaincode-xyz`) implements the following functions to support the application:

- CreateAsset
- ReadAsset
- UpdateAsset
- DeleteAsset
- TransferAsset

Note that the asset transfer implemented by the smart contract is a simplified scenario, without ownership validation, meant only to demonstrate the use of sending and receiving events.

## Running the sample

Like other samples, the Fabric test network is used to deploy and run this sample. Follow these steps in order:

1. Create the test network and a channel (from the `test-network` folder).
   ```
   ./network.sh up createChannel -c mychannel -ca
   ```

1. Deploy one of the smart contract implementations (from the `test-network` folder).
   ```
   # To deploy the JavaScript chaincode implementation
   ./network.sh deployCC -ccn events -ccp ../asset-transfer-events/chaincode-javascript/ -ccl javascript -ccep "OR('Org1MSP.peer','Org2MSP.peer')"

   # To deploy the Java chaincode implementation
   ./network.sh deployCC -ccn events -ccp ../asset-transfer-events/chaincode-java/ -ccl java -ccep "OR('Org1MSP.peer','Org2MSP.peer')"
   ```

1. Run the application (from the `asset-transfer-events` folder).
   ```
   # To run the Go sample application
   cd application-gateway-go
   go run .

   # To run the Typescript sample application
   cd application-gateway-typescript
   npm install
   npm start

   # To run the Java sample application
   cd application-gateway-java
   ./gradlew run
   ```

## Clean up

When you are finished, you can bring down the test network (from the `test-network` folder). The command will remove all the nodes of the test network, and delete any ledger data that you created.

```
./network.sh down
```