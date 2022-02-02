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
    // The gRPC client connection should be shared by all Gateway connections to this endpoint.
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
        // Get a network instance representing the channel where the smart contract is deployed.
        const network = gateway.getNetwork(channelName);

        // Get the smart contract from the network.
        const contract = network.getContract(chaincodeName);

        //Start Listening to events.
        events = await startEventListening(network);

        // Create a new asset on the ledger.
        const firstBlockNumber = await createAsset(contract);

        // Update an existing asset.
        await updateAsset(contract)

        // Transfer an existing asset.
        await transferAsset(contract);

        // Delete asset by assetID.
        await deleteAssetByID(contract);

        // Replay all the events received.
        await replayChaincodeEvents(network,firstBlockNumber)

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

/**
 * Start listening to events.
 */
async function startEventListening(network: Network): Promise<CloseableAsyncIterable<ChaincodeEvent>> {
    console.log('\n*** Start chaincode event listening\n');

    const events = await network.getChaincodeEvents(chaincodeName);

    readEvents(events);
    return events;
}

/**
 * Read events.
 */
async function readEvents(events: CloseableAsyncIterable<ChaincodeEvent>): Promise<void> {
    try {
        for await (const event of events) {
            const payload = utf8Decoder.decode(event.payload);
            console.log(`\n<-- Chaincode event received, name: ${event.eventName}, payload: ${payload}, txID: ${event.transactionId}, blockNumber:${event.blockNumber}`);
        }
    } catch (error: unknown) {
        if (!(error instanceof GatewayError) || error.code !== grpc.status.CANCELLED) {
            throw error;
        }
    }
}

/**
 * Submit a transaction asynchronously to create a new asset.
 */
async function createAsset(contract: Contract): Promise<bigint> {
    console.log(`\n --> Submit Transaction: CreateAsset, creates ${assetId} owned by Tom with appraised value 100`);

    const result = await contract.submitAsync('CreateAsset',
        {arguments: [assetId,'yellow','5','Tom','100',]});

    const status = await result.getStatus();
    if (!status.successful) {
        throw new Error(`failed to commit transaction ${status.transactionId} with status code ${status.code}`);
    }

    console.log('\n*** CreateAsset committed successfully\n')

    return status.blockNumber;
}
/**
 * Submit transaction synchronously, to updateAsset the asset appraised value.
 */
async function updateAsset(contract: Contract): Promise<void> {
    console.log(`\n--> Submit transaction: UpdateAsset, ${assetId} update appraised value to 200`);

    await contract.submitTransaction('UpdateAsset',
        assetId,'yellow','5','Tom','200',);

    console.log('\n*** UpdateAsset committed successfully\n')
}

/**
 * Submit transaction synchronously, to transfer the asset.
 */
async function transferAsset(contract: Contract): Promise<void> {
    console.log(`\n--> Submit transaction: TransferAsset, ${assetId} to Saptha\n`);

    await contract.submitTransaction('TransferAsset',
        assetId, 'Saptha',);

    console.log('\n*** TransferAsset committed successfully\n')
}

/**
 * Submit a transaction synchronously to delete an asset by ID.
 */
async function deleteAssetByID(contract:Contract): Promise<void>{
    console.log(`\n--> Submit transaction: DeleteAsset ${assetId}`);

    await contract.submitTransaction('DeleteAsset',
        assetId
    );

    console.log('\n*** DeleteAsset committed successfully\n')
}

/**
  * Replay all the events from the start block.
  */
async function replayChaincodeEvents(network:Network,startBlock:bigint):Promise<void>{

    const events = await network.getChaincodeEvents(chaincodeName, {
        startBlock
    });
    try {
        for await (const event of events) {
            const payload = utf8Decoder.decode(event.payload);
            console.log(`<-- Chaincode event replayed: ${event.eventName}, payload: ${payload}`);
            if(event.eventName === 'DeleteAsset'){
                break
            }
        }
    }finally {
        events.close()
    }
}