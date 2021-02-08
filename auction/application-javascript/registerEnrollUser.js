/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

const { Wallets } = require('fabric-network');
const FabricCAServices = require('fabric-ca-client');
const path = require('path');
const { buildCAClient, registerAndEnrollUser } = require('../../test-application/javascript/CAUtil.js');
const { buildCCPOrg1, buildCCPOrg2, buildWallet } = require('../../test-application/javascript/AppUtil.js');

const mspOrg1 = 'Org1MSP';
const mspOrg2 = 'Org2MSP';

async function connectToOrg1CA(UserID) {
	console.log('\n--> Register and enrolling new user');
	const ccpOrg1 = buildCCPOrg1();
	const caOrg1Client = buildCAClient(FabricCAServices, ccpOrg1, 'ca.org1.example.com');

	const walletPathOrg1 = path.join(__dirname, 'wallet/org1');
	const walletOrg1 = await buildWallet(Wallets, walletPathOrg1);

	await registerAndEnrollUser(caOrg1Client, walletOrg1, mspOrg1, UserID, 'org1.department1');

}

async function connectToOrg2CA(UserID) {
	console.log('\n--> Register and enrolling new user');
	const ccpOrg2 = buildCCPOrg2();
	const caOrg2Client = buildCAClient(FabricCAServices, ccpOrg2, 'ca.org2.example.com');

	const walletPathOrg2 = path.join(__dirname, 'wallet/org2');
	const walletOrg2 = await buildWallet(Wallets, walletPathOrg2);

	await registerAndEnrollUser(caOrg2Client, walletOrg2, mspOrg2, UserID, 'org2.department1');

}
async function main() {

	if (process.argv[2] === undefined && process.argv[3] === undefined) {
		console.log('Usage: node registerEnrollUser.js org userID');
		process.exit(1);
	}

	const org = process.argv[2];
	const userId = process.argv[3];

	try {

		if (org === 'Org1' || org === 'org1') {
			await connectToOrg1CA(userId);
		}
		else if (org === 'Org2' || org === 'org2') {
			await connectToOrg2CA(userId);
		} else {
			console.log('Usage: node registerEnrollUser.js org userID');
			console.log('Org must be Org1 or Org2');
		}
	} catch (error) {
		console.error(`Error in enrolling admin: ${error}`);
		process.exit(1);
	}
}

main();
