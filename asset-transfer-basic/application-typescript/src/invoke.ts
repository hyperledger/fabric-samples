/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { Gateway, Wallets } from 'fabric-network';
import * as path from 'path';
import * as fs from 'fs';

async function main() {
    try {
        // load the network configuration
        const ccpPath = path.resolve(__dirname, '..', '..', '..', 'test-network', 'organizations', 'peerOrganizations', 'org1.example.com', 'connection-org1.json');
        const fileExists = fs.existsSync(ccpPath);
        if (!fileExists) {
            throw new Error(`no such file or directory: ${ccpPath}`);
        }
        const ccp = JSON.parse(fs.readFileSync(ccpPath, 'utf8'));

        // Create a new file system based wallet for managing identities.
        const walletPath = path.join(path.resolve(__dirname, '..'), 'wallet');
        const wallet = await Wallets.newFileSystemWallet(walletPath);
        console.log(`Wallet path: ${walletPath}`);

        // Check to see if we've already enrolled the user.
        const identity = await wallet.get('appUser');
        if (!identity) {
            console.log('An identity for the user "appUser" does not exist in the wallet');
            console.log('Run the registerUser.ts application before retrying');
            return;
        }

        // Create a new gateway for connecting to our peer node.
        const gateway = new Gateway();
        await gateway.connect(ccp, { wallet, identity: 'appUser', discovery: { enabled: true, asLocalhost: true } });

        // Get the network (channel) our contract is deployed to.
        const network = await gateway.getNetwork('mychannel');

        // Get the contract from the network.
        const contract = network.getContract('basic');

        // Submit the specified transaction. (several example transactions are listed below)
        // createAsset creates an asset with ID asset1, color yellow, owner Dave, size 5 and appraizedValue of 130 requires 6 arguments.
        //      ex: ('createAsset', 'asset1', 'yellow', 'Dave', 5, 1300)
        // transferAsset transfers an asset with ID asset1 to new owner Tom - requires 2 arguments.
        //      ex: ('transferAsset', 'asset1', 'Tom')
        await contract.submitTransaction('createAsset', 'asset13', 'yellow', "5", 'Tom', "1300");
        console.log(`Transaction has been submitted`);

        // Disconnect from the gateway.
        await gateway.disconnect();

    } catch (error) {
        console.error(`Failed to submit transaction: ${error}`);
        process.exit(1);
    }
}

main();
