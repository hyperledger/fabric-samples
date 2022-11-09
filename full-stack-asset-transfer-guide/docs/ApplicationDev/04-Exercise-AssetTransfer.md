# Exercise: Implement asset transfer

Currently, our trader application can only create, read, and delete assets by invoking the CreateAsset(), ReadAsset(), and DeleteAsset() chaincode functions. To really be useful it needs to be able to transfer assets to new owners by invoking the TransferAsset() chaincode function.

There is already a **transfer** command implemented in [transfer.ts](../../applications/trader-typescript/src/commands/transfer.ts), which calls the `transferAsset()` method on our **AssetTransfer** class. Unfortunately, this has not yet been implemented and does nothing.

1. Write an implementation for the `transferAsset()` method in [contract.ts](../../applications/trader-typescript/src/contract.ts). Look at the [API documentation for Contract](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Contract.html) and other methods within the **AssetTransfer** class for ideas on how to proceed.

1. Recompile the application from your updated TypeScript:
    ```bash
    npm install
    ```
    > **Tip:** You can also leave `npm run build:watch` running in a terminal window to automatically rebuild your application on any code change.

1. Try it out! Use the **transfer** command to transfer assets to new owners with the same MSP ID.

1. What happens if you try to manipulate (transfer, delete) an asset after transferring it to another MSP ID?

The smart contract contains logic that only allows users in the owning organization to modify assets. It does this by checking that the Member Services Provider (MSP) ID for the client identity invoking the transaction matches the organization MSP ID of the asset owner. If you didn't notice this before, you might want to check out the smart contract code to see how this is implemented.

## Optional steps

Implement an **update** command in the client application to modify the properties of an asset.
