/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

/**
 * A test application to show state based endorsements operations with a running
 * asset-transfer-sbe chaincode with discovery.
 *   -- How to submit a transaction
 *   -- How to query
 *   -- How to limit the organizations involved in a transaction
 *
 * To see the SDK workings, try setting the logging to show on the console before running
 *        export HFC_LOGGING='{"debug":"console"}'
 */

// pre-requisites:
// - fabric-sample two organization test-network setup with two peers, ordering service,
//   and 2 certificate authorities
//         ===> from directory /fabric-samples/test-network
//         ./network.sh up createChannel -ca
// - Use any of the asset-transfer-sbe chaincodes deployed on the channel "mychannel"
//   with the chaincode name of "sbe". The following deploy command will package,
//   install, approve, and commit the javascript chaincode, all the actions it takes
//   to deploy a chaincode to a channel.
//         ===> from directory /fabric-samples/test-network
//         ./network.sh deployCC -ccn sbe -ccp ../asset-transfer-sbe/chaincode-typescript/ -ccl typescript
// - Be sure that node.js is installed
//         ===> from directory /fabric-samples/asset-transfer-sbe/application-javascript
//         node -v
// - npm installed code dependencies
//         ===> from directory /fabric-samples/asset-transfer-sbe/application-javascript
//         npm install
// - to run this test application
//         ===> from directory /fabric-samples/asset-transfer-sbe/application-javascript
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

const { Gateway, Wallets } = require('fabric-network');
const FabricCAServices = require('fabric-ca-client');
const path = require('path');
const { buildCAClient, registerAndEnrollUser, enrollAdmin } = require('../../test-application/javascript/CAUtil.js');
const { buildCCPOrg1, buildCCPOrg2, buildWallet } = require('../../test-application/javascript/AppUtil.js');

const channelName = 'mychannel';
const chaincodeName = 'sbe';

const org1 = 'Org1MSP';
const org2 = 'Org2MSP';
const Org1UserId = 'appUser1';
const Org2UserId = 'appUser2';

async function initGatewayForOrg1() {
	console.log('\n--> Fabric client user & Gateway init: Using Org1 identity to Org1 Peer');
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
		//connect using Discovery enabled
		await gatewayOrg1.connect(ccpOrg1,
			{ wallet: walletOrg1, identity: Org1UserId, discovery: { enabled: true, asLocalhost: true } });

		return gatewayOrg1;
	} catch (error) {
		console.error(`Error in connecting to gateway for Org1: ${error}`);
		process.exit(1);
	}
}

async function initGatewayForOrg2() {
	console.log('\n--> Fabric client user & Gateway init: Using Org2 identity to Org2 Peer');
	const ccpOrg2 = buildCCPOrg2();
	const caOrg2Client = buildCAClient(FabricCAServices, ccpOrg2, 'ca.org2.example.com');

	const walletPathOrg2 = path.join(__dirname, 'wallet', 'org2');
	const walletOrg2 = await buildWallet(Wallets, walletPathOrg2);

	await enrollAdmin(caOrg2Client, walletOrg2, org2);
	await registerAndEnrollUser(caOrg2Client, walletOrg2, org2, Org2UserId, 'org2.department1');

	try {
		// Create a new gateway for connecting to Org's peer node.
		const gatewayOrg2 = new Gateway();
		await gatewayOrg2.connect(ccpOrg2,
			{ wallet: walletOrg2, identity: Org2UserId, discovery: { enabled: true, asLocalhost: true } });

		return gatewayOrg2;
	} catch (error) {
		console.error(`Error in connecting to gateway for Org2: ${error}`);
		process.exit(1);
	}
}

function checkAsset(org, assetKey, resultBuffer, value, ownerOrg) {
	let asset;
	if (resultBuffer) {
		asset = JSON.parse(resultBuffer.toString('utf8'));
	}

	if (asset && value) {
		if (asset.Value === value && asset.OwnerOrg === ownerOrg) {
			console.log(`*** Result from ${org} - asset ${asset.ID} has value of ${asset.Value} and owned by ${asset.OwnerOrg}`);
		} else {
			console.log(`*** Failed from ${org} - asset ${asset.ID} has value of ${asset.Value} and owned by ${asset.OwnerOrg}`);
		}
	} else if (!asset && value === 0 ) {
		console.log(`*** Success from ${org} - asset ${assetKey} does not exist`);
	} else {
		console.log('*** Failed - asset read failed');
	}
}

