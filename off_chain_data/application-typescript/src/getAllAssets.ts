/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { Client } from '@grpc/grpc-js';
import { connect } from '@hyperledger/fabric-gateway';
import { chaincodeName, channelName, newConnectOptions } from './connect';
import { AssetTransferBasic } from './contract';

export async function main(client: Client): Promise<void> {
    const connectOptions = await newConnectOptions(client);
    const gateway = connect(connectOptions);

    try {
        const network = gateway.getNetwork(channelName);
        const contract = network.getContract(chaincodeName);

        const smartContract = new AssetTransferBasic(contract);
        const assets = await smartContract.getAllAssets();
        const assetsJson = JSON.stringify(assets, undefined, 2);
        assetsJson.split('\n').forEach(line => console.log(line)); // Write line-by-line to avoid truncation
    } finally {
        gateway.close();
    }
}
