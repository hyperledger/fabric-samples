/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
*/

/*
 * This application has 6 basic steps:
 * 1. Select an identity from a wallet
 * 2. Connect to network gateway
 * 3. Access PaperNet network
 * 4. Construct request to query the ledger
 * 5. Evaluate transactions (queries)
 * 6. Process responses
 */

'use strict';

// Bring key classes into scope, most importantly Fabric SDK network class
const fs = require('fs');
const yaml = require('js-yaml');
const { Wallets, Gateway } = require('fabric-network');
const CommercialPaper = require('../../magnetocorp/contract/lib/paper.js');


// Main program function
async function main () {

    // A wallet stores a collection of identities for use
    const wallet = await Wallets.newFileSystemWallet('../identity/user/balaji/wallet');


    // A gateway defines the peers used to access Fabric networks
    const gateway = new Gateway();

    // Main try/catch block
    try {

        // Specify userName for network access
        const userName = 'balaji';

        // Load connection profile; will be used to locate a gateway
        let connectionProfile = yaml.safeLoad(fs.readFileSync('../gateway/connection-org1.yaml', 'utf8'));

        // Set connection options; identity and wallet
        let connectionOptions = {
            identity: userName,
            wallet: wallet,
            discovery: { enabled: true, asLocalhost: true }

        };

        // Connect to gateway using application specified parameters
        console.log('Connect to Fabric gateway.');

        await gateway.connect(connectionProfile, connectionOptions);

        // Access PaperNet network
        console.log('Use network channel: mychannel.');

        const network = await gateway.getNetwork('mychannel');

        // Get addressability to commercial paper contract
        console.log('Use org.papernet.commercialpaper smart contract.');

        const contract = await network.getContract('papercontract', 'org.papernet.commercialpaper');

        // queries - commercial paper
        console.log('-----------------------------------------------------------------------------------------');
        console.log('****** Submitting commercial paper queries ****** \n\n ');


        // 1 asset history
        console.log('1. Query Commercial Paper History....');
        console.log('-----------------------------------------------------------------------------------------\n');
        let queryResponse = await contract.evaluateTransaction('queryHistory', 'MagnetoCorp', '00001');


        let paper = CommercialPaper.fromBuffer(queryResponse);
        console.log('\n\n');

        console.log(paper);
        console.log('\n  History query complete.');
        console.log('-----------------------------------------------------------------------------------------\n\n');

        // 2 ownership query
        console.log('2. Query Commercial Paper Ownership.... Papers owned by MagnetoCorp');
        console.log('-----------------------------------------------------------------------------------------\n');
        let queryResponse2 = await contract.evaluateTransaction('queryOwner', 'MagnetoCorp');


        let paper2 = CommercialPaper.fromBuffer(queryResponse2);
        console.log('\n\n');

        console.log(paper2);
        console.log('\n  Paper Ownership query complete.');
        console.log('-----------------------------------------------------------------------------------------\n\n');

        // 3 partial key query
        console.log('3. Query Commercial Paper Partial Key.... Papers in org.papernet.papers namespace and prefixed MagnetoCorp');
        console.log('-----------------------------------------------------------------------------------------\n');
        let queryResponse3 = await contract.evaluateTransaction('queryPartial', 'MagnetoCorp');


        let paper3 = CommercialPaper.fromBuffer(queryResponse3);
        console.log('\n\n');

        console.log(paper3);
        console.log('\n  Partial Key query complete.');
        console.log('-----------------------------------------------------------------------------------------\n\n');


        // 4 Named query - all redeemed papers
        console.log('4. Named Query: ... All papers in org.papernet.papers that are in current state of redeemed');
        console.log('-----------------------------------------------------------------------------------------\n');
        let queryResponse4 = await contract.evaluateTransaction('queryNamed', 'redeemed');


        let paper4 = CommercialPaper.fromBuffer(queryResponse4);
        console.log('\n\n');

        console.log(paper4);
        console.log('\n  Named query "redeemed" complete.');
        console.log('-----------------------------------------------------------------------------------------\n\n');


        // 5 named query - by value
        console.log('5. Named Query:.... All papers in org.papernet.papers with faceValue > 4000000');
        console.log('-----------------------------------------------------------------------------------------\n');
        let queryResponse5 = await contract.evaluateTransaction('queryNamed', 'value');


        let paper5 = CommercialPaper.fromBuffer(queryResponse5);
        console.log('\n\n');

        console.log(paper5);
        console.log('\n  Named query by "value" complete.');
        console.log('-----------------------------------------------------------------------------------------\n\n');
    } catch (error) {

        console.log(`Error processing transaction. ${error}`);
        console.log(error.stack);

    } finally {

        // Disconnect from the gateway
        console.log('Disconnect from Fabric gateway.');
        gateway.disconnect();

    }
}
main().then(() => {

    console.log('Queryapp program complete.');

}).catch((e) => {

    console.log('Queryapp program exception.');
    console.log(e);
    console.log(e.stack);
    process.exit(-1);

});
