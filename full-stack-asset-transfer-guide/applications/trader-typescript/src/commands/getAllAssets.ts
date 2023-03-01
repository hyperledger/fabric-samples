/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { Gateway } from '@hyperledger/fabric-gateway';
import { CHAINCODE_NAME, CHANNEL_NAME } from '../config';
import { AssetTransfer } from '../contract';

export default async function main(gateway: Gateway): Promise<void> {
    const network = gateway.getNetwork(CHANNEL_NAME);
    const contract = network.getContract(CHAINCODE_NAME);

    const smartContract = new AssetTransfer(contract);
    const assets = await smartContract.getAllAssets();

    const assetsJson = JSON.stringify(assets, undefined, 2);
    assetsJson.split('\n').forEach(line => console.log(line)); // Write line-by-line to avoid truncation
}