async function readAssetByBothOrgs(assetKey, value, ownerOrg, contractOrg1, contractOrg2) {
	if (value) {
		console.log(`\n--> Evaluate Transaction: ReadAsset, - ${assetKey} should have a value of ${value} and owned by ${ownerOrg}`);
	} else {
		console.log(`\n--> Evaluate Transaction: ReadAsset, - ${assetKey} should not exist`);
	}
	let resultBuffer;
	resultBuffer = await contractOrg1.evaluateTransaction('ReadAsset', assetKey);
	checkAsset('Org1', assetKey, resultBuffer, value, ownerOrg);
	resultBuffer = await contractOrg2.evaluateTransaction('ReadAsset', assetKey);
	checkAsset('Org2', assetKey, resultBuffer, value, ownerOrg);
}

// This application uses fabric-samples/test-network based setup and the companion chaincode
// For this illustration, both Org1 & Org2 client identities will be used, however
// notice they are used by two different "gateway"s to simulate two different running
// applications from two different organizations.
async function main() {
	try {
		// use a random key so that we can run multiple times
		const assetKey = `asset-${Math.floor(Math.random() * 100) + 1}`;

		/** ******* Fabric client init: Using Org1 identity to Org1 Peer ******* */
		const gatewayOrg1 = await initGatewayForOrg1();
		const networkOrg1 = await gatewayOrg1.getNetwork(channelName);
		const contractOrg1 = networkOrg1.getContract(chaincodeName);

		/** ******* Fabric client init: Using Org2 identity to Org2 Peer ******* */
		const gatewayOrg2 = await initGatewayForOrg2();
		const networkOrg2 = await gatewayOrg2.getNetwork(channelName);
		const contractOrg2 = networkOrg2.getContract(chaincodeName);

		try {
			let transaction;

			try {
				// Create an asset by organization Org1, this will require that both organization endorse.
				// The endorsement will be handled by Discovery, since the gateway was connected with discovery enabled.
				console.log(`\n--> Submit Transaction: CreateAsset, ${assetKey} as Org1 - endorsed by Org1 and Org2`);
				await contractOrg1.submitTransaction('CreateAsset', assetKey, '100', 'Tom');
				console.log('*** Result: committed, now asset will only require Org1 to endorse');
			} catch (createError) {
				console.log(`*** Failed: create - ${createError}`);
			}

			await readAssetByBothOrgs(assetKey, 100, org1, contractOrg1, contractOrg2);

			try {
				// Since the gateway is using discovery we should limit the organizations used by
				// discovery to endorse. This way we only have to know the organization and not
				// the actual peers that may be active at any given time.
				console.log(`\n--> Submit Transaction: UpdateAsset ${assetKey}, as Org1 - endorse by Org1`);
				transaction = contractOrg1.createTransaction('UpdateAsset');
				transaction.setEndorsingOrganizations(org1);
				await transaction.submit(assetKey, '200');
				console.log('*** Result: committed');
			} catch (updateError) {
				console.log(`*** Failed: update - ${updateError}`);
			}

			await readAssetByBothOrgs(assetKey, 200, org1, contractOrg1, contractOrg2);

			try {
				// Submit a transaction to make an update to the asset that has a key-level endorsement policy
				// set to only allow Org1 to make updates. The following example will not use the "setEndorsingOrganizations"
				// to limit the organizations that will do the endorsement, this means that it will be sent to all
				// organizations in the chaincode endorsement policy. When Org1 endorses, the transaction will be committed
				// if Org2 endorses or not.
				console.log(`\n--> Submit Transaction: UpdateAsset ${assetKey}, as Org1 - endorse by Org1 and Org2`);
				transaction = contractOrg1.createTransaction('UpdateAsset');
				await transaction.submit(assetKey, '300');
				console.log('*** Result: committed - because Org1 and Org2 both endorsed, while only the Org1 endorsement was required and checked');
			} catch (updateError) {
				console.log(`*** Failed: update - ${updateError}`);
			}

			await readAssetByBothOrgs(assetKey, 300, org1, contractOrg1, contractOrg2);

			try {
				// Again submit the change to both Organizations by not using "setEndorsingOrganizations". Since only
				// Org1 is required to approve, the transaction will be committed.
				console.log(`\n--> Submit Transaction: UpdateAsset ${assetKey}, as Org2 - endorse by Org1 and Org2`);
				transaction = contractOrg2.createTransaction('UpdateAsset');
				await transaction.submit(assetKey, '400');
				console.log('*** Result: committed - because Org1 was on the discovery list, Org2 did not endorse');
			} catch (updateError) {
				console.log(`*** Failed: update - ${updateError}`);
			}

			await readAssetByBothOrgs(assetKey, 400, org1, contractOrg1, contractOrg2);

			try {
				// Try to update by sending only to Org2, since the state-based-endorsement says that
				// Org1 is the only organization allowed to update, the transaction will fail.
				console.log(`\n--> Submit Transaction: UpdateAsset ${assetKey}, as Org2 - endorse by Org2`);
				transaction = contractOrg2.createTransaction('UpdateAsset');
				transaction.setEndorsingOrganizations(org2);
				await transaction.submit(assetKey, '500');
				console.log('*** Failed: committed - this should have failed to endorse and commit');
			} catch (updateError) {
				console.log(`*** Successfully caught the error: \n    ${updateError}`);
			}

			await readAssetByBothOrgs(assetKey, 400, org1, contractOrg1, contractOrg2);

			try {
				// Make a change to the state-based-endorsement policy making Org2 the owner.
				console.log(`\n--> Submit Transaction: TransferAsset ${assetKey}, as Org1 - endorse by Org1`);
				transaction = contractOrg1.createTransaction('TransferAsset');
				transaction.setEndorsingOrganizations(org1);
				await transaction.submit(assetKey, 'Henry', org2);
				console.log('*** Result: committed');
			} catch (transferError) {
				console.log(`*** Failed: transfer - ${transferError}`);
			}

			await readAssetByBothOrgs(assetKey, 400, org2, contractOrg1, contractOrg2);

			try {
				// Make sure that Org2 can now make updates, notice how the transaction has limited the
				// endorsement to only Org2.
				console.log(`\n--> Submit Transaction: UpdateAsset ${assetKey}, as Org2 - endorse by Org2`);
				transaction = contractOrg2.createTransaction('UpdateAsset');
				transaction.setEndorsingOrganizations(org2);
				await transaction.submit(assetKey, '600');
				console.log('*** Result: committed');
			} catch (updateError) {
				console.log(`*** Failed: update - ${updateError}`);
			}

			await readAssetByBothOrgs(assetKey, 600, org2, contractOrg1, contractOrg2);

			try {
				// With Org2 now the owner and the state-based-endorsement policy only allowing organization Org2
				// to make updates, a transaction only to Org1 will fail.
				console.log(`\n--> Submit Transaction: UpdateAsset ${assetKey}, as Org1 - endorse by Org1`);
				transaction = contractOrg1.createTransaction('UpdateAsset');
				transaction.setEndorsingOrganizations(org1);
				await transaction.submit(assetKey, '700');
				console.log('*** Failed: committed - this should have failed to endorse and commit');
			} catch (updateError) {
				console.log(`*** Successfully caught the error: \n    ${updateError}`);
			}

			await readAssetByBothOrgs(assetKey, 600, org2, contractOrg1, contractOrg2);

			try {
				// With Org2 the owner and the state-based-endorsement policy only allowing organization Org2
				// to make updates, a transaction to delete by Org1 will fail.
				console.log(`\n--> Submit Transaction: DeleteAsset ${assetKey}, as Org1 - endorse by Org1`);
				transaction = contractOrg1.createTransaction('DeleteAsset');
				transaction.setEndorsingOrganizations(org1);
				await transaction.submit(assetKey);
				console.log('*** Failed: committed - this should have failed to endorse and commit');
			} catch (updateError) {
				console.log(`*** Successfully caught the error: \n    ${updateError}`);
			}

			try {
				// With Org2 the owner and the state-based-endorsement policy only allowing organization Org2
				// to make updates, a transaction to delete by Org2 will succeed.
				console.log(`\n--> Submit Transaction: DeleteAsset ${assetKey}, as Org2 - endorse by Org2`);
				transaction = contractOrg2.createTransaction('DeleteAsset');
				transaction.setEndorsingOrganizations(org2);
				await transaction.submit(assetKey);
				console.log('*** Result: committed');
			} catch (deleteError) {
				console.log(`*** Failed: delete - ${deleteError}`);
			}

			// The asset should now be deleted, both orgs should not be able to read it
			try {
				await readAssetByBothOrgs(assetKey, 0, org2, contractOrg1, contractOrg2);
			} catch (readDeleteError) {
				console.log(`*** Successfully caught the error: ${readDeleteError}`);
			}

		} catch (runError) {
			console.error(`Error in transaction: ${runError}`);
			if (runError.stack) {
				console.error(runError.stack);
			}
			process.exit(1);
		} finally {
			// Disconnect from the gateway peer when all work for this client identity is complete
			gatewayOrg1.disconnect();
			gatewayOrg2.disconnect();
		}
	} catch (error) {
		console.error(`Error in setup: ${error}`);
		if (error.stack) {
			console.error(error.stack);
		}
		process.exit(1);
	}
}

main();
