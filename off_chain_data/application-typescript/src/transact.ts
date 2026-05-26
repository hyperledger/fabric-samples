/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { Client } from '@grpc/grpc-js';
import { connect } from '@hyperledger/fabric-gateway';
import * as crypto from 'crypto';
import { chaincodeName, channelName, newConnectOptions } from './connect';
import { Asset, AssetTransferBasic } from './contract';
import { allFulfilled, differentElement, randomElement, randomInt } from './utils';

export async function main(client: Client): Promise<void> {
    const connectOptions = await newConnectOptions(client);
    const gateway = connect(connectOptions);

    try {
        const network = gateway.getNetwork(channelName);
        const contract = network.getContract(chaincodeName);

        const smartContract = new AssetTransferBasic(contract);
        const app = new TransactApp(smartContract);
        await app.run();
    } finally {
        gateway.close();
    }
}

const colors = ['red', 'green', 'blue'];
const owners = ['alice', 'bob', 'charlie'];
const maxInitialValue = 1000;
const maxInitialSize = 10;

class TransactApp {
    readonly #smartContract: AssetTransferBasic;
    #batchSize = 10;

    constructor(smartContract: AssetTransferBasic) {
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

        // Transfer randomly 1 in 2 assets to a new owner.
        if (randomInt(2) === 0) {
            const newOwner = differentElement(owners, asset.Owner);
            const oldOwner = await this.#smartContract.transferAsset(asset.ID, newOwner);
            console.log(`Transferred asset ${asset.ID} from ${oldOwner} to ${newOwner}`);
        }

        // Delete randomly 1 in 4 created assets.
        if (randomInt(4) === 0) {
            await this.#smartContract.deleteAsset(asset.ID);
            console.log(`Deleted asset ${asset.ID}`);
        }
    }

    #newAsset(): Asset {
        return {
            ID: crypto.randomUUID(),
            Color: randomElement(colors),
            Size: randomInt(maxInitialSize) + 1,
            Owner: randomElement(owners),
            AppraisedValue: randomInt(maxInitialValue) + 1,
        };
    }
}
