/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

/**
 *  A test application to show basic queries operations with any of the asset-transfer-basic chaincodes
 *   -- How to submit a transaction
 *   -- How to query and check the
 *   -- How to use an HSM for users key and signing support
 **/

const { Gateway, Wallets, HsmX509Provider } = require('fabric-network');
const FabricCAServices = require('fabric-ca-client');
const path = require('path');
const fs = require('fs');
const { buildCAClient, registerAndEnrollUser, enrollAdmin,  getHSMLibPath} = require('../../test-application/javascript/CAUtil.js');
const { buildCCPOrg1, buildWallet } = require('../../test-application/javascript/AppUtil.js');

const channelName = 'mychannel';
const chaincodeName = 'basic';
const mspOrg1 = 'Org1MSP';
const walletPath = path.join(__dirname, 'wallet');

const user1 = 'appUser1';
const user2 = 'appUser2';

function prettyJSONString(inputString) {
	return JSON.stringify(JSON.parse(inputString), null, 2);
}

async function main() {
	try {
		let username = user1;

		// build an in memory object with the network configuration (also known as a connection profile)
		const ccp = buildCCPOrg1();

		// build an instance of the fabric ca services client based on
		// the information in the network configuration
		const caClient = buildCAClient(FabricCAServices, ccp, 'ca.org1.example.com');
		let hsmCaClient; // must a different ca client with a HSM crypto suite assigned when enrolling HSM user

		// setup the wallet to hold the credentials of the application user
		const wallet = await buildWallet(Wallets, walletPath);

		// in a real application this would be done on an administrative flow, and only once
		await enrollAdmin(caClient, wallet, mspOrg1);

		// users will be created using the HSM if SOFTHSM2_CONF and the HSM simulator has been initialized
		// Be sure to run the following before starting this application if you wish use the HSM simulator
		/*
			export SOFTHSM2_CONF="../../test-network/hsm/softhsm2.conf"
			softhsm2-util --init-token --slot 0 --label "ForFabric" --pin 98765432 --so-pin 1234
		*/

		/*
			NOTE: You will not be able to re run this application with the same user name when using
					the HSM simulator. The simulator has been restarted and will not have the handle
					as saved in the "wallet" store.
		*/
		if (process.env.SOFTHSM2_CONF) {
			// setup the wallet provider and the ca client to have the pkcs11 cryptosuite to work with
			// the HSM to generate keys and sign transactions
			const hsmOptions = {
				lib: getHSMLibPath(fs),
				pin: process.env.PKCS11_PIN || '98765432',
				slot: Number(process.env.PKCS11_SLOT || '0')
			};
			// build the wallet identity provider and the PKCS11 crypto suite based on the HSM options
			const hsmProvider = new HsmX509Provider(hsmOptions);
			wallet.getProviderRegistry().addProvider(hsmProvider);
			hsmCaClient = buildCAClient(FabricCAServices, ccp, 'ca.org1.example.com', hsmProvider.getCryptoSuite());
			username = user2;
		}

		// in a real application this would be done only when a new user was required to be added
		// and would be part of an administrative flow
		// note: when not using an HSM, hsmCaClient will be 'undefined'
		await registerAndEnrollUser(caClient, wallet, mspOrg1, username, 'org1.department1', hsmCaClient);

		// Create a new gateway instance for interacting with the fabric network.
		// In a real application this would be done as the backend server session is setup for
		// a user that has been verified.
		const gateway = new Gateway();

		try {
			// setup the gateway instance
			// The user will now be able to create connections to the fabric network and be able to
			// submit transactions and query. All transactions submitted by this gateway will be
			// signed by this user using the credentials stored in the wallet.
			await gateway.connect(ccp, {
				wallet,
				identity: username,
				discovery: { enabled: true, asLocalhost: true } // using asLocalhost as this gateway is using a fabric network deployed locally
			});

			// Build a network instance based on the channel where the smart contract is deployed
			const network = await gateway.getNetwork(channelName);
			// show the peers that network (channel) knows about
			// This will show all discovered peers and also peers that are in the connection profile
			console.log('\n--> Network has been discovered ' + network.getChannel().getEndorsers());

			// Get the contract from the network.
			const contract = network.getContract(chaincodeName);

			// Initialize a set of asset data on the channel using the chaincode 'InitLedger' function.
			// This type of transaction would only be run once by an application the first time it was started after it
			// deployed the first time. Any updates to the chaincode deployed later would likely not need to run
			// an "init" type function.
			console.log('\n--> Submit Transaction: InitLedger, function creates the initial set of assets on the ledger');
			await contract.submitTransaction('InitLedger');
			console.log('*** Result: committed');

			// Let's try a query type operation (function).
			// This will be sent to just one peer and the results will be shown.
			console.log('\n--> Evaluate Transaction: GetAllAssets, function returns all the current assets on the ledger');
			let result = await contract.evaluateTransaction('GetAllAssets');
			console.log(`*** Result: ${prettyJSONString(result.toString())}`);

			// Now let's try to submit a transaction.
			// This will be sent to both peers and if both peers endorse the transaction, the endorsed proposal will be sent
			// to the orderer to be committed by each of the peer's to the channel ledger.
			console.log('\n--> Submit Transaction: CreateAsset, creates new asset with ID, color, owner, size, and appraisedValue arguments');
			result = await contract.submitTransaction('CreateAsset', 'asset13', 'yellow', '5', 'Tom', '1300');
			// The "submitTransaction" returns the value generated by the chaincode. Notice how we normally do not
			// look at this value as the chaincodes are not returning a value. So for demonstration purposes we
			// have the javascript version of the chaincode return a value on the function 'CreateAsset'.
			// This value will be the same as the 'ReadAsset' results for the newly created asset.
			// The other chaincode versions could be updated to also return a value.
			// Having the chaincode return a value after after doing a create or update could avoid the application
			// from making an "evaluateTransaction" call to get information on the asset added by the chaincode
			// during the create or update.
			console.log(`*** Result committed: ${prettyJSONString(result.toString())}`);

			console.log('\n--> Evaluate Transaction: ReadAsset, function returns an asset with a given assetID');
			result = await contract.evaluateTransaction('ReadAsset', 'asset13');
			console.log(`*** Result: ${prettyJSONString(result.toString())}`);

			console.log('\n--> Evaluate Transaction: AssetExists, function returns "true" if an asset with given assetID exist');
			result = await contract.evaluateTransaction('AssetExists', 'asset1');
			console.log(`*** Result: ${prettyJSONString(result.toString())}`);

			console.log('\n--> Submit Transaction: UpdateAsset asset1, change the appraisedValue to 350');
			await contract.submitTransaction('UpdateAsset', 'asset1', 'blue', '5', 'Tomoko', '350');
			console.log('*** Result: committed');

			console.log('\n--> Evaluate Transaction: ReadAsset, function returns "asset1" attributes');
			result = await contract.evaluateTransaction('ReadAsset', 'asset1');
			console.log(`*** Result: ${prettyJSONString(result.toString())}`);

			try {
				// How about we try a transactions where the executing chaincode throws an error
				// Notice how the submitTransaction will throw an error containing the error thrown by the chaincode
				console.log('\n--> Submit Transaction: UpdateAsset asset70, asset70 does not exist and should return an error');
				await contract.submitTransaction('UpdateAsset', 'asset70', 'blue', '5', 'Tomoko', '300');
				console.log('******** FAILED to return an error');
			} catch (error) {
				console.log(`*** Successfully caught the error: \n    ${error}`);
			}

			console.log('\n--> Submit Transaction: TransferAsset asset1, transfer to new owner of Tom');
			await contract.submitTransaction('TransferAsset', 'asset1', 'Tom');
			console.log('*** Result: committed');

			console.log('\n--> Evaluate Transaction: ReadAsset, function returns "asset1" attributes');
			result = await contract.evaluateTransaction('ReadAsset', 'asset1');
			console.log(`*** Result: ${prettyJSONString(result.toString())}`);
		} catch (runError) {
			console.log('**** Error:' + runError);
			console.log(runError.stack);
		} finally {
			// Disconnect from the gateway when the application is closing
			// This will close all connections to the network
			gateway.disconnect();
		}
	} catch (error) {
		console.error(`******** FAILED to run the application: ${error}`);
	}
}

main();
