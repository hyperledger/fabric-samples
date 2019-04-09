'use strict';
/*
* Copyright IBM Corp All Rights Reserved
*
* SPDX-License-Identifier: Apache-2.0
*/
/*
 * Chaincode Invoke
 */

const Fabric_Client = require('fabric-client');
const path = require('path');
const util = require('util');
const os = require('os');
const fs = require('fs-extra');

const channel_name = "mychannel"

start();

async function start() {
	console.log('\n\n --- fabtoken.js - start');
	try {
		console.log('Setting up client side network objects');

		// create fabric client and related instances
		// starting point for all interactions with the fabric network
		const {fabric_client, channel} = createFabricClient();

		// create users from existing crypto materials
		const {admin, user1, user2} = await createUsers();

		console.log('Successfully setup client side');

		let operation = null;
		let user = null;
		const args = [];

		// if there is no argument, it will run demo by calling hardcoded token operations
		// if there are arguments, it will invoke corresponding issue, list, transfer, redeem operations
		if (process.argv.length == 2) {
			demo(fabric_client, channel, admin, user1, user2)
			return
		} else if (process.argv.length >= 4) {
			operation = process.argv[2];
			if (process.argv[3] === 'user1') {
				user = user1;
			} else if (process.argv[3] === 'user2') {
				user = user2;
			} else {
				throw new Error(util.format('Invalid username "%s". Must be user1 or user2', process.argv[3]));
			}
			for (let i = 4; i < process.argv.length; i++) {
				if (process.argv[i]) {
					console.log(' Token arg: ' + process.argv[i]);
					args.push(process.argv[i]);
				}
			}
		} else {
			throw new Error('Missing required arguments: operation, user');
		}

		console.log('\n\nStart %s token operation', operation);
		let result = null;
		switch (operation) {
			case 'issue':
				if (args.length < 2) {
					throw new Error('Missing required parameter for issue: token_type, quantity');
				}
				result = await issue(fabric_client, channel, admin, user, args);
				break;
			case 'transfer':
				if (args.length < 4) {
					throw new Error('Missing required parameters for transfer: recipient, transfer_quantity, tx_id, index');
				}
				let recipient
				if (args[0] === 'user1') {
					recipient = user1;
				} else if (args[0] === 'user2') {
					recipient = user2;
				} else {
					throw new Error(util.format('Invalid recipient "%s". Must be user1 or user2', process.argv[3]));
				}
				// shift out args[0] because recipient object is passed separately
				args.shift();
				result = await transfer(fabric_client, channel, user, recipient, args);
				break;
			case 'redeem':
				if (args.length < 3) {
					throw new Error('Missing required parameter for redeem: quantity, tx_id, index');
				}
				result = await redeem(fabric_client, channel, user, args);
				break;
			case 'list':
				result = await list(fabric_client, channel, user);
				break;
			default:
				throw new Error(' Unknown operation requested: ' + operation);
		}

		console.log('End %s token operation, returns\n %s', operation, util.inspect(result, {depth: null}));

	} catch(error) {
		console.log('Problem with fabric token ::'+ error.toString());
		process.exit(1);
	}
	console.log('\n\n --- fabtoken.js - end');
};

// demo invokes token operations using hardcoded parameters
async function demo(client, channel, admin, user1, user2) {
	await reset(client, channel, user1, user2);

	console.log('admin issues token to user1, wait 5 seconds for transaction to be committed');
	await issue(client, channel, admin, user1, ['USD', '100']);
	await sleep(5000)

	let user1_tokens = await list(client, channel, user1);
	console.log('\nuser1 has a token in USD type and 100 quantity after issue:\n%s', util.inspect(user1_tokens, {depth: null}));

	console.log('\nuser1 transfers 30 quantity of the token to user2, wait 5 seconds for transaction to be committed');
	let token_id = user1_tokens[0].id;
	await transfer(client, channel, user1, user2, ['30', token_id.tx_id, token_id.index]);
	await sleep(5000)

	user1_tokens = await list(client, channel, user1);
	console.log('\nuser1 has a token in 70 quantity after transfer:\n%s', util.inspect(user1_tokens, {depth: null}));

	let user2_tokens = await list(client, channel, user2);
	console.log('\nuser2 has a token in 30 quantity after transfer:\n%s', util.inspect(user2_tokens, {depth: null}));

	console.log('\nuser1 redeems 10 out of 70 quantity of the token');
	token_id = user1_tokens[0].id;
	await redeem(client, channel, user1, ['10', token_id.tx_id, token_id.index]);

	console.log('\nuser2 redeems entire token, wait 5 seconds for transaction to be committed');
	token_id = user2_tokens[0].id;
	await redeem(client, channel, user2, ['30', token_id.tx_id, token_id.index]);
	await sleep(5000)

	user1_tokens = await list(client, channel, user1);
	console.log('\nuser1 has a token in 60 quantity after redeem:\n%s', util.inspect(user1_tokens, {depth: null}));

	user2_tokens = await list(client, channel, user2);
	console.log('\nuser2 has no token after redeem:\n%s', util.inspect(user2_tokens, {depth: null}));

	await reset(client, channel, user1, user2);
}

// reset removes all the existing tokens on the channel to get a fresh env
async function reset(client, channel, user1, user2) {
	console.log('\nReset: remove all the tokens on the channel\n');

	let tokens = await list(client, channel, user1);
	for (const token of tokens) {
		await redeem(client, channel, user1, [token.quantity, token.id.tx_id, token.id.index]);
	}

	tokens = await list(client, channel, user2);
	for (const token of tokens) {
		await redeem(client, channel, user2, [token.quantity, token.id.tx_id, token.id.index]);
	}
}

