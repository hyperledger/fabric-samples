<!--
 Copyright IBM Corp All Rights Reserved

 SPDX-License-Identifier: Apache-2.0
-->

# High-Throughput Network

## Purpose
This network is used to understand how to properly design the chaincode data model when handling thousands of transactions per second which all
update the same asset in the ledger. A naive implementation would use a single key to represent the data for the asset, and the chaincode would
then attempt to update this key every time a transaction involving it comes in. However, when many transactions all come in at once, in the time
between when the transaction is simulated on the peer (i.e. read-set is created) and it's ready to be committed to the ledger, another transaction
may have already updated the same value. Thus, in the simple implementation, the read-set version will no longer match the version in the orderer,
and a large number of parallel transactions will fail. To solve this issue, the frequently updated value is instead stored as a series of deltas
which are aggregated when the value must be retrieved. In this way, no single row is frequently read and updated, but rather a collection of rows
is considered.

## Use Case
The primary use case for this chaincode data model design is for applications in which a particular asset has an associated amount that is
frequently added to or removed from. For example, with a bank or credit card account, money is either paid to or paid out of it, and the amount
of money in the account is the result of all of these additions and subtractions aggregated together. A typical person's bank account may not be
used frequently enough to require highly-parallel throughput, but an organizational account used to store the money collected from customers on an
e-commerce platform may very well receive a very high number of transactions from all over the world all at once. In fact, this use case is the only
use case for crypto currencies like Bitcoin: a user's unspent transaction output (UTXO) is the result of all transactions he or she has been a part of
since joining the blockchain. Other use cases that can employ this technique might be IOT sensors which frequently update their sensed value in the
cloud.

By adopting this method of storing data, an organization can optimize their chaincode to store and record transactions as quickly as possible and can
aggregate ledger records into one value at the time of their choosing without sacrificing transaction performance. Given the state-machine design of
Hyperledger Fabric, however, careful considerations need to be given to the data model design for the chaincode.

Let's look at some concrete use cases and how an organization might implement high-throughput storage. These cases will try and explore some of the
advantages and disadvantages of such a system, and how to overcome them.

#### Example 1 (IOT): Boxer Construction Analysts

Boxer Construction Analysts is an IOT company focused on enabling real-time monitoring of large, expensive assets (machinery) on commercial
construction projects. They've partnered with the only construction vehicle company in New York, Condor Machines Inc., to provide a reliable,
auditable, and replayable monitoring system on their machines. This allows Condor to monitor their machines and address problems as soon as
they occur while providing end-users with a transparent report on machine health, which helps keep the customers satisfied.

The vehicles are outfitted with many sensors each of which broadcasts updated values at frequencies ranging from several times a second to
several times a minute. Boxer initially sets up their chaincode so that the central machine computer pushes these values out to the blockchain
as soon as they're produced, and each sensor has its own row in the ledger which is updated when a new value comes in. While they find that
this works fine for the sensors which only update several times a minute, they run into some issues when updating the faster sensors. Often,
the blockchain skips several sensor readings before adding a new one, defeating the purpose of having a fast, always-on sensor. The issue they're
running into is that they're sending update transactions so fast that the version of the row is changed between the creation of a transaction's
read-set and committing that transaction to the ledger. The result is that while a transaction is in the process of being committed, all future
transactions are rejected until the commitment process is complete and a new, much later reading updates the ledger.

To address this issue, they adopt a high-throughput design for the chaincode data model instead. Each sensor has a key which identifies it within the
ledger, and the difference between the previous reading and the current reading is published as a transaction. For example, if a sensor is monitoring
engine temperature, rather than sending the following list: 220F, 223F, 233F, 227F, the sensor would send: +220, +3, +10, -6 (the sensor is assumed
to start a 0 on initialization). This solves the throughput problem, as the machine can post delta transactions as fast as it wants and they will all
eventually be committed to the ledger in the order they were received. Additionally, these transactions can be processed as they appear in the ledger
by a dashboard to provide live monitoring data. The only difference the engineers have to pay attention to in this case is to make sure the sensors can
send deltas from the previous reading, rather than fixed readings.

