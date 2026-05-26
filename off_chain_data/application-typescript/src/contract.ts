/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { Contract } from '@hyperledger/fabric-gateway';
import { TextDecoder } from 'util';

const utf8Decoder = new TextDecoder();

export interface Asset {
    ID: string;
    Color: string;
    Size: number;
    Owner: string;
    AppraisedValue: number;
}

export class AssetTransferBasic {
    readonly #contract: Contract;

    constructor(contract: Contract) {
        this.#contract = contract;
    }

    async createAsset(asset: Asset): Promise<void> {
        await this.#contract.submit('CreateAsset', {
            arguments: [asset.ID, asset.Color, String(asset.Size), asset.Owner, String(asset.AppraisedValue)],
        });
    }

    async transferAsset(id: string, newOwner: string): Promise<string> {
        const result = await this.#contract.submit('TransferAsset', {
            arguments: [id, newOwner],
        });
        return utf8Decoder.decode(result);
    }

    async deleteAsset(id: string): Promise<void> {
        await this.#contract.submit('DeleteAsset', {
            arguments: [id],
        });
    }

    async getAllAssets(): Promise<Asset[]> {
        const result = await this.#contract.evaluate('GetAllAssets');
        if (result.length === 0) {
            return [];
        }

        return JSON.parse(utf8Decoder.decode(result)) as Asset[];
    }
}
