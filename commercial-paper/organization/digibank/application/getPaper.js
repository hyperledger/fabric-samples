/*
SPDX-License-Identifier: Apache-2.0
*/
/*
 * This application has 6 basic steps:
 * 1. Select an identity from a wallet
 * 2. Connect to network gateway
 * 3. Access PaperNet network
 * 4. Construct request to query a commercial paper
 * 5. Submit transaction
 * 6. Process response
 */
'use strict';
// Bring key classes into scope, most importantly Fabric SDK network class
const fs = require('fs');
const yaml = require('js-yaml');
const { FileSystemWallet, Gateway } = require('fabric-network');
const CommercialPaper = require('../contract/lib/paper.js');
// A wallet stores a collection of identities for use
const wallet = new FileSystemWallet('../identity/user/balaji/wallet');
// Main program function
async function main() {
    // A gateway defines the peers used to access Fabric networks
    const gateway = new Gateway();

    // Main try/catch block
    try {
        // Specify userName for network access
        // const userName = 'isabella.issuer@magnetocorp.com';
        const userName = 'Admin@org1.example.com';
        // Load connection profile; will be used to locate a gateway 
        let connectionProfile = yaml.safeLoad(fs.readFileSync('../gateway/networkConnection.yaml', 'utf8'));
        // Set connection options; identity and wallet
        let connectionOptions = {
            identity: userName,
            wallet: wallet,
            discovery: { enabled:false, asLocalhost: true }
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

        // get commercial paper
        console.log('Submit commercial paper getPaper transaction.');
        const getPaperResponse = await contract.evaluateTransaction('getPaper', 'MagnetoCorp', '00001');

        // process response
        console.log('Process getPaper transaction response.');
        let paperJSON = JSON.parse(getPaperResponse);
        let paper = CommercialPaper.createInstance(paperJSON.issuer, paperJSON.paperNumber, paperJSON.issueDateTime, paperJSON.maturityDateTime, paperJSON.faceValue);
        paper.setOwner(paperJSON.owner);
        paper.currentState = paperJSON.currentState;

        // let paper = CommercialPaper.fromBuffer(getPaperResponse);
        let paperState = 'Unknown';
        if(paper.isIssued()) {
            paperState = 'ISSUED';
        } else if(paper.isTrading()){
            paperState = 'TRADING';
        } else if(paper.isRedeemed()){
            paperState = 'REDEEMED';
        }

        console.log(` +--------- Paper Retrieved ---------+ `);
        console.log(` | Paper number: "${paper.paperNumber}"`);
        console.log(` | Paper is owned by: "${paper.owner}"`);
        console.log(` | Paper is currently: "${paperState}"`);
        console.log(` | Paper face value: "${paper.faceValue}"`);
        console.log(` | Paper is issued by: "${paper.issuer}"`);
        console.log(` | Paper issue on: "${paper.issueDateTime}"`);
        console.log(` | Paper matures on: "${paper.maturityDateTime}"`);
        console.log(` +-----------------------------------+ `);
        console.log('Transaction complete.');

        //console.log('Transaction complete.' + JSON.stringify(paper));
    } catch (error) {
        console.log(`Error processing transaction. ${error}`);
        console.log(error.stack);
    } finally {
        // Disconnect from the gateway
        console.log('Disconnect from Fabric gateway.')
        gateway.disconnect();
    }
}
main().then(() => {
    console.log('getPaper program complete.');
}).catch((e) => {
    console.log('getPaper program exception.');
    console.log(e);
    console.log(e.stack);
    process.exit(-1);
});