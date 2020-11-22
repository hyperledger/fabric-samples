/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

/*
 *
 * addAssets.js will add random sample data to blockchain.
 *
 *    $ node addAssets.js
 *
 * addAssets will add 10 Assets by default with a starting Asset name of "Asset100".
 * Additional Assets will be added by incrementing the number at the end of the Asset name.
 *
 * The properties for adding Assets are stored in addAssets.json.  This file will be created
 * during the first execution of the utility if it does not exist.  The utility can be run
 * multiple times without changing the properties.  The nextAssetNumber will be incremented and
 * stored in the JSON file.
 *
 *    {
 *        "nextAssetNumber": 100,
 *        "numberAssetsToAdd": 10
 *    }
 *
 */

'use strict';

const { Wallets, Gateway } = require('fabric-network');
const fs = require('fs');
const path = require('path');

const addAssetsConfigFile = path.resolve(__dirname, 'addAssets.json');

const colors=[ 'blue', 'red', 'yellow', 'green', 'white', 'purple' ];
const owners=[ 'tom', 'fred', 'julie', 'james', 'janet', 'henry', 'alice', 'marie', 'sam', 'debra', 'nancy'];
const sizes=[ 10, 20, 30, 40, 50, 60, 70, 80, 90, 100 ];
const appraisedValues=[ 300, 310, 320, 330, 340, 350, 360, 370, 380, 390 ];
const docType='asset'

const config = require('./config.json');
const channelid = config.channelid;

async function main() {

    try {

        let nextAssetNumber;
        let numberAssetsToAdd;
        let addAssetsConfig;

        // check to see if there is a config json defined
        if (fs.existsSync(addAssetsConfigFile)) {
            // read file the next asset and number of assets to create
            let addAssetsConfigJSON = fs.readFileSync(addAssetsConfigFile, 'utf8');
            addAssetsConfig = JSON.parse(addAssetsConfigJSON);
            nextAssetNumber = addAssetsConfig.nextAssetNumber;
            numberAssetsToAdd = addAssetsConfig.numberAssetsToAdd;
        } else {
            nextAssetNumber = 100;
            numberAssetsToAdd = 20;
            // create a default config and save
            addAssetsConfig = new Object;
            addAssetsConfig.nextAssetNumber = nextAssetNumber;
            addAssetsConfig.numberAssetsToAdd = numberAssetsToAdd;
            fs.writeFileSync(addAssetsConfigFile, JSON.stringify(addAssetsConfig, null, 2));
        }

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

        for (var counter = nextAssetNumber; counter < nextAssetNumber + numberAssetsToAdd; counter++) {

            var randomColor = Math.floor(Math.random() * (6));
            var randomOwner = Math.floor(Math.random() * (11));
            var randomSize = Math.floor(Math.random() * (10));
            var randomValue = Math.floor(Math.random() * (9));

            // Submit the 'CreateAsset' transaction to the smart contract, and wait for it
            // to be committed to the ledger.
            await contract.submitTransaction('CreateAsset', docType+counter, colors[randomColor], ''+sizes[randomSize], owners[randomOwner],appraisedValues[randomValue]);
            console.log("Adding asset: " + docType + counter + "   owner:"  + owners[randomOwner] + "   color:" + colors[randomColor] + "   size:" + '' + sizes[randomSize] + "   appraised value:" + '' + appraisedValues[randomValue] );

        }

        await gateway.disconnect();

        addAssetsConfig.nextAssetNumber = nextAssetNumber + numberAssetsToAdd;

        fs.writeFileSync(addAssetsConfigFile, JSON.stringify(addAssetsConfig, null, 2));

    } catch (error) {
        console.error(`Failed to submit transaction: ${error}`);
        process.exit(1);
    }

}

main();
