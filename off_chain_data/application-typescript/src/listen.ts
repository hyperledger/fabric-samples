/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { Client } from '@grpc/grpc-js';
import { Checkpointer, checkpointers, connect } from '@hyperledger/fabric-gateway';
import { ledger } from '@hyperledger/fabric-protos';
import { promises as fs } from 'fs';
import * as path from 'path';
import { TextDecoder } from 'util';
import { Block, NamespaceReadWriteSet, parseBlock, Transaction } from './blockParser';
import { channelName, newConnectOptions } from './connect';
import { ExpectedError } from './expectedError';

const checkpointFile = path.resolve(process.env.CHECKPOINT_FILE ?? 'checkpoint.json');
const storeFile = path.resolve(process.env.STORE_FILE ?? 'store.log');
const simulatedFailureCount = getSimulatedFailureCount();

const startBlock = BigInt(0);
const utf8Decoder = new TextDecoder();

// Typically we should ignore read/write sets that apply to system chaincode namespaces.
const systemChaincodeNames = [
    '_lifecycle',
    'cscc',
    'escc',
    'lscc',
    'qscc',
    'vscc',
];

let transactionCount = 0; // Used only to simulate failures

/**
 * Apply writes for a given transaction to off-chain data store, ideally in a single operation for fault tolerance.
 * @param data Transaction data.
 */
type StoreStrategy = (data: LedgerUpdate) => Promise<void>;

/**
 * Ledger update made by a specific transaction.
 */
interface LedgerUpdate {
    blockNumber: bigint;
    channelName: string;
    transactionId: string;
    writes: Write[];
}

/**
 * Description of a ledger write that can be applied to an off-chain data store.
 */
interface Write {
    /** Channel whose ledger is being updated. */
    channelName: string;
    /** Key name within the ledger namespace. */
    key: string;
    /** Whether the key and associated value are being deleted. */
    isDelete: boolean;
    /** Namespace within the ledger. */
    namespace: string;
    /** If `isDelete` is false, the value written to the key; otherwise ignored. */
    value: Uint8Array;
}

/**
 * Apply writes for a given transaction to off-chain data store, ideally in a single operation for fault tolerance.
 * This implementation just writes to a file.
 */
const applyWritesToOffChainStore: StoreStrategy = async (data) => {
    simulateFailureIfRequired();

    const writes = data.writes
        .map(write => Object.assign({}, write, {
            value: utf8Decoder.decode(write.value), // Convert bytes to text, purely for readability in output
        }))
        .map(write => JSON.stringify(write));

    await fs.appendFile(storeFile, writes.join('\n') + '\n');
};

export async function main(client: Client): Promise<void> {
    const connectOptions = await newConnectOptions(client);
    const gateway = connect(connectOptions);

    try {
        const network = gateway.getNetwork(channelName);
        const checkpointer = await checkpointers.file(checkpointFile);

        console.log(`Starting event listening from block ${checkpointer.getBlockNumber() ?? startBlock}`);
        console.log('Last processed transaction ID within block:', checkpointer.getTransactionId());
        if (simulatedFailureCount > 0) {
            console.log(`Simulating a write failure every ${simulatedFailureCount} transactions`);
        }

        const blocks = await network.getBlockEvents({
            checkpoint: checkpointer,
            startBlock, // Used only if there is no checkpoint block number
        });

        try {
            for await (const blockProto of blocks) {
                const blockProcessor = new BlockProcessor({
                    block: parseBlock(blockProto),
                    checkpointer,
                    store: applyWritesToOffChainStore,
                });
                await blockProcessor.process();
            }
        } finally {
            blocks.close();
        }
    } finally {
        gateway.close();
    }
}

interface BlockProcessorOptions {
    block: Block;
    checkpointer: Checkpointer;
    store: StoreStrategy;
}

class BlockProcessor {
    readonly #block: Block;
    readonly #checkpointer: Checkpointer;
    readonly #store: StoreStrategy;

    constructor(options: Readonly<BlockProcessorOptions>) {
        this.#block = options.block;
        this.#checkpointer = options.checkpointer;
        this.#store = options.store;
    }

