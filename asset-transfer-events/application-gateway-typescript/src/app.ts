/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import * as grpc from '@grpc/grpc-js';
import { ChaincodeEvent, CloseableAsyncIterable, connect, Contract, GatewayError, Network } from '@hyperledger/fabric-gateway';
import { TextDecoder } from 'util';
import { newGrpcConnection, newIdentity, newSigner } from './connect';

const channelName = 'mychannel';
const chaincodeName = 'events';

const utf8Decoder = new TextDecoder();
const now = Date.now();
const assetId = `asset${now}`;


async function main(): Promise<void> {
    const client = await newGrpcConnection();
    const gateway = connect({
        client,
        identity: await newIdentity(),
        signer: await newSigner(),
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

    let events: CloseableAsyncIterable<ChaincodeEvent> | undefined;

    try {
        const network = gateway.getNetwork(channelName);
        const contract = network.getContract(chaincodeName);

        // Listen for events emitted by subsequent transactions
        events = await startEventListening(network);

        const firstBlockNumber = await createAsset(contract);
        await updateAsset(contract);
        await transferAsset(contract);
        await deleteAssetByID(contract);

        // Replay events from the block containing the first transaction
        await replayChaincodeEvents(network,firstBlockNumber);
    } finally {
        events?.close();
        gateway.close();
        client.close();
    }
}

main().catch(error => {
    console.error('******** FAILED to run the application:', error);
    process.exitCode = 1;
});

async function startEventListening(network: Network): Promise<CloseableAsyncIterable<ChaincodeEvent>> {
    console.log('\n*** Start chaincode event listening');

    const events = await network.getChaincodeEvents(chaincodeName);

    void readEvents(events); // Don't await - run asynchronously
    return events;
}

async function readEvents(events: CloseableAsyncIterable<ChaincodeEvent>): Promise<void> {
    try {
        for await (const event of events) {
            const payload = parseJson(event.payload);
            console.log(`\n<-- Chaincode event received: ${event.eventName} -`, payload);
        }
    } catch (error: unknown) {
        // Ignore the read error when events.close() is called explicitly
        if (!(error instanceof GatewayError) || error.code !== grpc.status.CANCELLED) {
            throw error;
        }
    }
}

function parseJson(jsonBytes: Uint8Array): unknown {
    const json = utf8Decoder.decode(jsonBytes);
    return JSON.parse(json);
}

async function createAsset(contract: Contract): Promise<bigint> {
    console.log(`\n--> Submit Transaction: CreateAsset, ${assetId} owned by Sam with appraised value 100`);

    const result = await contract.submitAsync('CreateAsset', {
        arguments: [ assetId, 'blue', '10', 'Sam', '100' ],
    });

    const status = await result.getStatus();
    if (!status.successful) {
        throw new Error(`failed to commit transaction ${status.transactionId} with status code ${status.code}`);
    }

    console.log('\n*** CreateAsset committed successfully');

    return status.blockNumber;
}

async function updateAsset(contract: Contract): Promise<void> {
    console.log(`\n--> Submit transaction: UpdateAsset, ${assetId} update appraised value to 200`);

    await contract.submitTransaction('UpdateAsset', assetId, 'blue', '10', 'Sam', '200');

    console.log('\n*** UpdateAsset committed successfully');
}

async function transferAsset(contract: Contract): Promise<void> {
    console.log(`\n--> Submit transaction: TransferAsset, ${assetId} to Mary`);

    await contract.submitTransaction('TransferAsset', assetId, 'Mary');

    console.log('\n*** TransferAsset committed successfully');
}

async function deleteAssetByID(contract: Contract): Promise<void>{
    console.log(`\n--> Submit transaction: DeleteAsset, ${assetId}`);

    await contract.submitTransaction('DeleteAsset', assetId);

    console.log('\n*** DeleteAsset committed successfully');
}

async function replayChaincodeEvents(network: Network, startBlock: bigint): Promise<void> {
    console.log('\n*** Start chaincode event replay');
    
    const events = await network.getChaincodeEvents(chaincodeName, {
        startBlock,
    });

    try {
        for await (const event of events) {
            const payload = parseJson(event.payload);
            console.log(`\n<-- Chaincode event replayed: ${event.eventName} -`, payload);

            if (event.eventName === 'DeleteAsset') {
                // Reached the last submitted transaction so break to stop listening for events
                break;
            }
        }
    } finally {
        events.close();
    }
}
