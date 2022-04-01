/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

/**
 * Application that shows events when creating and updating an asset
 *   -- How to register a contract listener for chaincode events
 *   -- How to get the chaincode event name and value from the chaincode event
 *   -- How to retrieve the transaction and block information from the chaincode event
 *   -- How to register a block listener for full block events
 *   -- How to retrieve the transaction and block information from the full block event
 *   -- How to register to recieve private data associated with transactions when
 *      registering a block listener
 *   -- How to retreive the private data from the full block event
 *   -- The listener will be notified of an event at anytime. Notice that events will
 *      be posted by the listener after the application activity causing the ledger change
 *      and during other application activity unrelated to the event
 *   -- How to connect to a Gateway that will not use events when submitting transactions.
 *      This may be useful when the application does not want to wait for the peer to commit
 *      blocks and notify the application.
 *
 * To see the SDK workings, try setting the logging to be displayed on the console
 * before executing this application.
 *        export HFC_LOGGING='{"debug":"console"}'
 * See the following on how the SDK is working with the Peer's Event Services
 * https://hyperledger-fabric.readthedocs.io/en/latest/peer_event_services.html
 *
 * See the following for more details on using the Node SDK
 * https://hyperledger.github.io/fabric-sdk-node/release-2.2/module-fabric-network.html
 */

// pre-requisites:
// - fabric-sample two organization test-network setup with two peers, ordering service,
//   and 2 certificate authorities
//         ===> from directory test-network
//         ./network.sh up createChannel -ca
//
// - Use the asset-transfer-events/chaincode-javascript chaincode deployed on
//   the channel "mychannel". The following deploy command will package, install,
//   approve, and commit the javascript chaincode, all the actions it takes
//   to deploy a chaincode to a channel.
//         ===> from directory test-network
//         ./network.sh deployCC -ccn events -ccp ../asset-transfer-events/chaincode-javascript/ -ccl javascript -ccep "OR('Org1MSP.peer','Org2MSP.peer')"
//
// - Be sure that node.js is installed
//         ===> from directory asset-transfer-events/application-javascript
//         node -v
// - npm installed code dependencies
//         ===> from directory asset-transfer-events/application-javascript
//         npm install
// - to run this test application
//         ===> from directory asset-transfer-events/application-javascript
//         node app.js

// NOTE: If you see an error like these:
/*

   Error in setup: Error: DiscoveryService: mychannel error: access denied

   OR

   Failed to register user : Error: fabric-ca request register failed with errors [[ { code: 20, message: 'Authentication failure' } ]]

	*/
// Delete the /fabric-samples/asset-transfer-sbe/application-javascript/wallet directory
// and retry this application.
//
// The certificate authority must have been restarted and the saved certificates for the
// admin and application user are not valid. Deleting the wallet store will force these to be reset
// with the new certificate authority.
//

// use this to set logging, must be set before the require('fabric-network');
process.env.HFC_LOGGING = '{"debug": "./debug.log"}';

const { Gateway, Wallets } = require('fabric-network');
const EventStrategies = require('fabric-network/lib/impl/event/defaulteventhandlerstrategies');
const FabricCAServices = require('fabric-ca-client');
const path = require('path');
const { buildCAClient, registerAndEnrollUser, enrollAdmin } = require('../../test-application/javascript/CAUtil.js');
const { buildCCPOrg1, buildWallet } = require('../../test-application/javascript/AppUtil.js');

const channelName = 'mychannel';
const chaincodeName = 'events';

const org1 = 'Org1MSP';
const Org1UserId = 'appUser1';

const RED = '\x1b[31m\n';
const GREEN = '\x1b[32m\n';
const BLUE = '\x1b[34m';
const RESET = '\x1b[0m';

/**
 * Perform a sleep -- asynchronous wait
 * @param ms the time in milliseconds to sleep for
 */
function sleep(ms) {
	return new Promise((resolve) => setTimeout(resolve, ms));
}

