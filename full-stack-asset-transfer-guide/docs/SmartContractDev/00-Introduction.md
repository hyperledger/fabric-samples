# Smart Contract Development

In this section, we'll introduce the concept of the Smart Contract, and how the Hyperledger Fabric platform handles these contracts. We'll talk about some of the important aspects to keep in mind; these can be different from other types of development.

This example shows how details of an asset may be stored in the ledger itself, with the Smart Contract controlling the asset's lifecycle. It provides transaction functions to

- Create an asset
- Retrieve (one or all) assets
- Update an asset
- Delete an asset
- Transfer ownership between parties

This topic addresses common design considerations for smart contracts. If you want to dive straight into the workshop code, feel free to skip straight to the [getting started](./01-Exercise-Getting-Started.md) and come back to this page as a reference later.

Please remember that Hyperledger Fabric is a Blockchain Ledger and not a Database!

[NEXT - Getting Stared with Code](./01-Exercise-Getting-Started.md)

---
## Design the assets and contracts

An initial and perhaps the most important decision is - "what information needs to be under the ledger's control?".  This is important for several reasons.

- The ledger is the shared state between organizations, is the information being shared applicable for all organizations to see? Note that Fabric's private data collections can be used when a subset of the ledger data needs to remain private between parties.
- For any ledger state updates, which organizations need to execute the smart contract and 'endorse' the results before the change is considered a valid transaction? Is the endorsement policy fixed, or based on the data? For example you may want the current asset owner's organization to endorse, along with a regulator organization. This can be accomplished with Fabric's state-based endorsement capabilities.
- How large is the amount of data? The smaller the better! Remember this data needs to be transferred between many parties and the history of transactions is preserved on the ledger. Would a salted secure hash of a scanned document work rather than the entire document?
- Though rich JSON query of the state is possible when using CouchDB as a state database, the peer and its ledger are optimized for transaction processing rather than query. Can queries be performed off the ledger, and the results 'validated' via a smart contract?

For any end-to-end tutorial there is a trade-off between making the scenario realistic, but not sufficiently complicated. For this tutorial, we'll define the data stored on the ledger as being a single asset 'object' with the following fields. This has been kept very simple, but the approach should be familiar.

