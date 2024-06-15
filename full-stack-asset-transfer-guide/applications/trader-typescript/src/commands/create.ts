/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { Gateway } from '@hyperledger/fabric-gateway';
import { CHAINCODE_NAME, CHANNEL_NAME } from '../config';
import { AssetTransfer } from '../contract';
import { assertDefined } from '../utils';

const usage = 'Arguments: <assetId> <ownerName> <color>';

export default async function main(gateway: Gateway, args: string[]): Promise<void> {
    const assetId = assertDefined(args[0], usage);
    const owner = assertDefined(args[1], usage);
    const color = assertDefined(args[2], usage);

    const network = gateway.getNetwork(CHANNEL_NAME);
    const contract = network.getContract(CHAINCODE_NAME);

    const smartContract = new AssetTransfer(contract);
    await smartContract.createAsset({
        ID: assetId,
        Owner: owner,
        Color: color,
        Size: 1,
        AppraisedValue: 1,
    });
}