async function initGatewayForOrg1(useCommitEvents) {
	console.log(`${GREEN}--> Fabric client user & Gateway init: Using Org1 identity to Org1 Peer${RESET}`);
	// build an in memory object with the network configuration (also known as a connection profile)
	const ccpOrg1 = buildCCPOrg1();

	// build an instance of the fabric ca services client based on
	// the information in the network configuration
	const caOrg1Client = buildCAClient(FabricCAServices, ccpOrg1, 'ca.org1.example.com');

	// setup the wallet to cache the credentials of the application user, on the app server locally
	const walletPathOrg1 = path.join(__dirname, 'wallet', 'org1');
	const walletOrg1 = await buildWallet(Wallets, walletPathOrg1);

	// in a real application this would be done on an administrative flow, and only once
	// stores admin identity in local wallet, if needed
	await enrollAdmin(caOrg1Client, walletOrg1, org1);
	// register & enroll application user with CA, which is used as client identify to make chaincode calls
	// and stores app user identity in local wallet
	// In a real application this would be done only when a new user was required to be added
	// and would be part of an administrative flow
	await registerAndEnrollUser(caOrg1Client, walletOrg1, org1, Org1UserId, 'org1.department1');

	try {
		// Create a new gateway for connecting to Org's peer node.
		const gatewayOrg1 = new Gateway();

		if (useCommitEvents) {
			await gatewayOrg1.connect(ccpOrg1, {
				wallet: walletOrg1,
				identity: Org1UserId,
				discovery: { enabled: true, asLocalhost: true }
			});
		} else {
			await gatewayOrg1.connect(ccpOrg1, {
				wallet: walletOrg1,
				identity: Org1UserId,
				discovery: { enabled: true, asLocalhost: true },
				eventHandlerOptions: EventStrategies.NONE
			});
		}


		return gatewayOrg1;
	} catch (error) {
		console.error(`Error in connecting to gateway for Org1: ${error}`);
		process.exit(1);
	}
}

function checkAsset(org, resultBuffer, color, size, owner, appraisedValue, price) {
	console.log(`${GREEN}<-- Query results from ${org}${RESET}`);

	let asset;
	if (resultBuffer) {
		asset = JSON.parse(resultBuffer.toString('utf8'));
	} else {
		console.log(`${RED}*** Failed to read asset${RESET}`);
	}
	console.log(`*** verify asset ${asset.ID}`);

	if (asset) {
		if (asset.Color === color) {
			console.log(`*** asset ${asset.ID} has color ${asset.Color}`);
		} else {
			console.log(`${RED}*** asset ${asset.ID} has color of ${asset.Color}${RESET}`);
		}
		if (asset.Size === size) {
			console.log(`*** asset ${asset.ID} has size ${asset.Size}`);
		} else {
			console.log(`${RED}*** Failed size check from ${org} - asset ${asset.ID} has size of ${asset.Size}${RESET}`);
		}
		if (asset.Owner === owner) {
			console.log(`*** asset ${asset.ID} owned by ${asset.Owner}`);
		} else {
			console.log(`${RED}*** Failed owner check from ${org} - asset ${asset.ID} owned by ${asset.Owner}${RESET}`);
		}
		if (asset.AppraisedValue === appraisedValue) {
			console.log(`*** asset ${asset.ID} has appraised value ${asset.AppraisedValue}`);
		} else {
			console.log(`${RED}*** Failed appraised value check from ${org} - asset ${asset.ID} has appraised value of ${asset.AppraisedValue}${RESET}`);
		}
		if (price) {
			if (asset.asset_properties && asset.asset_properties.Price === price) {
				console.log(`*** asset ${asset.ID} has price ${asset.asset_properties.Price}`);
			} else {
				console.log(`${RED}*** Failed price check from ${org} - asset ${asset.ID} has price of ${asset.asset_properties.Price}${RESET}`);
			}
		}
	}
}

