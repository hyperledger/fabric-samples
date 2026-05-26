/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { Gateway } from '@hyperledger/fabric-gateway';
import { CHAINCODE_NAME, CHANNEL_NAME } from '../config';
import { AssetTransfer } from '../contract';
import { assertDefined } from '../utils';

const usage = 'Arguments: <assetId> <ownerName> <ownerMspId>';

export default async function main(gateway: Gateway, args: string[]): Promise<void> {
    const assetId = assertDefined(args[0], usage);
    const newOwner = assertDefined(args[1], usage);
    const newOwnerOrg = assertDefined(args[2], usage);

    const network = gateway.getNetwork(CHANNEL_NAME);
    const contract = network.getContract(CHAINCODE_NAME);

    const smartContract = new AssetTransfer(contract);
    await smartContract.transferAsset(assetId, newOwner, newOwnerOrg);
}
