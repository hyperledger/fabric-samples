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
var path = require('path');
var fs = require('fs');

var Peer = require('fabric-client/lib/Peer.js');
var EventHub = require('fabric-client/lib/EventHub.js');
var tx_id = null;
var nonce = null;
var config = require('../config.json');
var helper = require('./helper.js');
var logger = helper.getLogger('Join-Channel');
//helper.hfc.addConfigFile(path.join(__dirname, 'network-config.json'));
var ORGS = helper.ORGS;
var allEventhubs = [];

//
//Attempt to send a request to the orderer with the sendCreateChain method
//
var joinChannel = function(channelName, peers, username, org) {
	// on process exit, always disconnect the event hub
	var closeConnections = function(isSuccess) {
		if (isSuccess) {
			logger.debug('\n============ Join Channel is SUCCESS ============\n');
		} else {
			logger.debug('\n!!!!!!!! ERROR: Join Channel FAILED !!!!!!!!\n');
		}
		logger.debug('');
		for (var key in allEventhubs) {
			var eventhub = allEventhubs[key];
			if (eventhub && eventhub.isconnected()) {
				//logger.debug('Disconnecting the event hub');
				eventhub.disconnect();
			}
		}
	};
	//logger.debug('\n============ Join Channel ============\n')
	logger.info(util.format(
		'Calling peers in organization "%s" to join the channel', org));

	var client = helper.getClientForOrg(org);
	var channel = helper.getChannelForOrg(org);
	var eventhubs = [];

	return helper.getOrgAdmin(org).then((admin) => {
		logger.info(util.format('received member object for admin of the organization "%s": ', org));
		tx_id = client.newTransactionID();
		let request = {
			txId : 	tx_id
		};

		return channel.getGenesisBlock(request);
	}).then((genesis_block) => {
		tx_id = client.newTransactionID();
		var request = {
			targets: helper.newPeers(peers, org),
			txId: tx_id,
			block: genesis_block
		};

		eventhubs = helper.newEventHubs(peers, org);
		for (let key in eventhubs) {
			let eh = eventhubs[key];
			eh.connect();
			allEventhubs.push(eh);
		}

		var eventPromises = [];
		eventhubs.forEach((eh) => {
			let txPromise = new Promise((resolve, reject) => {
				let handle = setTimeout(reject, parseInt(config.eventWaitTime));
				eh.registerBlockEvent((block) => {
					clearTimeout(handle);
					// in real-world situations, a peer may have more than one channels so
					// we must check that this block came from the channel we asked the peer to join
					if (block.data.data.length === 1) {
						// Config block must only contain one transaction
						var channel_header = block.data.data[0].payload.header.channel_header;
						if (channel_header.channel_id === channelName) {
							resolve();
						}
						else {
							reject();
						}
					}
				});
			});
			eventPromises.push(txPromise);
		});
		let sendPromise = channel.joinChannel(request);
		return Promise.all([sendPromise].concat(eventPromises));
	}, (err) => {
		logger.error('Failed to enroll user \'' + username + '\' due to error: ' +
			err.stack ? err.stack : err);
		throw new Error('Failed to enroll user \'' + username +
			'\' due to error: ' + err.stack ? err.stack : err);
	}).then((results) => {
		logger.debug(util.format('Join Channel R E S P O N S E : %j', results));
		if (results[0] && results[0][0] && results[0][0].response && results[0][0]
			.response.status == 200) {
			logger.info(util.format(
				'Successfully joined peers in organization %s to the channel \'%s\'',
				org, channelName));
			closeConnections(true);
			let response = {
				success: true,
				message: util.format(
					'Successfully joined peers in organization %s to the channel \'%s\'',
					org, channelName)
			};
			return response;
		} else {
			logger.error(' Failed to join channel');
			closeConnections();
			throw new Error('Failed to join channel');
		}
	}, (err) => {
		logger.error('Failed to join channel due to error: ' + err.stack ? err.stack :
			err);
		closeConnections();
		throw new Error('Failed to join channel due to error: ' + err.stack ? err.stack :
			err);
	});
};
exports.joinChannel = joinChannel;
