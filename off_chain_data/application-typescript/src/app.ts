/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import * as grpc from '@grpc/grpc-js';
import { newGrpcConnection } from './connect';
import { ExpectedError } from './expectedError';
import { main as getAllAssets } from './getAllAssets';
import { main as listen } from './listen';
import { main as transact } from './transact';

const allCommands: Record<string, (client: grpc.Client) => Promise<void>> = {
    getAllAssets,
    listen,
    transact,
};

async function main(): Promise<void> {
    const commands = process.argv.slice(2).map(name => {
        const command = allCommands[name];
        if (!command) {
            printUsage();
            throw new Error(`Unknown command: ${name}`);
        }

        return command;
    });
    if (commands.length === 0) {
        printUsage();
        throw new Error('Missing command');
    }

    const client = await newGrpcConnection();
    try {
        for (const command of commands) {
            await command(client);
        }
    } finally {
        client.close();
    }
}

function printUsage(): void {
    console.log('Arguments: <command1> [<command2> ...]');
    console.log('Available commands:', Object.keys(allCommands).join(', '));
}

main().catch(error => {
    if (error instanceof ExpectedError) {
        console.log(error);
    } else {
        console.error('\nUnexpected application error:', error);
        process.exitCode = 1;
    }
});