function showTransactionData(transactionData) {
	const creator = transactionData.actions[0].header.creator;
	console.log(`    - submitted by: ${creator.mspid}-${creator.id_bytes.toString('hex')}`);
	for (const endorsement of transactionData.actions[0].payload.action.endorsements) {
		console.log(`    - endorsed by: ${endorsement.endorser.mspid}-${endorsement.endorser.id_bytes.toString('hex')}`);
	}
	const chaincode = transactionData.actions[0].payload.chaincode_proposal_payload.input.chaincode_spec;
	console.log(`    - chaincode:${chaincode.chaincode_id.name}`);
	console.log(`    - function:${chaincode.input.args[0].toString()}`);
	for (let x = 1; x < chaincode.input.args.length; x++) {
		console.log(`    - arg:${chaincode.input.args[x].toString()}`);
	}
}

async function main() {
	console.log(`${BLUE} **** START ****${RESET}`);
	try {
		let randomNumber = Math.floor(Math.random() * 1000) + 1;
		// use a random key so that we can run multiple times
		let assetKey = `item-${randomNumber}`;

		/** ******* Fabric client init: Using Org1 identity to Org1 Peer ******* */
		const gateway1Org1 = await initGatewayForOrg1(true); // transaction handling uses commit events
		const gateway2Org1 = await initGatewayForOrg1();

		try {
			//
			//  - - - - - -  C H A I N C O D E  E V E N T S
			//
			console.log(`${BLUE} **** CHAINCODE EVENTS ****${RESET}`);
			let transaction;
			let listener;
			const network1Org1 = await gateway1Org1.getNetwork(channelName);
			const contract1Org1 = network1Org1.getContract(chaincodeName);

			try {
				// first create a listener to be notified of chaincode code events
				// coming from the chaincode ID "events"
				listener = async (event) => {
					// The payload of the chaincode event is the value place there by the
					// chaincode. Notice it is a byte data and the application will have
					// to know how to deserialize.
					// In this case we know that the chaincode will always place the asset
					// being worked with as the payload for all events produced.
					const asset = JSON.parse(event.payload.toString());
					console.log(`${GREEN}<-- Contract Event Received: ${event.eventName} - ${JSON.stringify(asset)}${RESET}`);
					// show the information available with the event
					console.log(`*** Event: ${event.eventName}:${asset.ID}`);
					// notice how we have access to the transaction information that produced this chaincode event
					const eventTransaction = event.getTransactionEvent();
					console.log(`*** transaction: ${eventTransaction.transactionId} status:${eventTransaction.status}`);
					showTransactionData(eventTransaction.transactionData);
					// notice how we have access to the full block that contains this transaction
					const eventBlock = eventTransaction.getBlockEvent();
					console.log(`*** block: ${eventBlock.blockNumber.toString()}`);
				};
				// now start the client side event service and register the listener
				console.log(`${GREEN}--> Start contract event stream to peer in Org1${RESET}`);
				await contract1Org1.addContractListener(listener);
			} catch (eventError) {
				console.log(`${RED}<-- Failed: Setup contract events - ${eventError}${RESET}`);
			}

			try {
				// C R E A T E
				console.log(`${GREEN}--> Submit Transaction: CreateAsset, ${assetKey} owned by Sam${RESET}`);
				transaction = contract1Org1.createTransaction('CreateAsset');
				await transaction.submit(assetKey, 'blue', '10', 'Sam', '100');
				console.log(`${GREEN}<-- Submit CreateAsset Result: committed, asset ${assetKey}${RESET}`);
			} catch (createError) {
				console.log(`${RED}<-- Submit Failed: CreateAsset - ${createError}${RESET}`);
			}
			try {
				// R E A D
				console.log(`${GREEN}--> Evaluate: ReadAsset, - ${assetKey} should be owned by Sam${RESET}`);
				const resultBuffer = await contract1Org1.evaluateTransaction('ReadAsset', assetKey);
				checkAsset(org1, resultBuffer, 'blue', '10', 'Sam', '100');
			} catch (readError) {
				console.log(`${RED}<-- Failed: ReadAsset - ${readError}${RESET}`);
			}

			try {
				// U P D A T E
				console.log(`${GREEN}--> Submit Transaction: UpdateAsset ${assetKey} update appraised value to 200`);
				transaction = contract1Org1.createTransaction('UpdateAsset');
				await transaction.submit(assetKey, 'blue', '10', 'Sam', '200');
				console.log(`${GREEN}<-- Submit UpdateAsset Result: committed, asset ${assetKey}${RESET}`);
			} catch (updateError) {
				console.log(`${RED}<-- Failed: UpdateAsset - ${updateError}${RESET}`);
			}
			try {
				// R E A D
				console.log(`${GREEN}--> Evaluate: ReadAsset, - ${assetKey} should now have appraised value of 200${RESET}`);
				const resultBuffer = await contract1Org1.evaluateTransaction('ReadAsset', assetKey);
				checkAsset(org1, resultBuffer, 'blue', '10', 'Sam', '200');
			} catch (readError) {
				console.log(`${RED}<-- Failed: ReadAsset - ${readError}${RESET}`);
			}

			try {
				// T R A N S F E R
				console.log(`${GREEN}--> Submit Transaction: TransferAsset ${assetKey} to Mary`);
				transaction = contract1Org1.createTransaction('TransferAsset');
				await transaction.submit(assetKey, 'Mary');
				console.log(`${GREEN}<-- Submit TransferAsset Result: committed, asset ${assetKey}${RESET}`);
			} catch (transferError) {
				console.log(`${RED}<-- Failed: TransferAsset - ${transferError}${RESET}`);
			}
			try {
				// R E A D
				console.log(`${GREEN}--> Evaluate: ReadAsset, - ${assetKey} should now be owned by Mary${RESET}`);
				const resultBuffer = await contract1Org1.evaluateTransaction('ReadAsset', assetKey);
				checkAsset(org1, resultBuffer, 'blue', '10', 'Mary', '200');
			} catch (readError) {
				console.log(`${RED}<-- Failed: ReadAsset - ${readError}${RESET}`);
			}

			try {
				// D E L E T E
				console.log(`${GREEN}--> Submit Transaction: DeleteAsset ${assetKey}`);
				transaction = contract1Org1.createTransaction('DeleteAsset');
				await transaction.submit(assetKey);
				console.log(`${GREEN}<-- Submit DeleteAsset Result: committed, asset ${assetKey}${RESET}`);
			} catch (deleteError) {
				console.log(`${RED}<-- Failed: DeleteAsset - ${deleteError}${RESET}`);
				if (deleteError.toString().includes('ENDORSEMENT_POLICY_FAILURE')) {
					console.log(`${RED}Be sure that chaincode was deployed with the endorsement policy "OR('Org1MSP.peer','Org2MSP.peer')"${RESET}`);
				}
			}
			try {
				// R E A D
				console.log(`${GREEN}--> Evaluate: ReadAsset, - ${assetKey} should now be deleted${RESET}`);
				const resultBuffer = await contract1Org1.evaluateTransaction('ReadAsset', assetKey);
				checkAsset(org1, resultBuffer, 'blue', '10', 'Mary', '200');
				console.log(`${RED}<-- Failed: ReadAsset - should not have read this asset${RESET}`);
			} catch (readError) {
				console.log(`${GREEN}<-- Success: ReadAsset - ${readError}${RESET}`);
			}

			// all done with this listener
			contract1Org1.removeContractListener(listener);

			//
			//  - - - - - -  B L O C K  E V E N T S  with  P R I V A T E  D A T A
			//
			console.log(`${BLUE} **** BLOCK EVENTS with PRIVATE DATA ****${RESET}`);
			const network2Org1 = await gateway2Org1.getNetwork(channelName);
			const contract2Org1 = network2Org1.getContract(chaincodeName);

			randomNumber = Math.floor(Math.random() * 1000) + 1;
			assetKey = `item-${randomNumber}`;

			let firstBlock = true; // simple indicator to track blocks

			try {
				let listener;

				// create a block listener
				listener = async (event) => {
					if (firstBlock) {
						console.log(`${GREEN}<-- Block Event Received - block number: ${event.blockNumber.toString()}` +
							'\n### Note:' +
							'\n    This block event represents the current top block of the ledger.' +
							`\n    All block events after this one are events that represent new blocks added to the ledger${RESET}`);
						firstBlock = false;
					} else {
						console.log(`${GREEN}<-- Block Event Received - block number: ${event.blockNumber.toString()}${RESET}`);
					}
					const transEvents = event.getTransactionEvents();
					for (const transEvent of transEvents) {
						console.log(`*** transaction event: ${transEvent.transactionId}`);
						if (transEvent.privateData) {
							for (const namespace of transEvent.privateData.ns_pvt_rwset) {
								console.log(`    - private data: ${namespace.namespace}`);
								for (const collection of namespace.collection_pvt_rwset) {
									console.log(`     - collection: ${collection.collection_name}`);
									if (collection.rwset.reads) {
										for (const read of collection.rwset.reads) {
											console.log(`       - read set - ${BLUE}key:${RESET} ${read.key}  ${BLUE}value:${read.value.toString()}`);
										}
									}
									if (collection.rwset.writes) {
										for (const write of collection.rwset.writes) {
											console.log(`      - write set - ${BLUE}key:${RESET}${write.key} ${BLUE}is_delete:${RESET}${write.is_delete} ${BLUE}value:${RESET}${write.value.toString()}`);
										}
									}
								}
							}
						}
						if (transEvent.transactionData) {
							showTransactionData(transEvent.transactionData);
						}
					}
				};
				// now start the client side event service and register the listener
				console.log(`${GREEN}--> Start private data block event stream to peer in Org1${RESET}`);
				await network2Org1.addBlockListener(listener, {type: 'private'});
			} catch (eventError) {
				console.log(`${RED}<-- Failed: Setup block events - ${eventError}${RESET}`);
			}

			try {
				// C R E A T E
				console.log(`${GREEN}--> Submit Transaction: CreateAsset, ${assetKey} owned by Sam${RESET}`);
				transaction = contract2Org1.createTransaction('CreateAsset');

				// create the private data with salt and assign to the transaction
				const randomNumber = Math.floor(Math.random() * 100) + 1;
				const asset_properties = {
					object_type: 'asset_properties',
					asset_id: assetKey,
					Price: '90',
					salt: Buffer.from(randomNumber.toString()).toString('hex')
				};
				const asset_properties_string = JSON.stringify(asset_properties);
				transaction.setTransient({
					asset_properties: Buffer.from(asset_properties_string)
				});
				// With the addition of private data to the transaction
				// We must only send this to the organization that will be
				// saving the private data or we will get an endorsement policy failure
				transaction.setEndorsingOrganizations(org1);
				// endorse and commit - private data (transient data) will be
				// saved to the implicit collection on the peer
				await transaction.submit(assetKey, 'blue', '10', 'Sam', '100');
				console.log(`${GREEN}<-- Submit CreateAsset Result: committed, asset ${assetKey}${RESET}`);
			} catch (createError) {
				console.log(`${RED}<-- Failed: CreateAsset - ${createError}${RESET}`);
			}
			await sleep(5000); // need to wait for event to be committed
			try {
				// R E A D
				console.log(`${GREEN}--> Evaluate: ReadAsset, - ${assetKey} should be owned by Sam${RESET}`);
				const resultBuffer = await contract2Org1.evaluateTransaction('ReadAsset', assetKey);
				checkAsset(org1, resultBuffer, 'blue', '10', 'Sam', '100', '90');
			} catch (readError) {
				console.log(`${RED}<-- Failed: ReadAsset - ${readError}${RESET}`);
			}

			try {
				// U P D A T E
				console.log(`${GREEN}--> Submit Transaction: UpdateAsset ${assetKey} update appraised value to 200`);
				transaction = contract2Org1.createTransaction('UpdateAsset');

				// update the private data with new salt and assign to the transaction
				const randomNumber = Math.floor(Math.random() * 100) + 1;
				const asset_properties = {
					object_type: 'asset_properties',
					asset_id: assetKey,
					Price: '90',
					salt: Buffer.from(randomNumber.toString()).toString('hex')
				};
				const asset_properties_string = JSON.stringify(asset_properties);
				transaction.setTransient({
					asset_properties: Buffer.from(asset_properties_string)
				});
				transaction.setEndorsingOrganizations(org1);

				await transaction.submit(assetKey, 'blue', '10', 'Sam', '200');
				console.log(`${GREEN}<-- Submit UpdateAsset Result: committed, asset ${assetKey}${RESET}`);
			} catch (updateError) {
				console.log(`${RED}<-- Failed: UpdateAsset - ${updateError}${RESET}`);
			}
			await sleep(5000); // need to wait for event to be committed
			try {
				// R E A D
				console.log(`${GREEN}--> Evaluate: ReadAsset, - ${assetKey} should now have appraised value of 200${RESET}`);
				const resultBuffer = await contract2Org1.evaluateTransaction('ReadAsset', assetKey);
				checkAsset(org1, resultBuffer, 'blue', '10', 'Sam', '200', '90');
			} catch (readError) {
				console.log(`${RED}<-- Failed: ReadAsset - ${readError}${RESET}`);
			}

			try {
				// T R A N S F E R
				console.log(`${GREEN}--> Submit Transaction: TransferAsset ${assetKey} to Mary`);
				transaction = contract2Org1.createTransaction('TransferAsset');

				// update the private data with new salt and assign to the transaction
				const randomNumber = Math.floor(Math.random() * 100) + 1;
				const asset_properties = {
					object_type: 'asset_properties',
					asset_id: assetKey,
					Price: '180',
					salt: Buffer.from(randomNumber.toString()).toString('hex')
				};
				const asset_properties_string = JSON.stringify(asset_properties);
				transaction.setTransient({
					asset_properties: Buffer.from(asset_properties_string)
				});
				transaction.setEndorsingOrganizations(org1);

				await transaction.submit(assetKey, 'Mary');
				console.log(`${GREEN}<-- Submit TransferAsset Result: committed, asset ${assetKey}${RESET}`);
			} catch (transferError) {
				console.log(`${RED}<-- Failed: TransferAsset - ${transferError}${RESET}`);
			}
			await sleep(5000); // need to wait for event to be committed
			try {
				// R E A D
				console.log(`${GREEN}--> Evaluate: ReadAsset, - ${assetKey} should now be owned by Mary${RESET}`);
				const resultBuffer = await contract2Org1.evaluateTransaction('ReadAsset', assetKey);
				checkAsset(org1, resultBuffer, 'blue', '10', 'Mary', '200', '180');
			} catch (readError) {
				console.log(`${RED}<-- Failed: ReadAsset - ${readError}${RESET}`);
			}

			try {
				// D E L E T E
				console.log(`${GREEN}--> Submit Transaction: DeleteAsset ${assetKey}`);
				transaction = contract2Org1.createTransaction('DeleteAsset');
				await transaction.submit(assetKey);
				console.log(`${GREEN}<-- Submit DeleteAsset Result: committed, asset ${assetKey}${RESET}`);
			} catch (deleteError) {
				console.log(`${RED}<-- Failed: DeleteAsset - ${deleteError}${RESET}`);
			}
			await sleep(5000); // need to wait for event to be committed
			try {
				// R E A D
				console.log(`${GREEN}--> Evaluate: ReadAsset, - ${assetKey} should now be deleted${RESET}`);
				const resultBuffer = await contract2Org1.evaluateTransaction('ReadAsset', assetKey);
				checkAsset(org1, resultBuffer, 'blue', '10', 'Mary', '200');
				console.log(`${RED}<-- Failed: ReadAsset - should not have read this asset${RESET}`);
			} catch (readError) {
				console.log(`${GREEN}<-- Success: ReadAsset - ${readError}${RESET}`);
			}

			// all done with this listener
			network2Org1.removeBlockListener(listener);

		} catch (runError) {
			console.error(`Error in transaction: ${runError}`);
			if (runError.stack) {
				console.error(runError.stack);
			}
		}
	} catch (error) {
		console.error(`Error in setup: ${error}`);
		if (error.stack) {
			console.error(error.stack);
		}
		process.exit(1);
	}

	await sleep(5000);
	console.log(`${BLUE} **** END ****${RESET}`);
	process.exit(0);
}
main();
