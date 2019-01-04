/**
 * Copyright 2017 IBM All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
'use strict';
var util = require('util');
var helper = require('./helper.js');
var logger = helper.getLogger('install-chaincode');

var installChaincode = async function(peers, chaincodeName, chaincodePath,
	chaincodeVersion, chaincodeType, username, org_name) {
	logger.debug('\n\n============ Install chaincode on organizations ============\n');
	helper.setupChaincodeDeploy();
	let error_message = null;
	try {
		logger.info('Calling peers in organization "%s" to join the channel', org_name);

		// first setup the client for this org
		var client = await helper.getClientForOrg(org_name, username);
		logger.debug('Successfully got the fabric client for the organization "%s"', org_name);

		var request = {
			targets: peers,
			chaincodePath: chaincodePath,
			chaincodeId: chaincodeName,
			chaincodeVersion: chaincodeVersion,
			chaincodeType: chaincodeType
		};
		let results = await client.installChaincode(request);
		// the returned object has both the endorsement results
		// and the actual proposal, the proposal will be needed
		// later when we send a transaction to the orederer
		var proposalResponses = results[0];
		var proposal = results[1];

		// lets have a look at the responses to see if they are
		// all good, if good they will also include signatures
		// required to be committed
		for (const i in proposalResponses) {
			if (proposalResponses[i] instanceof Error) {
				error_message = util.format('install proposal resulted in an error :: %s', proposalResponses[i].toString());
				logger.error(error_message);
			} else if (proposalResponses[i].response && proposalResponses[i].response.status === 200) {
				logger.info('install proposal was good');
			} else {
				all_good = false;
				error_message = util.format('install proposal was bad for an unknown reason %j', proposalResponses[i]);
				logger.error(error_message);
			}
		}
	} catch(error) {
		logger.error('Failed to install due to error: ' + error.stack ? error.stack : error);
		error_message = error.toString();
	}

	if (!error_message) {
		let message = util.format('Successfully installed chaincode');
		logger.info(message);
		// build a response to send back to the REST caller
		const response = {
			success: true,
			message: message
		};
		return response;
	} else {
		let message = util.format('Failed to install due to:%s',error_message);
		logger.error(message);
		const response = {
			success: false,
			message: message
		};
		return response;
	}
};
exports.installChaincode = installChaincode;
