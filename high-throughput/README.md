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
This sample provides the chaincode and scripts required to run a high-throughput application. For ease of use, it runs on the same network which is brought
up by `byfn.sh` in the `first-network` folder within `fabric-samples`, albeit with a few small modifications. The instructions to build the network
and run some invocations are provided below.

### Build your network
1. `cd` into the `first-network` folder within `fabric-samples`, e.g. `cd ~/fabric-samples/first-network`
2. Open `docker-compose-cli.yaml` in your favorite editor, and edit the following lines:
  * In the `volumes` section of the `cli` container, edit the second line which refers to the chaincode folder to point to the chaincode folder
    within the `high-throughput` folder, e.g.

    `./../chaincode/:/opt/gopath/src/github.com/hyperledger/fabric-samples/chaincode` -->
    `./../high-throughput/chaincode/:/opt/gopath/src/github.com/hyperledger/fabric-samples/chaincode`
  * Again in the `volumes` section, edit the fourth line which refers to the scripts folder so it points to the scripts folder within the
    `high-throughput` folder, e.g.

    `./scripts:/opt/gopath/src/github.com/hyperledger/fabric/peer/scripts/` -->
    `./../high-throughput/scripts/:/opt/gopath/src/github.com/hyperledger/fabric/peer/scripts/`

  * Finally, comment out the `docker exec cli scripts/script.sh` command from the `byfn.sh` script by placing a `#` before it so that the standard BYFN end to end script doesn't run, e.g.

    `#  docker exec cli scripts/script.sh $CHANNEL_NAME $CLI_DELAY $CC_SRC_LANGUAGE $CLI_TIMEOUT $VERBOSE`

3. We can now bring our network up by typing in `./byfn.sh up -c mychannel`
4. Open a new terminal window and enter the CLI container using `docker exec -it cli bash`, all operations on the network will happen within
   this container from now on.

### Vendor the chaincode dependencies
1. Outside of the CLI container, change into the chaincode directory, e.g. `cd ~/fabric-samples/high-throughput/chaincode`
2. Vendor the Go dependencies by running the following command: `GO111MODULE=on go mod vendor`
3. The chaincode directory will now contain a `vendor` directory.

### Install and define the chaincode
1. Once you're in the CLI container run `cd scripts` to enter the `scripts` folder
2. Set-up the environment variables by running `source setclienv.sh`
3. Set-up your channels and anchor peers by running `./channel-setup.sh`
4. Package and install your chaincode by running `./install-chaincode.sh 1`. The only argument is a number representing the    chaincode version, every time
   you want to install and upgrade to a new chaincode version simply increment this value by 1 when running the command, e.g. `./install-chaincode.sh 2`
5. Define your chaincode on the channel by running `./approve-commit-chaincode.sh 1`. The version argument serves the same purpose as in `./install-chaincode.sh 1`
   and should match the version of the chaincode you just installed. This script also invokes the chaincode `Init` function to start the chaincode container.
   You can also upgrade the chaincode to a newer version by running `./approve-commit-chaincode.sh 2`.
6. Your chaincode is now installed and ready to receive invocations

### Invoke the chaincode
All invocations are provided as scripts in `scripts` folder; these are detailed below.

#### Update
The format for update is: `./update-invoke.sh name value operation` where `name` is the name of the variable to update, `value` is the value to
add to the variable, and `operation` is either `+` or `-` depending on what type of operation you'd like to add to the variable. In the future,
multiply/divide operations will be supported (or add them yourself to the chaincode as an exercise!)

Example: `./update-invoke.sh myvar 100 +`

#### Get
The format for get is: `./get-invoke.sh name` where `name` is the name of the variable to get.

Example: `./get-invoke.sh myvar`

#### Delete
The format for delete is: `./delete-invoke.sh name` where `name` is the name of the variable to delete.

Example: `./delete-invoke.sh myvar`

#### Prune
Pruning takes all the deltas generated for a variable and combines them all into a single row, deleting all previous rows. This helps cleanup
the ledger when many updates have been performed.

The format for pruning is: `./prune-invoke.sh name` where `name` is the name of the variable to prune.

Example: `./prune-invoke.sh myvar`

### Test the Network
Two scripts are provided to show the advantage of using this system when running many parallel transactions at once: `many-updates.sh` and
`many-updates-traditional.sh`. The first script accepts the same arguments as `update-invoke.sh` but duplicates the invocation 1000 times
and in parallel. The final value, therefore, should be the given update value * 1000. Run this script to confirm that your network is functioning
properly. You can confirm this by checking your peer and orderer logs and verifying that no invocations are rejected due to improper versions.

The second script, `many-updates-traditional.sh`, also sends 1000 transactions but using the traditional storage system. It'll update a single
row in the ledger 1000 times, with a value incrementing by one each time (i.e. the first invocation sets it to 0 and the last to 1000). The
expectation would be that the final value of the row is 999. However, the final value changes each time this script is run and you'll find
errors in the peer and orderer logs.

There are two other scripts, `get-traditional.sh`, which simply gets the value of a row in the traditional way, with no deltas, and `del-traditional.sh` will delete an asset in the traditional way.

Examples:
`./many-updates.sh testvar 100 +` --> final value from `./get-invoke.sh testvar` should be 100000

`./many-updates-traditional.sh testvar` --> final value from `./get-traditional.sh testvar` is undefined
