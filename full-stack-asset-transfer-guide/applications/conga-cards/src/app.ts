/*
 * Copyright contributors to the Hyperledgendary Full Stack Asset Transfer Guide project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import * as sourceMapSupport from 'source-map-support';
sourceMapSupport.install();

import { Command, commands } from './commands';
import { newGatewayConnection, newGrpcConnection } from './connect';
import { ExpectedError } from './expectedError';

async function main(): Promise<void> {
    const commandName = process.argv[2];
    const args = process.argv.slice(3);

    const command = commands[commandName];
    if (!command) {
        printUsage();
        throw new Error(`Unknown command: ${commandName}`);
    }

    await runCommand(command, args);
}

async function runCommand(command: Command, args: string[]): Promise<void> {
    const client = await newGrpcConnection();
    try {
        const gateway = await newGatewayConnection(client);
        try {
            await command(gateway, args);
        } finally {
            gateway.close();
        }
    } finally {
        client.close();
    }
}

function printUsage(): void {
    console.log('Arguments: <command> [<arg1> ...]');
    console.log('Available commands:');
    console.log(`\t${Object.keys(commands).sort().join('\n\t')}`);
}

main().catch(error => {
    if (error instanceof ExpectedError) {
        console.log(error);
    } else {
        console.error('\nUnexpected application error:', error);
        process.exitCode = 1;
    }
});
