/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

/**
 * Application that uses implicit private data collections, state-based endorsement,
 * and organization-based ownership and access control to keep data private and securely
 * transfer an asset with the consent of both the current owner and buyer
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
// - Use the asset-transfer-secured-agreement/chaincode-go chaincode deployed on
//   the channel "mychannel". The following deploy command will package, install,
//   approve, and commit the golang chaincode, all the actions it takes
//   to deploy a chaincode to a channel with the endorsement and private collection
//   settings.
//         ===> from directory /fabric-samples/test-network
//         ./network.sh deployCC -ccn secured -ccp ../asset-transfer-secured-agreement/chaincode-go/ -ccl go -ccep "OR('Org1MSP.peer','Org2MSP.peer')"
//
// - Be sure that node.js is installed
//         ===> from directory /fabric-samples/asset-transfer-secured-agreement/application-javascript
//         node -v
// - npm installed code dependencies
//         ===> from directory /fabric-samples/asset-transfer-secured-agreement/application-javascript
//         npm install
// - to run this test application
//         ===> from directory /fabric-samples/asset-transfer-secured-agreement/application-javascript
//         node app.js

// NOTE: If you see an error like these:
/*

   Error in setup: Error: DiscoveryService: mychannel error: access denied

   OR

   Failed to register user : Error: fabric-ca request register failed with errors [[ { code: 20, message: 'Authentication failure' } ]]

	*/
// Delete the /fabric-samples/asset-transfer-secured-agreement/application-javascript/wallet directory
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
const chaincodeName = 'secured';

const org1 = 'Org1MSP';
const org2 = 'Org2MSP';
const Org1UserId = 'appUser1';
const Org2UserId = 'appUser2';

const RED = '\x1b[31m\n';
const GREEN = '\x1b[32m\n';
const RESET = '\x1b[0m';

