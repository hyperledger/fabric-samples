const { Wallets } = require('fabric-network');
const FabricCAServices = require('fabric-ca-client');
const path = require('path');
const { buildCAClient, registerAndEnrollUser} = require('../../../test-application/javascript/CAUtil.js');
const { buildCCPOrg1, buildWallet } = require('../../../test-application/javascript/AppUtil.js');

const mspOrg1 = 'Org1MSP';


async function connectToOrg1CA(UserID) {
	console.log('\n--> Register and enrolling new user');
	const ccpOrg1 = buildCCPOrg1();
	const caOrg1Client = buildCAClient(FabricCAServices, ccpOrg1, 'ca.org1.example.com');

	const walletPathOrg1 = path.join(__dirname, 'wallet');
	const walletOrg1 = await buildWallet(Wallets, walletPathOrg1);

	await registerAndEnrollUser(caOrg1Client, walletOrg1, mspOrg1, UserID, 'org1.department1');

}

async function main() {

	if (process.argv[2] === undefined ) {
		console.log('Usage: node registerEnrollUser.js userID');
		process.exit(1);
	}

	const userId = process.argv[2];

	try {

		await connectToOrg1CA(userId);
		
	} catch (error) {
		console.error(`Error in enrolling admin: ${error}`);
		process.exit(1);
	}
}

main();
