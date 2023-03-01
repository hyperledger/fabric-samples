/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { Gateway } from '@hyperledger/fabric-gateway';
import * as crypto from 'crypto';
import { CHAINCODE_NAME, CHANNEL_NAME } from '../config';
import { AssetCreate, AssetTransfer } from '../contract';
import { allFulfilled, differentElement, randomElement, randomInt } from '../utils';

export default async function main(gateway: Gateway): Promise<void> {
    const network = gateway.getNetwork(CHANNEL_NAME);
    const contract = network.getContract(CHAINCODE_NAME);

    const smartContract = new AssetTransfer(contract);
    const app = new TransactApp(smartContract);
    await app.run();
}

const colors = ['red', 'green', 'blue'];
const maxInitialValue = 1000;
const maxInitialSize = 10;

class TransactApp {
    readonly #smartContract: AssetTransfer;
    #batchSize = 6;

    constructor(smartContract: AssetTransfer) {
        this.#smartContract = smartContract;
    }

    async run(): Promise<void> {
        const promises = Array.from({ length: this.#batchSize }, () => this.#transact());
        await allFulfilled(promises);
    }

    async #transact(): Promise<void> {
        const asset = this.#newAsset();

        await this.#smartContract.createAsset(asset);
        console.log(`Created asset ${asset.ID}`);

        // Update randomly 1 in 2 assets to a new owner.
        if (randomInt(2) === 0) {
            const oldColor = asset.Color;
            asset.Color = differentElement(colors, oldColor);
            await this.#smartContract.updateAsset(asset);
            console.log(`Updated color of asset ${asset.ID} from ${oldColor} to ${asset.Color}`);
        }

        // Delete randomly 1 in 4 created assets.
        if (randomInt(4) === 0) {
            await this.#smartContract.deleteAsset(asset.ID);
            console.log(`Deleted asset ${asset.ID}`);
        }
    }

    #newAsset(): AssetCreate {
        return {
            ID: crypto.randomUUID().replaceAll('-', '').substring(0, 8),
            Color: randomElement(colors),
            Size: randomInt(maxInitialSize) + 1,
            AppraisedValue: randomInt(maxInitialValue) + 1,
        };
    }

    setbatchSize(batchSize: number): void {
        this.#batchSize = batchSize;
    }
}