#### Example 2 (Balance Transfer): Robinson Credit Co.

Robinson Credit Co. provides credit and financial services to large businesses. As such, their accounts are large, complex, and accessed by many
people at once at any time of the day. They want to switch to blockchain, but are having trouble keeping up with the number of deposits and
withdrawals happening at once on the same account. Additionally, they need to ensure users never withdraw more money than is available
on an account, and transactions that do get rejected. The first problem is easy to solve, the second is more nuanced and requires a variety of
strategies to accommodate high-throughput storage model design.

To solve throughput, this new storage model is leveraged to allow every user performing transactions against the account to make that transaction in terms
of a delta. For example, global e-commerce company America Inc. must be able to accept thousands of transactions an hour in order to keep up with
their customer's demands. Rather than attempt to update a single row with the total amount of money in America Inc's account, Robinson Credit Co.
accepts each transaction as an additive delta to America Inc's account. At the end of the day, America Inc's accounting department can quickly
retrieve the total value in the account when the sums are aggregated.

However, what happens when American Inc. now wants to pay its suppliers out of the same account, or a different account also on the blockchain?
Robinson Credit Co. would like to be assured that America Inc.'s accounting department can't simply overdraw their account, which is difficult to
do while at the same enabling transactions to happen quickly, as deltas are added to the ledger without any sort of bounds checking on the final
aggregate value. There are a variety of solutions which can be used in combination to address this.

Solution 1 involves polling the aggregate value regularly. This happens separate from any delta transaction, and can be performed by a monitoring
service setup by Robinson themselves so that they can at least be guaranteed that if an overdraw does occur, they can detect it within a known
number of seconds and respond to it appropriately (e.g. by temporarily shutting off transactions on that account), all of which can be automated.
Furthermore, thanks to the decentralized nature of Fabric, this operation can be performed on a peer dedicated to this function that would not
slow down or impact the performance of peers processing customer transactions.

Solution 2 involves breaking up the submission and verification steps of the balance transfer. Balance transfer submissions happen very quickly
and don't bother with checking overdrawing. However, a secondary process reviews each transaction sent to the chain and keeps a running total,
verifying that none of them overdraw the account, or at the very least that aggregated withdrawals vs deposits balance out at the end of the day.
Similar to Solution 1, this system would run separate from any transaction processing hardware and would not incur a performance hit on the
customer-facing chain.

Solution 3 involves individually tailoring the smart contracts between Robinson and America Inc, leveraging the power of chaincode to customize
spending limits based on solvency proofs. Perhaps a limit is set on withdrawal transactions such that anything below \$1000 is automatically processed
and assumed to be correct and at minimal risk to either company simply due to America Inc. having proved solvency. However, withdrawals above \$1000
must be verified before approval and admittance to the chain.

## How
This sample provides the chaincode and scripts required to run a high-throughput application on the Fabric test network.

### Start the network

You can use the `startFabric.sh` script to create an instance of the Fabric test network with a single channel named `mychannel`. The script then deploys the `high-throughput` chaincode to the channel by installing it on the test network peers and committing the chaincode definition to the channel.

Change back into the `high-throughput` directory in `fabic-samples`. Start the network and deploy the chaincode by issuing the following command:
```
./startFabric.sh
```

If successful, you will see messages of the Fabric test network being created and the chaincode being deployed, followed by the execution time of the script:
```
Total setup execution time : 81 secs ...
```

The `high-throughput` chaincode is now ready to receive invocations.

### Invoke the chaincode

You can invoke the `high-througput` chaincode using a Go application in the `application-go` folder. The Go application will allow us to submit many transactions to the network concurrently. Navigate to the application:
```
cd application-go
```

#### Update
The format for update is: `go run app.go update name value operation` where `name` is the name of the variable to update, `value` is the value to add to the variable, and `operation` is either `+` or `-` depending on what type of operation you'd like to add to the variable.

