/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { Gateway } from '@hyperledger/fabric-gateway';
import create from './create';
import deleteCommand from './delete';
import getAllAssets from './getAllAssets';
import listen from './listen';
import read from './read';
import transact from './transact';
import transfer from './transfer';

export type Command = (gateway: Gateway, args: string[]) => Promise<void>;

export const commands: Record<string, Command> = {
    create,
    delete: deleteCommand,
    getAllAssets,
    listen,
    read,
    transact,
    transfer,
};
