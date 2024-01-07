const { Wallets } = require('fabric-network');
const FabricCAServices = require('fabric-ca-client');
const path = require('path');
const { buildCAClient,  enrollAdmin, ClearWallet } = require('../../../test-application/javascript/CAUtil.js');
const { buildCCPOrg1, buildWallet } = require('../../../test-application/javascript/AppUtil.js');

const mspOrg1 = 'Org1MSP';

async function enrollAdmmin() {
	console.log('\n--> Enrolling the Org1 CA admin');
	const ccpOrg1 = buildCCPOrg1();
	const caOrg1Client = buildCAClient(FabricCAServices, ccpOrg1, 'ca.org1.example.com');

	const walletPathOrg1 = path.join(__dirname, 'wallet');
	const walletOrg1 = await buildWallet(Wallets, walletPathOrg1);

	await enrollAdmin(caOrg1Client, walletOrg1, mspOrg1);
}

async function clearWallet(){
	const walletPath = path.join(__dirname, 'wallet');
	const wallet = await buildWallet(Wallets, walletPath);

	await ClearWallet(wallet);
}

async function main() {
	cmd = "create";
	if (!(process.argv[2] === undefined)) {
		cmd = process.argv[2];
	}
	
	try {
		if(cmd=="clear"){
			await clearWallet();
		}else if(cmd=="create"){
			await enrollAdmmin();
		}else{
			throw Error(cmd + " is not defined. (create / clean)")
		}
	} catch (error) {
		console.error(`Error in admin: ${error}`);
		process.exit(1);
	}
}

main();