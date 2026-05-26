/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

const assetTransferEvents = require('./lib/assetTransferEvents');

module.exports.AssetTransferEvents = assetTransferEvents;
module.exports.contracts = [assetTransferEvents];
