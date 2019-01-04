/**
 * Copyright Hitachi America, Ltd. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';
var util = require('util');
var fs = require('fs');
var path = require('path');

var helper = require('./helper.js');
var logger = helper.getLogger('update-anchor-peers');

var updateAnchorPeers = async function(channelName, configUpdatePath, username, org_name) {
	logger.debug('\n====== Updating Anchor Peers on \'' + channelName + '\' ======\n');
	var error_message = null;
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

		// read in the envelope for the channel config raw bytes
		var envelope = fs.readFileSync(path.join(__dirname, configUpdatePath));
		// extract the channel config bytes from the envelope to be signed
		var channelConfig = client.extractChannelConfig(envelope);

		//Acting as a client in the given organization provided with "orgName" param
		// sign the channel config bytes as "endorsement", this is required by
		// the orderer's channel creation policy
		// this will use the admin identity assigned to the client when the connection profile was loaded
		let signature = client.signChannelConfig(channelConfig);

		let request = {
			config: channelConfig,
			signatures: [signature],
			name: channelName,
			txId: client.newTransactionID(true) // get an admin based transactionID
		};

		var promises = [];
		let event_hubs = channel.getChannelEventHubsForOrg();
		logger.debug('found %s eventhubs for this organization %s',event_hubs.length, org_name);
		event_hubs.forEach((eh) => {
			let anchorUpdateEventPromise = new Promise((resolve, reject) => {
				logger.debug('anchorUpdateEventPromise - setting up event');
				const event_timeout = setTimeout(() => {
					let message = 'REQUEST_TIMEOUT:' + eh.getPeerAddr();
					logger.error(message);
					eh.disconnect();
				}, 60000);
				eh.registerBlockEvent((block) => {
					logger.info('The config update has been committed on peer %s',eh.getPeerAddr());
					clearTimeout(event_timeout);
					resolve();
				}, (err) => {
					clearTimeout(event_timeout);
					logger.error(err);
					reject(err);
				},
					// the default for 'unregister' is true for block listeners
					// so no real need to set here, however for 'disconnect'
					// the default is false as most event hubs are long running
					// in this use case we are using it only once
					{unregister: true, disconnect: true}
				);
				eh.connect();
			});
			promises.push(anchorUpdateEventPromise);
		});

		var sendPromise = client.updateChannel(request);
		// put the send to the orderer last so that the events get registered and
		// are ready for the orderering and committing
		promises.push(sendPromise);
		let results = await Promise.all(promises);
		logger.debug(util.format('------->>> R E S P O N S E : %j', results));
		let response = results.pop(); //  orderer results are last in the results

		if (response) {
			if (response.status === 'SUCCESS') {
				logger.info('Successfully update anchor peers to the channel %s', channelName);
			} else {
				error_message = util.format('Failed to update anchor peers to the channel %s with status: %s reason: %s', channelName, response.status, response.info);
				logger.error(error_message);
			}
		} else {
			error_message = util.format('Failed to update anchor peers to the channel %s', channelName);
			logger.error(error_message);
		}
	} catch (error) {
		logger.error('Failed to update anchor peers due to error: ' + error.stack ? error.stack :	error);
		error_message = error.toString();
	}

	if (!error_message) {
		let message = util.format(
			'Successfully update anchor peers in organization %s to the channel \'%s\'',
			org_name, channelName);
		logger.info(message);
		const response = {
			success: true,
			message: message
		};
		return response;
	} else {
		let message = util.format('Failed to update anchor peers. cause:%s',error_message);
		logger.error(message);
		const response = {
			success: false,
			message: message
		};
		return response;
	}
};

exports.updateAnchorPeers = updateAnchorPeers;
