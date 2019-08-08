/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

/*
 * tranferMarble.js will transfer ownership a specified marble to a new ownder. Example:
 *
 *   $ node transferMarble.js marble102 jimmy
 *
 * The utility is meant to demonstrate update block events.
 */

'use strict';

const { FileSystemWallet, Gateway } = require('fabric-network');
const fs = require('fs');
const path = require('path');

const config = require('./config.json');
const channelid = config.channelid;

async function main() {

    if (process.argv[2] == undefined && process.argv[3] == undefined) {
        console.log("Usage: node changeMarbleOwner.js marbleId owner");
        process.exit(1);
    }

    const updatekey = process.argv[2];
    const newowner = process.argv[3];

    try {

        // Parse the connection profile. This would be the path to the file downloaded
        // from the IBM Blockchain Platform operational console.
        const ccpPath = path.resolve(__dirname, '..', 'first-network', 'connection-org1.json');
        const ccp = JSON.parse(fs.readFileSync(ccpPath, 'utf8'));

        // Configure a wallet. This wallet must already be primed with an identity that
        // the application can use to interact with the peer node.
        const walletPath = path.resolve(__dirname, 'wallet');
        const wallet = new FileSystemWallet(walletPath);

        // Create a new gateway, and connect to the gateway peer node(s). The identity
        // specified must already exist in the specified wallet.
        const gateway = new Gateway();
        await gateway.connect(ccpPath, { wallet, identity: 'user1', discovery: { enabled: true, asLocalhost: true } });

        // Get the network channel that the smart contract is deployed to.
        const network = await gateway.getNetwork(channelid);

        // Get the smart contract from the network channel.
        const contract = network.getContract('marbles');

        await contract.submitTransaction('transferMarble', updatekey, newowner);
        console.log("Transferred marble " + updatekey + " to " + newowner);

        await gateway.disconnect();

    } catch (error) {
        console.error(`Failed to submit transaction: ${error}`);
        process.exit(1);
    }

}

main();
