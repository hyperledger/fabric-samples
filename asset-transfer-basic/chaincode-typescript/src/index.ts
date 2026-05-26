/*
 * SPDX-License-Identifier: Apache-2.0
 */

import {type Contract} from 'fabric-contract-api';
import {AssetTransferContract} from './assetTransfer';

export const contracts: typeof Contract[] = [AssetTransferContract];
