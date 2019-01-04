/**
 * Copyright 2017 IBM All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an 'AS IS' BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
var util = require('util');

var helper = require('./helper.js');
var logger = helper.getLogger('Join-Channel');

/*
 * Have an organization join a channel
 */
var joinChannel = async function(channel_name, peers, username, org_name) {
	logger.debug('\n\n============ Join Channel start ============\n')
	var error_message = null;
	var all_eventhubs = [];
	try {
		logger.info('Calling peers in organization "%s" to join the channel', org_name);

		// first setup the client for this org
		var client = await helper.getClientForOrg(org_name, username);
		logger.debug('Successfully got the fabric client for the organization "%s"', org_name);
		var channel = client.getChannel(channel_name);
		if(!channel) {
			let message = util.format('Channel %s was not defined in the connection profile', channel_name);
			logger.error(message);
			throw new Error(message);
		}

		// next step is to get the genesis_block from the orderer,
		// the starting point for the channel that we want to join
		let request = {
			txId : 	client.newTransactionID(true) //get an admin based transactionID
		};
		let genesis_block = await channel.getGenesisBlock(request);

		// tell each peer to join and wait 10 seconds
		// for the channel to be created on each peer
		var promises = [];
		promises.push(new Promise(resolve => setTimeout(resolve, 10000)));

		let join_request = {
			targets: peers, //using the peer names which only is allowed when a connection profile is loaded
			txId: client.newTransactionID(true), //get an admin based transactionID
			block: genesis_block
		};
		let join_promise = channel.joinChannel(join_request);
		promises.push(join_promise);
		let results = await Promise.all(promises);
		logger.debug(util.format('Join Channel R E S P O N S E : %j', results));

		// lets check the results of sending to the peers which is
		// last in the results array
		let peers_results = results.pop();
		// then each peer results
		for(let i in peers_results) {
			let peer_result = peers_results[i];
			if (peer_result instanceof Error) {
				error_message = util.format('Failed to join peer to the channel with error :: %s', peer_result.toString());
				logger.error(error_message);
			} else if(peer_result.response && peer_result.response.status == 200) {
				logger.info('Successfully joined peer to the channel %s',channel_name);
			} else {
				error_message = util.format('Failed to join peer to the channel %s',channel_name);
				logger.error(error_message);
			}
		}
	} catch(error) {
		logger.error('Failed to join channel due to error: ' + error.stack ? error.stack : error);
		error_message = error.toString();
	}

	// need to shutdown open event streams
	all_eventhubs.forEach((eh) => {
		eh.disconnect();
	});

	if (!error_message) {
		let message = util.format(
			'Successfully joined peers in organization %s to the channel:%s',
			org_name, channel_name);
		logger.info(message);
		// build a response to send back to the REST caller
		const response = {
			success: true,
			message: message
		};
		return response;
	} else {
		let message = util.format('Failed to join all peers to channel. cause:%s',error_message);
		logger.error(message);
		// build a response to send back to the REST caller
		const response = {
			success: false,
			message: message
		};
		return response;
	}
};
exports.joinChannel = joinChannel;