    async process(): Promise<void> {
        console.log(`\nReceived block ${this.#block.getNumber()}`);

        const blockNumber = this.#block.getNumber();
        const validTransactions = this.#getNewTransactions()
            .filter(transaction => transaction.isValid());

        for (const transaction of validTransactions) {
            const transactionProcessor = new TransactionProcessor({
                blockNumber,
                channelName: transaction.getChannelHeader().getChannelId(),
                store: this.#store,
                transaction,
            });
            await transactionProcessor.process();

            const transactionId = transaction.getChannelHeader().getTxId();
            await this.#checkpointer.checkpointTransaction(blockNumber, transactionId);
        }

        await this.#checkpointer.checkpointBlock(this.#block.getNumber());
    }

    #getNewTransactions(): Transaction[] {
        const transactions = this.#block.getTransactions();

        const lastTransactionId = this.#checkpointer.getTransactionId();
        if (!lastTransactionId) {
            // No previously processed transactions within this block so all are new
            return transactions;
        }

        // Ignore transactions up to the last processed transaction ID
        const blockTransactionIds = transactions.map(transaction => transaction.getChannelHeader().getTxId());
        const lastProcessedIndex = blockTransactionIds.indexOf(lastTransactionId);
        if (lastProcessedIndex < 0) {
            throw new Error(`Checkpoint transaction ID ${lastTransactionId} not found in block ${this.#block.getNumber()} containing transactions: ${blockTransactionIds.join(', ')}`);
        }

        return transactions.slice(lastProcessedIndex + 1);
    }
}

interface TransactionProcessorOptions {
    blockNumber: bigint;
    channelName: string;
    store: StoreStrategy;
    transaction: Transaction;
}

class TransactionProcessor {
    readonly #blockNumber: bigint;
    readonly #channelName: string;
    readonly #id: string;
    readonly #namespaceReadWriteSets: NamespaceReadWriteSet[];
    readonly #store: StoreStrategy;

    constructor(options: Readonly<TransactionProcessorOptions>) {
        this.#blockNumber = options.blockNumber;
        this.#channelName = options.channelName;
        this.#id = options.transaction.getChannelHeader().getTxId();
        this.#namespaceReadWriteSets = options.transaction.getNamespaceReadWriteSets()
            .filter(readWriteSet => !isSystemChaincode(readWriteSet.getNamespace()));
        this.#store = options.store;
    }

    async process(): Promise<void> {
        const writes = this.#getWrites();
        if (writes.length === 0) {
            console.log(`Skipping read-only or system transaction ${this.#id}`);
            return;
        }

        console.log(`Process transaction ${this.#id}`);

        await this.#store({
            blockNumber: this.#blockNumber,
            channelName: this.#channelName,
            transactionId: this.#id,
            writes: this.#getWrites(),
        });
    }

    #getWrites(): Write[] {
        return this.#namespaceReadWriteSets.flatMap(readWriteSet => {
            const namespace = readWriteSet.getNamespace();
            return readWriteSet.getReadWriteSet().getWritesList().map(write => this.#newWrite(namespace, write));
        });
    }

    #newWrite(namespace: string, write: ledger.rwset.kvrwset.KVWrite): Write {
        return {
            channelName: this.#channelName,
            key: write.getKey(),
            isDelete: write.getIsDelete(),
            namespace,
            value: write.getValue_asU8(),
        };
    }
}

function isSystemChaincode(chaincodeName: string): boolean {
    return systemChaincodeNames.includes(chaincodeName);
}

function getSimulatedFailureCount(): number {
    const value = process.env.SIMULATED_FAILURE_COUNT ?? '0';
    const count = Math.floor(Number(value));
    if (isNaN(count) || count < 0) {
        throw new Error(`Invalid SIMULATED_FAILURE_COUNT value: ${String(value)}`);
    }

    return count;
}

function simulateFailureIfRequired(): void {
    if (simulatedFailureCount > 0 && transactionCount++ >= simulatedFailureCount) {
        transactionCount = 0;
        throw new ExpectedError('Simulated write failure');
    }
}
