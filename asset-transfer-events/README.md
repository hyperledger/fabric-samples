# Asset Transfer Events Sample

The asset transfer events sample demonstrates chaincode events send/receive
and the receive of block events. The chaincode events are set by your
chaincode which adds the event data to the transaction and are sent when the
transaction is committed to the ledger. The block events are published when
a block is committed to the ledger, containing all the transaction details
within that block.

For more information about event services on per-channel basis, visit the
[Channel-based event service](https://hyperledger-fabric.readthedocs.io/en/latest/peer_event_services.html)
page in the Fabric documentation.


## About the Sample

This sample includes chaincodes and application code in multiple languages. 
In a use-case similar to basic asset transfer ( see `../asset-transfer-basic` folder)
this sample shows sending and receiving of events during create/update/delete of an asset 
and during transfer of an asset to a new owner.

### Application
The application demonstrates this, using two types of listeners in subsequent sections of `main` function:
1. Contract Listener: listen for events in a specific Contract
- How to register a contract listener in an application, for chaincode events
- How to get the chaincode event name and value from the chaincode event
- How to retrieve the transaction and block information from the chaincode event

2. Block Listener: listen for block level events and parse private-data events
- How to register a block listener for full block events
- How to retrieve the transaction and block information from the block event
- How to register to receive private data associated with transactions, when registering a block listener
- How to retrieve the private data collection details from the full block event
- This section also shows how to connect to a Gateway with listener that will not listen for commit events. This may be useful when the application does not want to wait for the peer to commit blocks and notify the application.


Follow the comments in `application-javascript/app.js` file, and corresponding output on running this application.
Pay attention to the sequence of 
- smart contract calls (console output like `--> Submit Transaction or --> Evaluate`)
- the events received at application end (console output like `<-- Contract Event Received: or <-- Block Event Received`) 

The listener will be notified of an event asynchronously. Notice that events will
be posted by the listener after the application code sends the transaction (or after the 
change is committed to the ledger), but during other application activity unrelated to the event.

### Smart Contract
The smart contract implements (in folder `chaincode-xyz`) following functions to support the application:
- CreateAsset
- ReadAsset
- UpdateAsset
- DeleteAsset
- TransferAsset

Note that the asset transfer implemented by the smart contract is a simplified scenario, without ownership validation, meant only to 
demonstrate the use of sending and receiving events.


## Running the sample

Like other samples, we will use the Fabric test network to deploy and run ths sample. Follow these step in order.
- Create the test network and a channel
``` 
cd test-network
./network.sh up createChannel -c mychannel -ca
```

- Deploy the chaincode (smart contract)
``` 
# to deploy javascript version
./network.sh deployCC -ccs 1  -ccv 1 -ccep "OR('Org1MSP.peer','Org2MSP.peer')"  -ccl javascript -ccp ./../asset-transfer-events/chaincode-javascript/ -ccn asset-transfer-events-javascript

# or to deploy java version
./network.sh deployCC -ccs 1  -ccv 1 -ccep "OR('Org1MSP.peer','Org2MSP.peer')"  -ccl java -ccp ./../asset-transfer-events/chaincode-java/ -ccn asset-transfer-events-java
```

- Run the application
```
cd application-javascript
npm install
# ensure this line in app.js have correct chaincode deploy name
#       const chaincodeName = '...';
node app.js
```


## Clean up
When you are finished, you can bring down the test network. The command will remove all the nodes of the test network, and delete any ledger data that you created:

```
./network.sh down
```
