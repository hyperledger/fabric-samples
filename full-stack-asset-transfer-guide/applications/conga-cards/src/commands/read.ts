/*
 * Copyright contributors to the Hyperledgendary Full Stack Asset Transfer Guide project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { Gateway } from '@hyperledger/fabric-gateway';
import { CHAINCODE_NAME, CHANNEL_NAME } from '../config';
import { AssetTransfer } from '../contract';
import { assertDefined } from '../utils';

export default async function main(gateway: Gateway, args: string[]): Promise<void> {
    const assetId = assertDefined(args[0], 'Arguments: <assetId>');

    const network = gateway.getNetwork(CHANNEL_NAME);
    const contract = network.getContract(CHAINCODE_NAME);

    const smartContract = new AssetTransfer(contract);
    const asset = await smartContract.readAsset(assetId);

    const assetsJson = JSON.stringify(asset, undefined, 2);
    console.log(assetsJson);
}
