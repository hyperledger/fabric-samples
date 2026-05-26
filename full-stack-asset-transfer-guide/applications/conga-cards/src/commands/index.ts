/*
 * Copyright contributors to the Hyperledgendary Full Stack Asset Transfer Guide project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { Gateway } from '@hyperledger/fabric-gateway';
import create from './create';
import deleteCommand from './delete';
import discord from './discord';
import getAllAssets from './getAllAssets';
import read from './read';
import transfer from './transfer';

export type Command = (gateway: Gateway, args: string[]) => Promise<void>;

export const commands: Record<string, Command> = {
    create,
    delete: deleteCommand,
    discord,
    getAllAssets,
    read,
    transfer,
};