- ID: string unique-identifier
- Color: string representing a color
- Size: numerical value representing a size
- Owner: string representing the identity of the asset owner (organization id plus common name from the client's certificate)
- Appraised Value: numerical value

Arguably not all these values need to be on the ledger, the data could be stored in an off-ledger 'oracle' that provides a hash of the data to store on the blockchain ledger.

### Keys and Queries

Think of the ledger state database as being a key-value store to persist assets, or more generally, any data record that you want to maintain on the ledger.

It is important to take care in choosing the 'key' that will be used. You could use simple keys such as a logical key passed in from an external system, a UUID, or even use the txid of the transaction that created the asset. Composite keys are also possible. These are constructed to form a hierarchical structure and enable additional types of key-based queries.

A key string can be formed from a list of strings, separated by the `u+0000` nil byte. There must be at least one string in the list. If there is only one string, this is referred to as `simple` key, otherwise, it is a `composite` key. For example you may want to create a composite key based on the asset 'type' and ID. The smart contract APIs help you easily create composite keys.

For simple keys, you can query based on the key using the API `getState(key: string)`.

You can also query a range of keys using the API `getStateByRange(startKey: string, endKey: string)`. Range queries allow you to specify a start and end key, and return an iterator of all key-values between those start and end points (inclusively). The keys are ordered in alphanumeric order.

Composite keys provide an interesting query mechanism as they offer a range query by partial key. For example, if a composite key has the strings `fruit:pineapples:supplier_fred:consignment_xx` (using a colon here to make it easier to read, as the nil byte isn't easy to read) then it is possible to issue queries with a leading partial key.
For example, to query all the pineapple records held by `supplier_fred` you could query on the partial key `fruit:pineapples:supplier_fred`.

A way of thinking about this is to visualize the keys as forming a hierarchy.

Note that the 'simple' and 'composite' keys are held distinct from each other. Therefore a query on a simple key won't return anything held under a composite key, and conversely a composite key query won't return anything held under a simple key.

The above types of queries are supported on both LevelDB and CouchDB state databases and query the 'keys' of the key-value store.

If you use CouchDB, you can also query on the 'value' of the key-value pair using a rich JSON query. This requires the value be in JSON format (as in this tutorial). CouchDB indices can be provided in the smart contract package to make JSON queries efficient (and strongly recommended). But keep in mind that 'value' based queries will never be as efficient as 'key' based queries.

## Transaction Functions

Let's look at the separate transaction function types that can be written on the Smart Contract. Each one of these can be invoked from the client application.
- 'Evaluate' functions are invoked in a read-only manner to query the ledger state database on a specific peer.
- 'Submit' functions are invoked to submit a transaction to all the peers that are needed to endorse changes to the ledger, resulting in a write operation or read-write operation that gets submitted to the ordering service and ultimately committed on all peers.

### General Aspects

Each smart contract transaction function needs to be marked as such (using language-specific conventions). You can also specify if the function is meant to be 'submitted' or 'evaluated'. This is not enforced but is an indication to the user.

Each function will need to consider how it handles data to marshal into the format needed for the ledger.

Each function needs to ensure that any initial state is correct. For example, before transferring an asset from Alice to Bob, ensure that Alice does own the asset, and that Alice is indeed the identity submitting the transfer transaction.

### Creation Functions

Consider in the create function if you want to pass in the individual data elements or a fully formed object. This is largely a matter of personal preference; remember though that any unique identifier must be created outside of the smart contract. Any form of random choice or other non-deterministic processes can not be used since the transaction will be executed on multiple peers and the results must match.

Often there are extra elements of data (such as the submitting organization) that need to be added.

### Retrieval

It is a good idea to think ahead of the types of retrieval operations that are needed. Can the key structure be created to allow for range queries?

If rich JSON queries are required, aim to make these as simple as possible and include indexes. Also ensure that if you wish to do a rich JSON query that involves the same data as the 'key' that it is included in the JSON structure as part of the 'value'.

There is an example of get-all type queries in this workshop. Please consider that over time this could get a very large amount of data with a performance cost, therefore it is generally not recommended!

For advanced queries, considering creating a downstream data store optimized for the types of queries that you need. The [off-chain data sample](https://github.com/hyperledger/fabric-samples/tree/main/off_chain_data) demonstrates how to build a downstream data store based on block events.

### Reading-your-own-writes and conflicts

The updates a transaction function makes to the state, aren't actioned immediately; they form a set of changes that must be endorsed and ordered. There are two important consequences of this asynchronous behaviour.

If data under a key is updated, and then queried *in the same smart contract function* the returned data will be the *original* value - not the updated value.

Additionally, you may see transactions invalidated with a 'MVCC Conflict' error: this means that two transaction functions have executed at the same time and attempted to read and update the same keys. The first transaction to be ordered in a block will get validated, while the second transaction will get invalidated since a read input has changed since contract execution. Design your keys and applications so that the same keys will not get updated concurrently. If this is a rare occurrence then you could simply compensate for it in the application, for example by re-issuing the transaction.

## Audit Trails vs Asset Store

An important decision to make is whether the state held on the ledger is representing an 'audit trail' of activity or the 'source of truth' of the actual assets.  Storing the information about the assets, as shown in the following samples, is conceptually straightforward but keep in mind that this is a distributed database, rather than a database.

Storing a form of audit trail can work well with the ledger concept. The 'source of truth' here is that a certain action was taken and it's results. For example the ownership of an asset changed. Details of the actual asset may be stored off chain. This does need more infrastructure provided around the ledger but is worth considering if the primary business reason is for audit purposes. For example, tracking the state of a process and how it moved from one state to the next.

To help with the integration of other systems it is well worth issuing events from the transaction functions. These events will be available to the client applications when the transaction is finally committed. These can be very useful in triggering other processes.

## Is it Smart Contract or Chaincode?

Simply both - the terms have been used in Fabric history almost interchangeable; Chaincode was the original name, but then Smart Contracts is a common a blockchain term. The class/structure that is extended/implemented in code is called `Contract`.

The aim is to standardize on
- the Smart Contract(s) are classes/structures - the code - that you write in Go/JavaScript/TypeScript/Java etc.
- these are then packaged up and run inside a Chaincode-container (chaincode-image / chaincode-runtime depending on exactly the format of the packaging)
- the chaincode definition is more than just the Smart Contract code, as it includes things such as the CouchDB indexes, and the endorsement policy

## Packaging

In Hyperledger Fabric v1.x and still supported as 'the old lifecycle' in v2.x, the CDS chaincode package format was used. The v2.x 'new lifecycle' should be used now - with standard `tar.gz` format. Using `tar` and `gzip` are standard techniques with standard tools. Therefore the main issue becomes what goes into those files and when/how are they used.
