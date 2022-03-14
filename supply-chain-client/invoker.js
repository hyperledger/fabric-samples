const { Gateway, Wallets } = require('fabric-network');
const FabricCAServices = require('fabric-ca-client');
const path = require('path');
const { buildCAClient, registerAndEnrollUser, enrollAdmin } = require('./CAUtil.js');
const { buildCCPOrg1, buildWallet } = require('./AppUtil.js');

const channelName = 'mychannel';
const chaincodeName = 'basic';
const mspOrg1 = 'Org1MSP';
const walletPath = path.join(__dirname, 'wallet');
const org1UserId = 'appUser';

function prettyJSONString(inputString) {
	return JSON.stringify(JSON.parse(inputString), null, 2);
}

class FabricSampleService {
	constructor(){

	}

	async init() {
		try {
			const ccp = buildCCPOrg1();

			// build an instance of the fabric ca services client based on
			// the information in the network configuration
			const caClient = buildCAClient(FabricCAServices, ccp, 'ca.org1.example.com');

			// setup the wallet to hold the credentials of the application user
			const wallet = await buildWallet(Wallets, walletPath);

			// in a real application this would be done on an administrative flow, and only once
			await enrollAdmin(caClient, wallet, mspOrg1);

			// in a real application this would be done only when a new user was required to be added
			// and would be part of an administrative flow
			await registerAndEnrollUser(caClient, wallet, mspOrg1, org1UserId, 'org1.department1');

			// Create a new gateway instance for interacting with the fabric network.
			// In a real application this would be done as the backend server session is setup for
			// a user that has been verified.
			const gateway = new Gateway();

			// setup the gateway instance
			// The user will now be able to create connections to the fabric network and be able to
			// submit transactions and query. All transactions submitted by this gateway will be
			// signed by this user using the credentials stored in the wallet.
			await gateway.connect(ccp, {
				wallet,
				identity: org1UserId,
				discovery: { enabled: true, asLocalhost: true } // using asLocalhost as this gateway is using a fabric network deployed locally
			});

			// Build a network instance based on the channel where the smart contract is deployed
			const network = await gateway.getNetwork(channelName);

			// Get the contract from the network.
			this.contract = network.getContract(chaincodeName);

			//console.log('Adding initial inventory to Ledger');
			//await this.contract.submitTransaction('InitLedger');
			//console.log('Done, applicaiton Ready!');

		} catch (error) {
			console.error(`******** FAILED to startup the FabicSampleService: ${error}`);
		}
	}

	async get_fabric(id) {
		console.log('\n--> Evaluate Transaction: ReadAsset, function returns an asset with a given assetID');
		let result = await this.contract.evaluateTransaction('ReadAsset', id);
		console.log(`*** Result: ${prettyJSONString(result.toString())}`);
		return JSON.parse(result.toString())
	}

	async get_all_fabric() {
		console.log('\n--> Evaluate Transaction: GetAllAssets, function returns all the current assets on the ledger');
		let result = await this.contract.evaluateTransaction('GetAllAssets');
		console.log(`*** Result: ${prettyJSONString(result.toString())}`);
		return JSON.parse(result.toString())
	}

	async add_fabric(fabric) {
		console.log('\n--> Submit Transaction: CreateAsset, creates new asset with ID, color, owner, size, and appraisedValue arguments');
		let result = await this.contract.submitTransaction('CreateAsset', fabric.ID, fabric.Color, fabric.Size, fabric.Owner, fabric.AppraisedValue);
		console.log('*** Result: committed');
		if (`${result}` !== '') {
			console.log(`*** Result: ${prettyJSONString(result.toString())}`);
			return JSON.parse(result.toString())
		}
		return {}

	}

	async change_owner(id, newowner) {
		console.log('\n--> Submit Transaction: TransferAsset asset1, transfer to new owner of Tom');
		let result = await this.contract.submitTransaction('TransferAsset', id, newowner);
		console.log(result.toString())
		return {}
	}
}

module.exports = FabricSampleService;