/*
 * SPDX-License-Identifier: Apache-2.0
 */

import {type Contract} from 'fabric-contract-api';
import {AssetTransferCustomContract} from './assetTransferCustom';

export const contracts: typeof Contract[] = [AssetTransferCustomContract];
