# Exercise: Use chaincode events

First, let's try listening for chaincode events to see what information is included in events emitted by the smart contract transaction functions.

In a new terminal window, navigate to the [applications/trader-typescript](../../applications/trader-typescript/) directory so that we can run the listen application.
It is assumed that you have already built the application in prior steps.

1. If you are using a new terminal window, set environment variables to point to resources required by the application.
    ```bash
    export ENDPOINT=org1peer-api.127-0-0-1.nip.io:8080
    export MSP_ID=org1MSP
    export CERTIFICATE=../../_cfg/uf/_msp/org1/org1admin/msp/signcerts/cert.pem
    export PRIVATE_KEY=../../_cfg/uf/_msp/org1/org1admin/msp/keystore/cert_sk
    ```

1. Run the **listen** command to listen for ledger updates. The listen command will return prior events and also wait for future events.
    ```bash
    npm start listen
    ```

1. Once you have received the available events, interrupt the application using `Control-C`.

1. Run the **listen** command again. What do we see this time?

On the second run of the **listen** command, you should have seen exactly the same output as the first run. This is because each run of the **listen** command retrieves all chaincode events from start of the blockchain. That's not so useful if we want to invoke external business processes in response to chaincode events. It would be much better if each event was received exactly once, regardless of whether the client application is restarted.

Let's implement checkpointing to ensure there are no duplicate or missed events.

5. Implement checkpointing for the reading of chaincode events in [listen.ts](../../applications/trader-typescript/src/commands/listen.ts). Look at the [API documentation for Network](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Network.html) for ideas on how to proceed. Be sure to only checkpoint events *after* they are successfully processed!

1. Ensure your changes are compiled, then run the **listen** command with the SIMULATED_FAILURE_COUNT environment variable set to simulate an application error during the processing of a chancode event:
    ```bash
    SIMULATED_FAILURE_COUNT=3 npm start listen
    ```

1. Run the **listen** command again. You should see event listening resume from the same chaincode event that the application failed to process on the previous run.

> **Note:** The checkpointer persists its current listening position in a `checkpoint.json` file. If you want to remove the checkpointer's stored state and start listening from the `startBlock` again, remove the `checkpoint.json` file while the checkpointer is not in use.

## Optional steps

So far we have been replaying previously emitted chaincode events. Let's use the **listen** command to notify us in realtime when we take ownership of assets.

8. Modify the **onEvent()** function in [listen.ts](../../applications/trader-typescript/src/commands/listen.ts) to notify you if you become the owner of a new (`CreateAsset` event) or transferred (`TransferAsset` event) asset. Note that the `payload` property of the event is a [Uint8Array](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Uint8Array) containing the [JSON](https://en.wikipedia.org/wiki/JSON) emitted by the smart contract. Look at the **readAsset()** method in [contract.ts](../../applications/trader-typescript/src/contract.ts) for ideas on how to convert this into a JavaScript object so you can inspect its `Owner` property.

1. Try running the **listen** command in one terminal window while using another terminal window to create and transfer assets.
