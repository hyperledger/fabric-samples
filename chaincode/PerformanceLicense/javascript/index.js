/*
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

const Performance = require('./lib/PerformanceLicense').Performance;
const License = require('./lib/PerformanceLicense').License;

module.exports.Performance = Performance;
module.exports.License = License;
module.exports.contracts = [ Performance, License ];
