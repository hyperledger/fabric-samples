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
var path = require('path');
var fs = require('fs');
var util = require('util');
var hfc = require('fabric-client');
var helper = require('./helper.js');
var logger = helper.getLogger('invoke-chaincode');

var invokeChaincode = async function(peerNames, channelName, chaincodeName, fcn, args, username, org_name) {
	logger.debug(util.format('\n============ invoke transaction on channel %s ============\n', channelName));
	var error_message = null;
	var eventhubs_in_use = [];
	var tx_id_string = null;
	try {
		// first setup the client for this org
		var client = await helper.getClientForOrg(org_name, username);
		logger.debug('Successfully got the fabric client for the organization "%s"', org_name);
		var channel = client.getChannel(channelName);
		if(!channel) {
			let message = util.format('Channel %s was not defined in the connection profile', channelName);
			logger.error(message);
			throw new Error(message);
		}
		var tx_id = client.newTransactionID();
		// will need the transaction ID string for the event registration later
		tx_id_string = tx_id.getTransactionID();

		// send proposal to endorser
		var request = {
			targets: peerNames,
			chaincodeId: chaincodeName,
			fcn: fcn,
			args: args,
			chainId: channelName,
			txId: tx_id
		};

		let results = await channel.sendTransactionProposal(request);

		// the returned object has both the endorsement results
		// and the actual proposal, the proposal will be needed
		// later when we send a transaction to the orderer
		var proposalResponses = results[0];
		var proposal = results[1];

		// lets have a look at the responses to see if they are
		// all good, if good they will also include signatures
		// required to be committed
		var all_good = true;
		for (var i in proposalResponses) {
			let one_good = false;
			if (proposalResponses && proposalResponses[i].response &&
				proposalResponses[i].response.status === 200) {
				one_good = true;
				logger.info('invoke chaincode proposal was good');
			} else {
				logger.error('invoke chaincode proposal was bad');
			}
			all_good = all_good & one_good;
		}

		if (all_good) {
			logger.info(util.format(
				'Successfully sent Proposal and received ProposalResponse: Status - %s, message - "%s", metadata - "%s", endorsement signature: %s',
				proposalResponses[0].response.status, proposalResponses[0].response.message,
				proposalResponses[0].response.payload, proposalResponses[0].endorsement
				.signature));

			// tell each peer to join and wait for the event hub of each peer to tell us
			// that the channel has been created on each peer
			var promises = [];
			let event_hubs = client.getEventHubsForOrg(org_name);
			event_hubs.forEach((eh) => {
				logger.debug('invokeEventPromise - setting up event');
				let invokeEventPromise = new Promise((resolve, reject) => {
					let event_timeout = setTimeout(() => {
						let message = 'REQUEST_TIMEOUT:' + eh._ep._endpoint.addr;
						logger.error(message);
						eh.disconnect();
						reject(new Error(message));
					}, 3000);
					eh.registerTxEvent(tx_id_string, (tx, code) => {
						logger.info('The chaincode invoke chaincode transaction has been committed on peer %s',eh._ep._endpoint.addr);
						clearTimeout(event_timeout);
						eh.unregisterTxEvent(tx_id_string);

						if (code !== 'VALID') {
							let message = util.format('The invoke chaincode transaction was invalid, code:%s',code);
							logger.error(message);
							reject(new Error(message));
						} else {
							let message = 'The invoke chaincode transaction was valid.';
							logger.info(message);
							resolve(message);
						}
					}, (err) => {
						clearTimeout(event_timeout);
						eh.unregisterTxEvent(tx_id_string);
						let message = 'Problem setting up the event hub :'+ err.toString();
						logger.error(message);
						reject(new Error(message));
					});
				});
				promises.push(invokeEventPromise);
				eh.connect();
				eventhubs_in_use.push(eh);
			});

			var orderer_request = {
				txId: tx_id,
				proposalResponses: proposalResponses,
				proposal: proposal
			};
			var sendPromise = channel.sendTransaction(orderer_request);
			// put the send to the orderer last so that the events get registered and
			// are ready for the orderering and committing
			promises.push(sendPromise);
			let results = await Promise.all(promises);
			logger.debug(util.format('------->>> R E S P O N S E : %j', results));
			let response = results.pop(); //  orderer results are last in the results
			if (response.status === 'SUCCESS') {
				logger.info('Successfully sent transaction to the orderer.');
			} else {
				error_message = util.format('Failed to order the transaction. Error code: %s',response.status);
				logger.debug(error_message);
			}

			// now see what each of the event hubs reported
			for(let i in results) {
				let event_hub_result = results[i];
				let event_hub = event_hubs[i];
				logger.debug('Event results for event hub :%s',event_hub._ep._endpoint.addr);
				if(typeof event_hub_result === 'string') {
					logger.debug(event_hub_result);
				} else {
					if(!error_message) error_message = event_hub_result.toString();
					logger.debug(event_hub_result.toString());
				}
			}
		} else {
			error_message = util.format('Failed to send Proposal and receive all good ProposalResponse');
			logger.debug(error_message);
		}
	} catch (error) {
		logger.error('Failed to invoke due to error: ' + error.stack ? error.stack : error);
		error_message = error.toString();
	}

	// need to shutdown open event streams
	eventhubs_in_use.forEach((eh) => {
		eh.disconnect();
	});

	if (!error_message) {
		let message = util.format(
			'Successfully invoked the chaincode %s to the channel \'%s\' for transaction ID: %s',
			org_name, channelName, tx_id_string);
		logger.info(message);

		return tx_id_string;
	} else {
		let message = util.format('Failed to invoke chaincode. cause:%s',error_message);
		logger.error(message);
		throw new Error(message);
	}
};

exports.invokeChaincode = invokeChaincode;
