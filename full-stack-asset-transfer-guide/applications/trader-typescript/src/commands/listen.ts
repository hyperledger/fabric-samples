/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { ChaincodeEvent, checkpointers, Gateway } from '@hyperledger/fabric-gateway';
import * as path from 'path';
import { CHAINCODE_NAME, CHANNEL_NAME } from '../config';
import { ExpectedError } from '../expectedError';
import { printable } from '../utils';

const checkpointFile = path.resolve(process.env.CHECKPOINT_FILE ?? 'checkpoint.json');
const simulatedFailureCount = getSimulatedFailureCount();

const startBlock = BigInt(0);

export default async function main(gateway: Gateway): Promise<void> {
    const network = gateway.getNetwork(CHANNEL_NAME);
    const checkpointer = await checkpointers.file(checkpointFile);

    console.log(`Starting event listening from block ${checkpointer.getBlockNumber() ?? startBlock}`);
    console.log('Last processed transaction ID within block:', checkpointer.getTransactionId());
    if (simulatedFailureCount > 0) {
        console.log(`Simulating a write failure every ${simulatedFailureCount} transactions`);
    }

    const events = await network.getChaincodeEvents(CHAINCODE_NAME, {
        startBlock, // Used only if there is no checkpoint block number
    });

    try {
        for await (const event of events) {
            onEvent(event);
        }
    } finally {
        events.close();
    }
}

function onEvent(event: ChaincodeEvent): void {
    simulateFailureIfRequired();

    console.log(printable(event));
}

function getSimulatedFailureCount(): number {
    const value = process.env.SIMULATED_FAILURE_COUNT ?? '0';
    const count = Math.floor(Number(value));
    if (isNaN(count) || count < 0) {
        throw new Error(`Invalid SIMULATED_FAILURE_COUNT value: ${String(value)}`);
    }

    return count;
}

let eventCount = 0; // Used only to simulate failures

function simulateFailureIfRequired(): void {
    if (simulatedFailureCount > 0 && eventCount++ >= simulatedFailureCount) {
        eventCount = 0;
        throw new ExpectedError('Simulated write failure');
    }
}