Example: `go run app.go update myvar 100 +`

#### Query
You can query the value of a variable by running `go run app.go get name` where `name` is the name of the variable to get.

Example: `go run app.go get myvar`

#### Prune
Pruning takes all the deltas generated for a variable and combines them all into a single row, deleting all previous rows. This helps cleanup the ledger when many updates have been performed.

The format for pruning is: `go run app.go prune name` where `name` is the name of the variable to prune.

Example: `go run app.go prune myvar`

#### Delete
The format for delete is: `go run app.go delete name` where `name` is the name of the variable to delete.

Example: `go run app.go delete myvar`

### Test the Network

The application provides two methods that demonstrate the advantages of this system by submitting many concurrent transactions to the smart contract: `manyUpdates` and `manyUpdatesTraditional`. The first function accepts the same arguments as `update-invoke.sh` but runs the invocation 1000 times in parallel. The final value, therefore, should be the given update value * 1000.

The second function, `manyUpdatesTraditional`, submits 1000 transactions that attempt to update the same key in the world state 1000 times.

Run the following command to create and update `testvar1` a 1000 times:
```
go run app.go manyUpdates testvar1 100 +
```

The application will query the variable after submitting the transaction. The result should be `100000`.

We will now see what happens when you try to run 1000 concurrent updates using a traditional transaction. Run the following command to create a variable named `testvar2`:
```
go run app.go update testvar2 100 +
```
The variable will have a value of 100:
```
2020/10/27 18:01:45 Value of variable testvar2 :  100
```

Now lets try to update `testvar2` 1000 times in parallel:
```
go run app.go manyUpdatesTraditional testvar2 100 +
```

When the program ends, you may see that none of the updates succeeded.
```
2020/10/27 18:03:15 Final value of variable testvar2 :  100
```

The transactions failed because multiple transactions in each block updated the same key. Because of these transactions generated read/write conflicts, the transactions included in each block were rejected in the validation stage.

You can can examine the peer logs to view the messages generated by the rejected blocks:


```
docker logs peer0.org1.example.com
```

```
[...]
2020-10-28 17:37:58.746 UTC [gossip.privdata] StoreBlock -> INFO 2190 [mychannel] Received block [407] from buffer
2020-10-28 17:37:58.749 UTC [committer.txvalidator] Validate -> INFO 2191 [mychannel] Validated block [407] in 2ms
2020-10-28 17:37:58.750 UTC [validation] validateAndPrepareBatch -> WARN 2192 Block [407] Transaction index [0] TxId [b6b14cf988b0d7d35d4e0d7a0d2ae0c9f5569bc10ec5010f03a28c22694b8ef6] marked as invalid by state validator. Reason code [MVCC_READ_CONFLICT]
2020-10-28 17:37:58.750 UTC [validation] validateAndPrepareBatch -> WARN 2193 Block [407] Transaction index [1] TxId [9d7c4f6ff95a0f22e01d6ffeda261227752e78db43f2673ad4ea6f0fdace44d1] marked as invalid by state validator. Reason code [MVCC_READ_CONFLICT]
2020-10-28 17:37:58.750 UTC [validation] validateAndPrepareBatch -> WARN 2194 Block [407] Transaction index [2] TxId [9cc228b61d8841208feb6160254aee098b1b3a903f645e62cfa12222e6f52e65] marked as invalid by state validator. Reason code [MVCC_READ_CONFLICT]
2020-10-28 17:37:58.750 UTC [validation] validateAndPrepareBatch -> WARN 2195 Block [407] Transaction index [3] TxId [2ae78d363c30b5f3445f2b028ccac7cf821f1d5d5c256d8c17bd42f33178e2ed] marked as invalid by state validator. Reason code [MVCC_READ_CONFLICT]
```

### Clean up

When you are finished using the `high-throughput` chaincode, you can bring down the network and remove any accompanying artifacts using the `networkDown.sh` script.

```
./networkDown.sh
```