// Issue token to the user with args [type, quantity]
// It uses "admin" to issue tokens, but other users can also issue tokens as long as they have the permission.
async function issue(client, channel, admin, user, args) {
	console.log('Start token issue with args ' + args);

	await client.setUserContext(admin, true);

	const tokenClient = client.newTokenClient(channel, 'localhost:7051');

	// build the request to issue tokens to the user
	const txId = client.newTransactionID();
	const param = {
		owner: user.getIdentity().serialize(),
		type: args[0],
		quantity: args[1]
	};
	const request = {
		params: [param],
		txId: txId,
	};

	return await tokenClient.issue(request);
}

// Transfers token from the user to the recipient with args [quantity, tx_id, index]
async function transfer(client, channel, user, recipient, args) {
	console.log('Start token transfer with args ' + args);

	await client.setUserContext(user, true);

	const tokenClient = client.newTokenClient(channel, 'localhost:7051');

	// build the request to transfer tokens to the recipient
	const txId = client.newTransactionID();
	const param1 = {
		owner: recipient.getIdentity().serialize(),
		quantity: args[0]
	};

	const request =  {
		tokenIds: [{tx_id: args[1], index: parseInt(args[2])}],
		params: [param1],
		txId: txId,
	};

	return await tokenClient.transfer(request);
}

// Redeem tokens from the user with args [quantity, tx_id, index]
async function redeem(client, channel, user, args) {
	console.log('Start token redeem with args ' + args);

	await client.setUserContext(user, true);

	const tokenClient = client.newTokenClient(channel, 'localhost:7051');

	// build the request to redeem tokens
	const txId = client.newTransactionID();
	const param = {
		quantity: args[0]
	};
	const request = {
		tokenIds: [{tx_id: args[1], index: parseInt(args[2])}],
		params: [param],
		txId: txId,
	};

	return await tokenClient.redeem(request);
}

// List tokens for the user
async function list(client, channel, user) {
	await client.setUserContext(user, true);

	const tokenClient = client.newTokenClient(channel, 'localhost:7051');

	return await tokenClient.list();
}

// Create fabric client, channel, orderer, and peer instances.
// These are needed for SDK to invoke token operations.
function createFabricClient() {
	// fabric client instance
	// starting point for all interactions with the fabric network
	const fabric_client = new Fabric_Client();

	// -- channel instance to represent the ledger
	const channel = fabric_client.newChannel(channel_name);
	console.log(' Created client side object to represent the channel');

	// -- peer instance to represent a peer on the channel
	const peer = fabric_client.newPeer('grpc://localhost:7051');
	console.log(' Created client side object to represent the peer');

	// -- orderer instance to reprsent the channel's orderer
	const orderer = fabric_client.newOrderer('grpc://localhost:7050')
	console.log(' Created client side object to represent the orderer');

	// add peer and orderer to the channel
	channel.addPeer(peer);
	channel.addOrderer(orderer);

	return {fabric_client: fabric_client, channel: channel};
}

// Create admin, user1 and user2 by loading crypto files
async function createUsers() {
	// This sample application will read user idenitity information from
	// pre-generated crypto files and create users. It will use a client object as
	// an easy way to create the user objects from known cyrpto material.

	const client = new Fabric_Client();

	// load admin
	let keyPath = path.join(__dirname, '../../basic-network/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/keystore');
	let keyPEM = Buffer.from(readAllFiles(keyPath)[0]).toString();
	let certPath = path.join(__dirname, '../../basic-network/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/signcerts');
	let certPEM = readAllFiles(certPath)[0];

	let user_opts = {
		username: 'admin',
		mspid: 'Org1MSP',
		skipPersistence: true,
		cryptoContent: {
			privateKeyPEM: keyPEM,
			signedCertPEM: certPEM
		}
	};
	const admin = await client.createUser(user_opts);

	// load user1
	keyPath = path.join(__dirname, '../../basic-network/crypto-config/peerOrganizations/org1.example.com/users/User1@org1.example.com/msp/keystore');
	keyPEM = Buffer.from(readAllFiles(keyPath)[0]).toString();
	certPath = path.join(__dirname, '../../basic-network/crypto-config/peerOrganizations/org1.example.com/users/User1@org1.example.com/msp/signcerts');
	certPEM = readAllFiles(certPath)[0];

	user_opts = {
		username: 'user1',
		mspid: 'Org1MSP',
		skipPersistence: true,
		cryptoContent: {
			privateKeyPEM: keyPEM,
			signedCertPEM: certPEM
		}
	};
	const user1 = await client.createUser(user_opts);

	// load user2
	keyPath = path.join(__dirname, '../../basic-network/crypto-config/peerOrganizations/org1.example.com/users/User2@org1.example.com/msp/keystore');
	keyPEM = Buffer.from(readAllFiles(keyPath)[0]).toString();
	certPath = path.join(__dirname, '../../basic-network/crypto-config/peerOrganizations/org1.example.com/users/User2@org1.example.com/msp/signcerts');
	certPEM = readAllFiles(certPath)[0];

	user_opts = {
		username: 'user2',
		mspid: 'Org1MSP',
		skipPersistence: true,
		cryptoContent: {
			privateKeyPEM: keyPEM,
			signedCertPEM: certPEM
		}
	};
	const user2 = await client.createUser(user_opts);

	return {admin: admin, user1: user1, user2: user2};
}

function readAllFiles(dir) {
	const files = fs.readdirSync(dir);
	const certs = [];
	files.forEach((file_name) => {
		const file_path = path.join(dir, file_name);
		const data = fs.readFileSync(file_path);
		certs.push(data);
	});
	return certs;
}

function sleep(ms) {
	return new Promise(resolve => setTimeout(resolve, ms));
}
