/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { type Contract } from 'fabric-contract-api';
import { AssetTransferContract } from './assetTransferLedger';

export const contracts: (typeof Contract)[] = [AssetTransferContract];
