/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { type Contract } from 'fabric-contract-api';
import {AssetTransfer} from './assetTransfer';

export {AssetTransfer} from './assetTransfer';

export const contracts: typeof Contract[] = [AssetTransfer];
