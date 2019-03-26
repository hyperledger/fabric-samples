# Off Chain data

This sample demonstrates how you can use [Peer channel-based event services](https://hyperledger-fabric.readthedocs.io/en/release-1.4/peer_event_services.html)
to replicate the data on your blockchain network to an off chain database.
Using an off chain database allows you to analyze the data from your network or
build a dashboard without degrading the performance of your application.

This sample uses the [Fabric network event listener](https://fabric-sdk-node.github.io/release-1.4/tutorial-listening-to-events.html) from the Node.JS Fabric SDK to write data to local instance of
CouchDB.

## Getting started

This sample uses Node Fabric SDK application code similar to the `fabcar` sample
to connect to a network created using the `first-network` sample.

### Install dependencies

You need to install Node.js version 8.9.x to use the sample application code.
Execute the following commands to install the required dependencies:

```
cd fabric-samples/off_chain_data
npm install
```

### Configuration

The configuration for the listener is stored in the `config.json` file:

```
{
    "peer_name": "peer0.org1.example.com",
    "channelid": "mychannel",
    "use_couchdb":true,
    "create_history_log":true,
    "couchdb_address": "http://localhost:5990"
}
```

`peer_name:` is the target peer for the listener.
`channelid:` is the channel name for block events.
`use_couchdb:` If set to true, events will be stored in a local instance of
CouchDB. If set to false, only a local log of events will be stored.
`create_history_log:` If true, a local log file will be created with all of the
block changes.
`couchdb_address:` is the local address for an off chain CouchDB database.

### Create an instance of CouchDB

If you set the "use_couchdb" option to true in `config.json`, you can run the
following command start a local instance of CouchDB using docker:

```
docker run --publish 5990:5984 --detach --name offchaindb hyperledger/fabric-couchdb
docker start offchaindb
```

### Starting the Network

Use the following command to start the sample network:

```
./startFabric.sh
```

This command uses the `first-network` sample to deploy a fabric network with an
ordering service, two peer organizations with two peers each, and a channel
named `mychannel`. The marbles chaincode will be installed on all four peers and
instantiated on the channel.

### Starting the Channel Event Listener

Once the network has started, we can use the Node.js SDK to create the user and
certificates our listener application will use to interact with the network. Run
the following command to enroll the admin user:

```
node enrollAdmin.js
```

You can then run the following command to register and enroll an application
user:

```
node registerUser.js
```

We can then use our application user to start the block event listener:

```
node blockEventListener.js
```

If the command is successful, you should see the output of the listener reading
the configuration blocks of `mychannel` in addition to the blocks that recorded
the approval and commitment of the marbles chaincode definition.

```
Listening for block events, nextblock: 0
Added block 0 to ProcessingMap
Added block 1 to ProcessingMap
Added block 2 to ProcessingMap
Added block 3 to ProcessingMap
Added block 4 to ProcessingMap
Added block 5 to ProcessingMap
Added block 6 to ProcessingMap
------------------------------------------------
Block Number: 0
------------------------------------------------
Block Number: 1
------------------------------------------------
Block Number: 2
------------------------------------------------
Block Number: 3
Block Timestamp: 2019-08-08T19:47:56.148Z
ChaincodeID: _lifecycle
[]
------------------------------------------------
Block Number: 4
Block Timestamp: 2019-08-08T19:48:00.234Z
ChaincodeID: _lifecycle
[]
------------------------------------------------
Block Number: 5
Block Timestamp: 2019-08-08T19:48:14.092Z
ChaincodeID: _lifecycle
[ { key: 'namespaces/fields/marbles/Collections',
    is_delete: false,
    value: '\u0012\u0000' },
  { key: 'namespaces/fields/marbles/EndorsementInfo',
    is_delete: false,
    value: '\u0012\r\n\u00031.0\u0010\u0001\u001a\u0004escc' },
  { key: 'namespaces/fields/marbles/Sequence',
    is_delete: false,
    value: '\b\u0001' },
  { key: 'namespaces/fields/marbles/ValidationInfo',
    is_delete: false,
    value: '\u00122\n\u0004vscc\u0012*\n(\u0012\f\u0012\n\b\u0002\u0012\u0002\b\u0000\u0012\u0002\b\u0001\u001a\u000b\u0012\t\n\u0007Org1MSP\u001a\u000b\u0012\t\n\u0007Org2MSP' },
  { key: 'namespaces/metadata/marbles',
    is_delete: false,
    value: '\n\u0013ChaincodeDefinition\u0012\bSequence\u0012\u000fEndorsementInfo\u0012\u000eValidationInfo\u0012\u000bCollections' } ]
```

`blockEventListener.js` creates a listener named "offchain-listener" on the
channel `mychannel`. The listener writes each block added to the channel to a
processing map called BlockMap for temporary storage and ordering purposes.
`blockEventListener.js` uses `nextblock.txt` to keep track of the latest block
that was retrieved by the listener. The block number in `nextblock.txt` may be
set to a previous block number in order to replay previous blocks. The file
may also be deleted and all blocks will be replayed when the block listener is
started.

`BlockProcessing.js` runs as a daemon and pulls each block in order from the
BlockMap. It then uses the read-write set of that block to extract the latest
key value data and store it in the database. The configuration blocks of
mychannel did not any data to the database because the blocks did not contain a
read-write set.

The channel event listener also writes metadata from each block to a log file
defined as channelid_chaincodeid.log. In this example, events will be written to
a file named `mychannel_marbles.log`. This allows you to record a history of
changes made by each block for each key in addition to storing the latest value
of the world state.

**Note:** Leave the blockEventListener.js running in a terminal window. Open a
new window to execute the next parts of the demo.

### Generate data on the blockchain

Now that our listener is setup, we can generate data using the marbles chaincode
and use our application to replicate the data to our database. Open a new
terminal and navigate to the `fabric-samples/off_chain_data` directory.

You can use the `addMarbles.js` file to add random sample data to blockchain.
The file uses the configuration information stored in `addMarbles.json` to
create a series of marbles. This file will be created during the first execution
of `addMarbles.js` if it does not exist. This program can be run multiple times
without changing the properties. The `nextMarbleNumber` will be incremented and
stored in the `addMarbles.json` file.

```
    {
        "nextMarbleNumber": 100,
        "numberMarblesToAdd": 20
    }
```

Open a new window and run the following command to add 20 marbles to the
blockchain:

```
node addMarbles.js
```

After the marbles have been added to the ledger, use the following command to
transfer one of the marbles to a new owner:

```
node transferMarble.js marble110 james
```

Now run the following command to delete the marble that was transferred:

```
node deleteMarble.js marble110
```

## Offchain CouchDB storage:

If you followed the instructions above and set `use_couchdb` to true,
`blockEventListener.js` will create two tables in the local instance of CouchDB.
`blockEventListener.js` is written to create two tables for each channel and for
each chaincode.

The first table is an offline representation of the current world state of the
blockchain ledger. This table was created using the read-write set data from
the blocks. If the listener is running, this table should be the same as the
latest values in the state database running on your peer. The table is named
after the channelid and chaincodeid, and is named mychannel_marbles in this
example. You can navigate to this table using your browser:
http://127.0.0.1:5990/mychannel_marbles/_all_docs

A second table records each block as a historical record entry, and was created
using the block data that was recorded in the log file. The table name appends
history to the name of the first table, and is named mychannel_marbles_history
in this example. You can also navigate to this table using your browser:
http://127.0.0.1:5990/mychannel_marbles_history/_all_docs

### Configure a map/reduce view for summarizing counts of marbles by color:

Now that we have state and history data replicated to tables in CouchDB, we
can use the following commands query our off-chain data. We will also add an
index to support a more complex query. Note that if the `blockEventListener.js`
is not running, the database commands below may fail since the database is only
created when events are received.

Open a new terminal window and execute the following:

```
curl -X PUT http://127.0.0.1:5990/mychannel_marbles/_design/colorviewdesign -d '{"views":{"colorview":{"map":"function (doc) { emit(doc.color, 1);}","reduce":"function ( keys , values , combine ) {return sum( values )}"}}}' -H 'Content-Type:application/json'
```

Execute a query to retrieve the total number of marbles (reduce function):

```
curl -X GET http://127.0.0.1:5990/mychannel_marbles/_design/colorviewdesign/_view/colorview?reduce=true
```

If successful, this command will return the number of marbles in the blockchain
world state, without having to query the blockchain ledger:

```
{"rows":[
  {"key":null,"value":19}
  ]}
```

Execute a new query to retrieve the number of marbles by color (map function):

```
curl -X GET http://127.0.0.1:5990/mychannel_marbles/_design/colorviewdesign/_view/colorview?group=true
```

The command will return a list of marbles by color from the CouchDB database.

```
{"rows":[
  {"key":"blue","value":2},
  {"key":"green","value":2},
  {"key":"purple","value":3},
  {"key":"red","value":4},
  {"key":"white","value":6},
  {"key":"yellow","value":2}
  ]}
```

To run a more complex command that reads through the block history database, we
will create an index of the blocknumber, sequence, and key fields. This index
will support a query that traces the history of each marble. Execute the
following command to create the index:

```
curl -X POST http://127.0.0.1:5990/mychannel_marbles_history/_index -d '{"index":{"fields":["blocknumber", "sequence", "key"]},"name":"marble_history"}'  -H 'Content-Type:application/json'
```

Now execute a query to retrieve the history for the marble we transferred and
then deleted:

```
curl -X POST http://127.0.0.1:5990/mychannel_marbles_history/_find -d '{"selector":{"key":{"$eq":"marble110"}}, "fields":["blocknumber","is_delete","value"],"sort":[{"blocknumber":"asc"}, {"sequence":"asc"}]}'  -H 'Content-Type:application/json'
```

You should see the transaction history of the marble that was created,
transferred, and then removed from the ledger.

```
{"docs":[
{"blocknumber":12,"is_delete":false,"value":"{\"docType\":\"marble\",\"name\":\"marble110\",\"color\":\"blue\",\"size\":60,\"owner\":\"debra\"}"},
{"blocknumber":22,"is_delete":false,"value":"{\"docType\":\"marble\",\"name\":\"marble110\",\"color\":\"blue\",\"size\":60,\"owner\":\"james\"}"},
{"blocknumber":23,"is_delete":true,"value":""}
  ]}
```

## Getting historical data from the network

You can also use the `blockEventListener.js` program to retrieve historical data
from your network. This allows you to create a database that is up to date with
the latest data from the network or recover any blocks that the program may
have missed.

If you ran through the example steps above, navigate back to the terminal window
where `blockEventListener.js` is running and close it. Once the listener is no
longer running, use the following command to add 20 more marbles to the
ledger:

```
node addMarbles.js
```

The listener will not be able to add the new marbles to your CouchDB database.
If you check the current state table using the reduce command, you will only
be able to see the original marbles in your database.

```
curl -X GET http://127.0.0.1:5990/mychannel_marbles/_design/colorviewdesign/_view/colorview?reduce=true
```

To add the new data to your off-chain database, remove the `nextblock.txt`
file that kept track of the latest block read by `blockEventListener.js`:

```
rm nextblock.txt
```

You can new re-run the channel listener to read every block from the channel:

```
node blockEventListener.js
```

This will rebuild the CouchDB tables and include the 20 marbles that have been
added to the ledger. If you run the reduce command against your database one
more time,

```
curl -X GET http://127.0.0.1:5990/mychannel_marbles/_design/colorviewdesign/_view/colorview?reduce=true
```

you will be able to see that all of the marbles have been added to your
database:

```
{"rows":[
{"key":null,"value":39}
]}
```

## Clean up

If you are finished using the sample application, you can bring down the network
and any accompanying artifacts.

* Change to `fabric-samples/first-network` directory.
* To stop the network, run `./byfn.sh down`.
* Change back to `fabric-samples/off_chain_data` directory.
* Remove the certificates you generated by deleting the `wallet` folder.
* Delete `nextblock.txt` so you can start with the first block next time you
  operate the listener. You can also reset the `nextMarbleNumber` in
  `addMarbles.json` to 100.
* To take down the local CouchDB database, first stop and then remove the
  docker container:
  ```
  docker stop offchaindb
  docker rm offchaindb
  ```
