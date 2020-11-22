/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

/*
 *
 * deleteAsset.js will delete a specified asset. Example:
 *
 *    $ node deleteAsset.js asset100
 *
 *  The utility is meant to demonstrate delete block events.
 */

'use strict';

const { Wallets, Gateway } = require('fabric-network');
const fs = require('fs');
const path = require('path');

const config = require('./config.json');
const channelid = config.channelid;

async function main() {

    if (process.argv[2] == undefined) {
        console.log("Usage: node deleteAsset AssetId");
        process.exit(1);
    }

    const deletekey = process.argv[2];

    try {

        // Parse the connection profile.
        const ccpPath = path.resolve(__dirname, '..', 'test-network','organizations','peerOrganizations','org1.example.com', 'connection-org1.json');
        const ccp = JSON.parse(fs.readFileSync(ccpPath, 'utf8'));

        // Configure a wallet. This wallet must already be primed with an identity that
        // the application can use to interact with the peer node.
        const walletPath = path.resolve(__dirname, 'wallet');
        const wallet = await Wallets.newFileSystemWallet(walletPath);

        // Create a new gateway, and connect to the gateway peer node(s). The identity
        // specified must already exist in the specified wallet.
        const gateway = new Gateway();
        await gateway.connect(ccp, { wallet, identity: 'appUser', discovery: { enabled: true, asLocalhost: true } });

        // Get the network channel that the smart contract is deployed to.
        const network = await gateway.getNetwork(channelid);

        // Get the smart contract from the network channel.
        const contract = network.getContract('basic');

        await contract.submitTransaction('DeleteAsset', deletekey);
        console.log("Deleted asset: " + deletekey);

        await gateway.disconnect();

    } catch (error) {
        console.error(`Failed to submit transaction: ${error}`);
        process.exit(1);
    }

}

main();
