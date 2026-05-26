# Application overview

This topic describes key parts of the client application and how it uses the Fabric Gateway client API to interact with the network. This knowledge will allow you to extend the application in subsequent topics.

## Connect to the Gateway service

Connection to the peer Gateway service is driven by the **runCommand()** function in [app.ts](../../applications/trader-typescript/src/app.ts). This calls to two other functions to perform the two tasks required before the client application can transact with the Fabric network:

1. **Create gRPC connection to peer Gateway endpoint** - this is done in the **newGrpcConnection()** function in [connect.ts](../../applications/trader-typescript/src/connect.ts):
    ```typescript
    const tlsCredentials = grpc.credentials.createSsl(tlsRootCert);
    return new grpc.Client(GATEWAY_ENDPOINT, tlsCredentials);
    ```
    The gRPC client connection is established using the [gRPC API](https://grpc.io/docs/) and is managed by the client application. The application can use the same gRPC connection to transact on behalf of many client identities.

1. **Create peer Gateway connection** - this is done in the **newGatewayConnection()** function in [connect.ts](../../applications/trader-typescript/src/connect.ts):
    ```typescript
    return connect({
        client,
        identity: await newIdentity(),
        signer: await newSigner(),
        // Default timeouts for different gRPC calls
        evaluateOptions: () => {
            return { deadline: Date.now() + 5000 }; // 5 seconds
        },
        endorseOptions: () => {
            return { deadline: Date.now() + 15000 }; // 15 seconds
        },
        submitOptions: () => {
            return { deadline: Date.now() + 5000 }; // 5 seconds
        },
        commitStatusOptions: () => {
            return { deadline: Date.now() + 60000 }; // 1 minute
        },
    });
    ```
    The **Gateway** connection is established by calling the [connect()](https://hyperledger.github.io/fabric-gateway/main/api/node/functions/connect.html) factory function with a client [identity](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Identity.html) (user's X.509 certificate) and [signing implementation](https://hyperledger.github.io/fabric-gateway/main/api/node/functions/signers.newPrivateKeySigner.html) (based on the user's private key). It allows a specific user to interact with a Fabric network using the previously created gRPC connection. Optional configuration can also be supplied, and it is strongly recommended to include default timeouts for operations.

## Application CLI commands

All the CLI command implementations are located within the [commands](../../applications/trader-typescript/src/commands/) directory. Commands are exposed to [app.ts](../../applications/trader-typescript/src/app.ts) by [commands/index.ts](../../applications/trader-typescript/src/commands/index.ts).

When invoked, the command is passed the **Gateway** instance it should use to interact with the Fabric network. To do useful work, command implementations typically performs these steps:

1. **Get Network** - this represents a network of Fabric nodes belonging to a specific Fabric channel:
    ```typescript
    const network = gateway.getNetwork(CHANNEL_NAME);
    ```

1. **Get Contract** - this represents a specific smart contract deployed in the **Network**:
    ```typescript
    const contract = network.getContract(CHAINCODE_NAME);
    ```

1. **Create smart contract adapter** - this provides a view of the smart contract and its transaction functions in form that is easy to use for the client application business logic:
    ```typescript
    const smartContract = new AssetTransfer(contract);
    ```

1. **Invoke transaction functions on a deployed chaincode** - for example:
    - Create an asset in [commands/create.ts](../../applications/trader-typescript/src/commands/create.ts)
        ```typescript
        await smartContract.createAsset({
            ID: assetId,
            Owner: owner,
            Color: color,
            Size: 1,
            AppraisedValue: 1,
        });
        ```
    - Read all assets in [commands/getAllAssets.ts](../../applications/trader-typescript/src/commands/getAllAssets.ts)
        ```typescript
        const assets = await smartContract.getAllAssets();
        ```

The application CLI commands represent a simplified application that performs one action per call. Note that real world applications will typically be long running, and will re-use a connection to the peer Gateway service when making transaction requests on behalf of client applications. The connection may utilize a single organization identity on behalf of various user requests.

## Gateway API calls

The **AssetTransfer** class in [contract.ts](../../applications/trader-typescript/src/contract.ts) presents the smart contract in a form appropriate to the business application. Internally it uses the Fabric Gateway client API to invoke transaction functions, and deals with the translation between the business application and API representation of parameters and return values.

Refer to the [Contract API documentation](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Contract.html) for more details on the available calls.

### Transaction submit

The transaction submit function will submit the request to the peer Gateway service. The peer Gateway service will invoke chaincode and collect the required endorsements from different organization's peers to meet the contract's endorsement policy, and will then submit the transaction to the ordering service on behalf of the client application so that the blockchain ledger can be updated.

An example of transaction submit is in the **createAsset()** method:

```typescript
await this.#contract.submit('CreateAsset', {
    arguments: [JSON.stringify(asset)],
});
```

### Transaction evaluate

The transaction evaluate function will request the peer Gateway service to invoke the chaincode and return the results to the client, without submitting a transaction to the ordering service. Use the evaluate function to query the state of the blockchain ledger.

An example of evaluating a transaction is in the **getAllAssets()** method:
```typescript
const result = await this.#contract.evaluate('GetAllAssets');
```

## Retry of transaction submit

The nature of the transaction submit flow in Fabric means that failures can occur at different points in the flow. To aid client handling of failures, the Gateway API produces errors of specific types to indicate the point in the flow a failure occurred. The **submitWithRetry()** function in [contract.ts](../../applications/trader-typescript/src/contract.ts) retries transactions that fail to commit successfully:

```typescript
let lastError: unknown | undefined;

for (let retryCount = 0; retryCount < RETRIES; retryCount++) {
    try {
        return await submit();
    } catch (err: unknown) {
        lastError = err;
        if (err instanceof CommitError) {
            // Transaction failed validation and did not update the ledger. Handle specific transaction validation codes.
            if (err.code === StatusCode.MVCC_READ_CONFLICT) {
                continue; // Retry
            }
        }
        break; // Failure -- don't retry
    }
}

throw lastError;
```

See the [submit() API documentation](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Contract.html#submit) for the other error types that can be thrown.

For some cases it can be useful to retry only a specific step within the transaction submit flow. The Gateway API provides a fine-grained flow to allow this. See the [Contract API documentation](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Contract.html) for examples of this fine-grained flow.
