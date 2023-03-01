/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { CommitError, Contract, StatusCode } from '@hyperledger/fabric-gateway';
import { TextDecoder } from 'util';

const RETRIES = 2;

const utf8Decoder = new TextDecoder();

export interface Asset {
    ID: string;
    Color: string;
    Size: number;
    Owner: string;
    AppraisedValue: number;
}

export type AssetCreate = Omit<Asset, 'Owner'> & Partial<Asset>;
export type AssetUpdate = Pick<Asset, 'ID'> & Partial<Omit<Asset, 'Owner'>>;

/**
 * AssetTransfer presents the smart contract in a form appropriate to the business application. Internally it uses the
 * Fabric Gateway client API to invoke transaction functions, and deals with the translation between the business
 * application and API representation of parameters and return values.
 */
export class AssetTransfer {
    readonly #contract: Contract;

    constructor(contract: Contract) {
        this.#contract = contract;
    }

    async createAsset(asset: AssetCreate): Promise<void> {
        await this.#contract.submit('CreateAsset', {
            arguments: [JSON.stringify(asset)],
        });
    }

    async getAllAssets(): Promise<Asset[]> {
        const result = await this.#contract.evaluate('GetAllAssets');
        if (result.length === 0) {
            return [];
        }

        return JSON.parse(utf8Decoder.decode(result)) as Asset[];
    }

    async readAsset(id: string): Promise<Asset> {
        const result = await this.#contract.evaluate('ReadAsset', {
            arguments: [id],
        });
        return JSON.parse(utf8Decoder.decode(result)) as Asset;
    }

    async updateAsset(asset: AssetUpdate): Promise<void> {
        await submitWithRetry(() => this.#contract.submit('UpdateAsset', {
            arguments: [JSON.stringify(asset)],
        }));
    }

    async deleteAsset(id: string): Promise<void> {
        await submitWithRetry(() => this.#contract.submit('DeleteAsset', {
            arguments: [id],
        }));
    }

    async assetExists(id: string): Promise<boolean> {
        const result = await this.#contract.evaluate('AssetExists', {
            arguments: [id],
        });
        return utf8Decoder.decode(result).toLowerCase() === 'true';
    }

    async transferAsset(id: string, newOwner: string, newOwnerOrg: string): Promise<void> {
        console.log(`transferring asset '${id}' to ${newOwner}, ${newOwnerOrg}`);
        // TODO: implement the transferAsset() function.
        // TODO: submit a 'TransferAsset' transaction, requiring [id, newOwner, newOwnerOrg] arguments.
        return Promise.reject(new Error('TODO: implement the contract.ts transferAsset() function.'));
    }
}

async function submitWithRetry<T>(submit: () => Promise<T>): Promise<T> {
    let lastError: unknown | undefined;

    for (let retryCount = 0; retryCount < RETRIES; retryCount++) {
        try {
            return await submit();
        } catch (err: unknown) {
            lastError = err;
            if (err instanceof CommitError) {
                // Transaction failed validation and did not update the ledger. Handle specific transaction validation codes.
                if (err.code === StatusCode.MVCC_READ_CONFLICT) {
                    continue; // Retry
                }
            }
            break; // Failure -- don't retry
        }
    }

    throw lastError;
}
