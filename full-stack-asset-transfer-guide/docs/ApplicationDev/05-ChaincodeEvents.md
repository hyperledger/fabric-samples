# Chaincode events

A smart contract transaction function can emit a chaincode event to communicate business events. These events are emitted only after a transaction is successfully committed and updates the ledger. Transactions that fail validation do not emit chaincode events.

Client applications can listen for chaincode events and trigger external business processes in response to ledger updates. An example might be to schedule collection of a parcel after a delivery order is received. Events can either be replayed from any point in the blockchain, or received in realtime.

When emitting a chaincode event, the smart contract can specify an arbitrary **payload** to be included in the event. The **payload** is used to communicate business context to client applications receiving the chaincode events.

The [Network](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Network.html) object in the Gateway API provides methods to obtain chaincode events.

To ensure the correct operation of business processes, it is important that each chaincode event is received exactly once. We don't want to collect the same parcel twice, or to miss a parcel collection!

The Gateway API allows a [Checkpointer](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Checkpointer.html) to be used to track (or checkpoint) successfully processed events, and for eventing to be resumed exactly after the last checkpointed event if a failure or application restart occurs.

For convenience, the Gateway API provides two checkpointer implementations:

1. [File checkpointer](https://hyperledger.github.io/fabric-gateway/main/api/node/functions/checkpointers.file.html) that persists its state to the file-system. This can be used to resume eventing, even after an application restart.
1. [In-memory checkpointer](https://hyperledger.github.io/fabric-gateway/main/api/node/functions/checkpointers.inMemory.html) that stores its state only in-memory. This can be used to recover from transient failures, such as a network communication error, during a single application run.

Client applications can also use their own checkpointer implementations, which persist their state in suitable storage such as a database, provided they conform to the simple [Checkpoint](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Checkpoint.html) interface.