async function initGatewayForOrg1() {
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
	console.log(`${GREEN}--> Fabric client user & Gateway init: Using Org2 identity to Org2 Peer${RESET}`);
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

async function readPrivateAsset(assetKey, org, contract) {
	console.log(`${GREEN}--> Evaluate Transaction: GetAssetPrivateProperties, - ${assetKey} from organization ${org}${RESET}`);
	try {
		const resultBuffer = await contract.evaluateTransaction('GetAssetPrivateProperties', assetKey);
		const asset = JSON.parse(resultBuffer.toString('utf8'));
		console.log(`*** Result: GetAssetPrivateProperties, ${JSON.stringify(asset)}`);

	} catch (evalError) {
		console.log(`*** Failed evaluateTransaction readPrivateAsset: ${evalError}`);
	}
}

async function readBidPrice(assetKey, org, contract) {
	console.log(`${GREEN}--> Evaluate Transaction: GetAssetBidPrice, - ${assetKey} from organization ${org}${RESET}`);
	try {
		const resultBuffer = await contract.evaluateTransaction('GetAssetBidPrice', assetKey);
		const asset = JSON.parse(resultBuffer.toString('utf8'));
		console.log(`*** Result: GetAssetBidPrice, ${JSON.stringify(asset)}`);

	} catch (evalError) {
		console.log(`*** Failed evaluateTransaction GetAssetBidPrice: ${evalError}`);
	}
}

async function readSalePrice(assetKey, org, contract) {
	console.log(`${GREEN}--> Evaluate Transaction: GetAssetSalesPrice, - ${assetKey} from organization ${org}${RESET}`);
	try {
		const resultBuffer = await contract.evaluateTransaction('GetAssetSalesPrice', assetKey);
		const asset = JSON.parse(resultBuffer.toString('utf8'));
		console.log(`*** Result: GetAssetSalesPrice, ${JSON.stringify(asset)}`);

	} catch (evalError) {
		console.log(`*** Failed evaluateTransaction GetAssetSalesPrice: ${evalError}`);
	}
}

function checkAsset(org, resultBuffer, ownerOrg) {
	let asset;
	if (resultBuffer) {
		asset = JSON.parse(resultBuffer.toString('utf8'));
	}

	if (asset) {
		if (asset.ownerOrg === ownerOrg) {
			console.log(`*** Result from ${org} - asset ${asset.assetID} owned by ${asset.ownerOrg} DESC:${asset.publicDescription}`);
		} else {
			console.log(`${RED}*** Failed owner check from ${org} - asset ${asset.assetID} owned by ${asset.ownerOrg} DESC:${asset.publicDescription}${RESET}`);
		}
	}
}

// This is not a real function for an application, this simulates when two applications are running
// from different organizations and what they would see if they were to both query the asset
async function readAssetByBothOrgs(assetKey, ownerOrg, contractOrg1, contractOrg2) {
	console.log(`${GREEN}--> Evaluate Transactions: ReadAsset, - ${assetKey} should be owned by ${ownerOrg}${RESET}`);
	let resultBuffer;
	resultBuffer = await contractOrg1.evaluateTransaction('ReadAsset', assetKey);
	checkAsset('Org1', resultBuffer, ownerOrg);
	resultBuffer = await contractOrg2.evaluateTransaction('ReadAsset', assetKey);
	checkAsset('Org2', resultBuffer, ownerOrg);
}

// This application uses fabric-samples/test-network based setup and the companion chaincode
// For this illustration, both Org1 & Org2 client identities will be used, however
// notice they are used by two different "gateway"s to simulate two different running
// applications from two different organizations.
async function main() {
	console.log(`${GREEN} **** START ****${RESET}`);
	try {
		const randomNumber = Math.floor(Math.random() * 100) + 1;
		let assetKey;

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
				// Create an asset by organization Org1, this only requires the owning
				// organization to endorse.
				// With the gateway using discovery, we should limit the organizations used
				// to endorse. This only requires knowledge of the Organizations and not
				// the actual peers that may be active at any given time.
				const asset_properties = {
					object_type: 'asset_properties',
					color: 'blue',
					size: 35,
					salt: Buffer.from(randomNumber.toString()).toString('hex')
				};
				const asset_properties_string = JSON.stringify(asset_properties);
				console.log(`${GREEN}--> Submit Transaction: CreateAsset as Org1 - endorsed by Org1${RESET}`);
				console.log(`${asset_properties_string}`);
				transaction = contractOrg1.createTransaction('CreateAsset');
				transaction.setEndorsingOrganizations(org1);
				transaction.setTransient({
					asset_properties: Buffer.from(asset_properties_string)
				});
				assetKey = await transaction.submit( `Asset owned by ${org1} is not for sale`);
				console.log(`*** Result: committed, asset ${assetKey} is owned by Org1`);
			} catch (createError) {
				console.log(`${RED}*** Failed: CreateAsset - ${createError}${RESET}`);
			}

			// read the public details by both orgs
			await readAssetByBothOrgs(assetKey, org1, contractOrg1, contractOrg2);
			// Org1 should be able to read the private data details of this asset
			await readPrivateAsset(assetKey, org1, contractOrg1);
			// Org2 is not the owner and does not have the private details, this should fail
			await readPrivateAsset(assetKey, org2, contractOrg2);

			try {
				// This is an update to the public state and requires only the owner to endorse.
				console.log(`${GREEN}--> Submit Transaction: ChangePublicDescription ${assetKey}, as Org1 - endorse by Org1${RESET}`);
				transaction = contractOrg1.createTransaction('ChangePublicDescription');
				transaction.setEndorsingOrganizations(org1);
				await transaction.submit(assetKey, `Asset ${assetKey} owned by ${org1} is for sale`);
				console.log(`*** Result: committed, asset ${assetKey} is now for sale by Org1`);
			} catch (updateError) {
				console.log(`${RED}*** Failed: ChangePublicDescription - ${updateError}${RESET}`);
			}

			// read the public details by both orgs
			await readAssetByBothOrgs(assetKey, org1, contractOrg1, contractOrg2);

			try {
				// This is an update to the public state and requires the owner(Org1) to endorse and
				// sent by the owner org client (Org1).
				// Since the client is from Org2, which is not the owner, this will fail
				console.log(`${GREEN}--> Submit Transaction: ChangePublicDescription ${assetKey}, as Org2 - endorse by Org2${RESET}`);
				transaction = contractOrg2.createTransaction('ChangePublicDescription');
				transaction.setEndorsingOrganizations(org2);
				await transaction.submit(assetKey, `Asset ${assetKey} owned by ${org2} is NOT for sale`);
				console.log(`${RESET}*** Failed: Org2 is not the owner and this should have failed${RESET}`);
			} catch (updateError) {
				console.log(`*** Success: ChangePublicDescription has failed endorsememnt by Org2 sent by Org2 - ${updateError}`);
			}

			try {
				// This is an update to the public state and requires the owner(Org1) to endorse and
				// sent by the owner org client (Org1).
				// Since this is being sent by Org2, which is not the owner, this will fail
				console.log(`${GREEN}--> Submit Transaction: ChangePublicDescription ${assetKey}, as Org2 - endorse by Org1${RESET}`);
				transaction = contractOrg2.createTransaction('ChangePublicDescription');
				transaction.setEndorsingOrganizations(org1);
				await transaction.submit(assetKey, `Asset ${assetKey} owned by ${org2} is NOT for sale`);
				console.log(`${RESET}*** Failed: Org2 is not the owner and this should have failed${RESET}`);
			} catch (updateError) {
				console.log(`*** Success: ChangePublicDescription has failed endorsement by Org1 sent by Org2 - ${updateError}`);
			}

			// read the public details by both orgs
			await readAssetByBothOrgs(assetKey, org1, contractOrg1, contractOrg2);

			try {
				// Agree to a sell by Org1
				const asset_price = {
					asset_id: assetKey.toString(),
					price: 110,
					trade_id: randomNumber.toString()
				};
				const asset_price_string = JSON.stringify(asset_price);
				console.log(`${GREEN}--> Submit Transaction: AgreeToSell, ${assetKey} as Org1 - endorsed by Org1${RESET}`);
				transaction = contractOrg1.createTransaction('AgreeToSell');
				transaction.setEndorsingOrganizations(org1);
				transaction.setTransient({
					asset_price: Buffer.from(asset_price_string)
				});
				//call agree to sell with desired price
				await transaction.submit(assetKey);
				console.log(`*** Result: committed, Org1 has agreed to sell asset ${assetKey} for 110`);
			} catch (sellError) {
				console.log(`${RED}*** Failed: AgreeToSell - ${sellError}${RESET}`);
			}

			try {
				// check the private information about the asset from Org2
				// Org1 would have to send Org2 these details, so the hash of the
				// details may be checked by the chaincode.
				const asset_properties = {
					object_type: 'asset_properties',
					color: 'blue',
					size: 35,
					salt: Buffer.from(randomNumber.toString()).toString('hex')
				};
				const asset_properties_string = JSON.stringify(asset_properties);
				console.log(`${GREEN}--> Evalute: VerifyAssetProperties, ${assetKey} as Org2 - endorsed by Org2${RESET}`);
				console.log(`${asset_properties_string}`);
				transaction = contractOrg2.createTransaction('VerifyAssetProperties');
				transaction.setTransient({
					asset_properties: Buffer.from(asset_properties_string)
				});
				const verifyResultBuffer = await transaction.evaluate(assetKey);
				if (verifyResultBuffer) {
					const verifyResult = Boolean(verifyResultBuffer.toString());
					if (verifyResult) {
						console.log(`*** Successfully VerifyAssetProperties, private information about asset ${assetKey} has been verified by Org2`);
					} else {
						console.log(`*** Failed: VerifyAssetProperties, private information about asset ${assetKey} has not been verified by Org2`);
					}
				} else {
					console.log(`*** Failed: VerifyAssetProperties, private information about asset ${assetKey} has not been verified by Org2`);
				}
			} catch (verifyError) {
				console.log(`${RED}*** Failed: VerifyAssetProperties - ${verifyError}${RESET}`);
			}

			try {
				// Agree to a buy by Org2
				const asset_price = {
					asset_id: assetKey.toString(),
					price: 100,
					trade_id: randomNumber.toString()
				};
				const asset_price_string = JSON.stringify(asset_price);
				const asset_properties = {
					object_type: 'asset_properties',
					color: 'blue',
					size: 35,
					salt: Buffer.from(randomNumber.toString()).toString('hex')
				};
				const asset_properties_string = JSON.stringify(asset_properties);
				console.log(`${GREEN}--> Submit Transaction: AgreeToBuy, ${assetKey} as Org2 - endorsed by Org2${RESET}`);
				transaction = contractOrg2.createTransaction('AgreeToBuy');
				transaction.setEndorsingOrganizations(org2);
				transaction.setTransient({
					asset_price: Buffer.from(asset_price_string),
					asset_properties: Buffer.from(asset_properties_string)
				});
				await transaction.submit(assetKey);
				console.log(`*** Result: committed, Org2 has agreed to buy asset ${assetKey} for 100`);
			} catch (buyError) {
				console.log(`${RED}*** Failed: AgreeToBuy - ${buyError}${RESET}`);
			}

			// read the public details by both orgs
			await readAssetByBothOrgs(assetKey, org1, contractOrg1, contractOrg2);

			// Org1 should be able to read the private data details of this asset
			await readPrivateAsset(assetKey, org1, contractOrg1);
			// Org2 is not the owner and does not have the private details, this should fail
			await readPrivateAsset(assetKey, org2, contractOrg2);

			// Org1 should be able to read the sale price of this asset
			await readSalePrice(assetKey, org1, contractOrg1);
			// Org2 has not set a sale price and this should fail
			await readSalePrice(assetKey, org2, contractOrg2);

			// Org1 has not agreed to buy so this should fail
			await readBidPrice(assetKey, org1, contractOrg1);
			// Org2 should be able to see the price it has agreed
			await readBidPrice(assetKey, org2, contractOrg2);

			try {
				// Org1 will try to transfer the asset to Org2
				// This will fail due to the sell price and the bid price
				// are not the same
				const asset_price = {
					asset_id: assetKey.toString(),
					price: 110,
					trade_id: randomNumber.toString()
				};
				const asset_price_string = JSON.stringify(asset_price);

				console.log(`${GREEN}--> Submit Transaction: TransferAsset, ${assetKey} as Org1 - endorsed by Org1${RESET}`);
				transaction = contractOrg1.createTransaction('TransferAsset');
				transaction.setEndorsingOrganizations(org1);
				transaction.setTransient({
					asset_price: Buffer.from(asset_price_string)
				});
				await transaction.submit(assetKey, org2);
				console.log(`${RED}*** Failed: committed, TransferAsset should have failed for asset ${assetKey}${RESET}`);
			} catch (transferError) {
				console.log(`*** Success: TransferAsset - ${transferError}`);
			}

			try {
				// Agree to a sell by Org1
				// Org1, the seller will agree to the bid price of Org2
				const asset_price = {
					asset_id: assetKey.toString(),
					price: 100,
					trade_id: randomNumber.toString()
				};
				const asset_price_string = JSON.stringify(asset_price);
				console.log(`${GREEN}--> Submit Transaction: AgreeToSell, ${assetKey} as Org1 - endorsed by Org1${RESET}`);
				transaction = contractOrg1.createTransaction('AgreeToSell');
				transaction.setEndorsingOrganizations(org1);
				transaction.setTransient({
					asset_price: Buffer.from(asset_price_string)
				});
				await transaction.submit(assetKey);
				console.log(`*** Result: committed, Org1 has agreed to sell asset ${assetKey} for 100`);
			} catch (sellError) {
				console.log(`${RED}*** Failed: AgreeToSell - ${sellError}${RESET}`);
			}

			// read the public details by both orgs
			await readAssetByBothOrgs(assetKey, org1, contractOrg1, contractOrg2);

			// Org1 should be able to read the private data details of this asset
			await readPrivateAsset(assetKey, org1, contractOrg1);

			// Org1 should be able to read the sale price of this asset
			await readSalePrice(assetKey, org1, contractOrg1);

			// Org2 should be able to see the price it has agreed
			await readBidPrice(assetKey, org2, contractOrg2);

			try {
				// Org2 user will try to transfer the asset to Org2
				// This will fail as the owner is Org1
				const asset_price = {
					asset_id: assetKey.toString(),
					price: 100,
					trade_id: randomNumber.toString()
				};
				const asset_price_string = JSON.stringify(asset_price);

				console.log(`${GREEN}--> Submit Transaction: TransferAsset, ${assetKey} as Org2 - endorsed by Org1${RESET}`);
				transaction = contractOrg2.createTransaction('TransferAsset');
				transaction.setEndorsingOrganizations(org1, org2);
				transaction.setTransient({
					asset_price: Buffer.from(asset_price_string)
				});
				await transaction.submit(assetKey, org2);
				console.log(`${RED}*** FAILED: committed, TransferAsset - Org2 now owns the asset ${assetKey}${RESET}`);
			} catch (transferError) {
				console.log(`*** Succeded: TransferAsset - ${transferError}`);
			}

			try {
				// Org1 will transfer the asset to Org2
				// This will now complete as the sell price and the bid price are the same
				const asset_price = {
					asset_id: assetKey.toString(),
					price: 100,
					trade_id: randomNumber.toString()
				};
				const asset_price_string = JSON.stringify(asset_price);

				console.log(`${GREEN}--> Submit Transaction: TransferAsset, ${assetKey} as Org1 - endorsed by Org1${RESET}`);

				transaction = contractOrg1.createTransaction('TransferAsset');
				transaction.setEndorsingOrganizations(org1, org2);
				transaction.setTransient({
					asset_price: Buffer.from(asset_price_string)
				});
				await transaction.submit(assetKey, org2);
				console.log(`*** Results: committed, TransferAsset - Org2 now owns the asset ${assetKey}`);
			} catch (transferError) {
				console.log(`${RED}*** Failed: TransferAsset - ${transferError}${RESET}`);
			}

			// read the public details by both orgs
			await readAssetByBothOrgs(assetKey, org2, contractOrg1, contractOrg2);

			// Org2 should be able to read the private data details of this asset
			await readPrivateAsset(assetKey, org2, contractOrg2);
			// Org1 should not be able to read the private data details of this asset
			await readPrivateAsset(assetKey, org1, contractOrg1);

			try {
				// This is an update to the public state and requires only the owner to endorse.
				// Org2 wants to indicate that the items is no longer for sale
				console.log(`${GREEN}--> Submit Transaction: ChangePublicDescription ${assetKey}, as Org2 - endorse by Org2${RESET}`);
				transaction = contractOrg2.createTransaction('ChangePublicDescription');
				transaction.setEndorsingOrganizations(org2);
				await transaction.submit(assetKey, `Asset ${assetKey} owned by ${org2} is NOT for sale`);
				console.log('*** Results: committed - Org2 is now the owner and asset is not for sale');
			} catch (updateError) {
				console.log(`${RED}*** Failed: ChangePublicDescription has failed by Org2 - ${updateError}${RESET}`);
			}

			// read the public details by both orgs
			await readAssetByBothOrgs(assetKey, org2, contractOrg1, contractOrg2);
		} catch (runError) {
			console.error(`Error in transaction: ${runError}`);
			if (runError.stack) {
				console.error(runError.stack);
			}
			process.exit(1);
		} finally {
			// Disconnect from the gateway peer when all work for this client identity is complete
			console.log(`${GREEN}--> Close gateways`);
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
	console.log(`${GREEN} **** END ****${RESET}`);
}
main();
