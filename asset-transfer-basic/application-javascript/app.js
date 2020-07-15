/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

const { Gateway, Wallets } = require('fabric-network');
const path = require('path');
const fs = require('fs');
const registerUser = require('./registerUser');
const enrollAdmin = require('./enrollAdmin');

const myChannel = 'mychannel';
const myChaincodeName =  'basic';

function prettyJSONString(inputString) {
    return JSON.stringify(JSON.parse(inputString),null,2);
}

// pre-requisites:
// fabric-sample test-network setup with two peers and an ordering service,
// the companion chaincode is deployed, approved and committed on the channel mychannel
async function main() {
    try {
        // load the network configuration
        const ccpPath = path.resolve(__dirname, '..', '..', 'test-network', 'organizations', 'peerOrganizations', 'org1.example.com', 'connection-org1.json');
        const fileExists = fs.existsSync(ccpPath);
        if (!fileExists) {
            throw new Error(`no such file or directory: ${ccpPath}`);
        }
        const ccp = JSON.parse(fs.readFileSync(ccpPath, 'utf8'));

        // Create a new file system based wallet for managing identities.
        const walletPath = path.join(__dirname, 'wallet');
        const wallet = await Wallets.newFileSystemWallet(walletPath);
        console.log(`Wallet path: ${walletPath}`);


        // Steps:
        // Note: Steps 1 & 2 need to done only once in an app-server per blockchain network
        // 1. register & enroll admin user with CA, stores admin identity in local wallet
        enrollAdmin.EnrollAdminUser();

        // 2. register & enroll application user with CA, which is used as client identify to make chaincode calls, stores app user identity in local wallet
        registerUser.RegisterAppUser();

        // Check to see if app user exist in wallet.
        const identity = await wallet.get(registerUser.ApplicationUserId);
        if (!identity) {
            console.log('An identity for the user does not exist in the wallet: '+ registerUser.ApplicationUserId);
            return;
        }

        //3. Prepare to call chaincode using fabric javascript node sdk
        // Create a new gateway for connecting to our peer node.
        const gateway = new Gateway();
        await gateway.connect(ccp, { wallet, identity: registerUser.ApplicationUserId, discovery: { enabled: true, asLocalhost: true } });
        try {
            // Get the network (channel) our contract is deployed to.
            const network = await gateway.getNetwork(myChannel);

            // Get the contract from the network.
            const contract = network.getContract(myChaincodeName);

            //4. Init a set of asset data on the channel using chaincode 'InitLedger'
            console.log('Submit Transaction: InitLedger creates the initial set of assets on the ledger.');
            await contract.submitTransaction('InitLedger');

            //5. *** Some example transactions are listed below ***

            // GetAllAssets returns all the current assets on the ledger
            let result = await contract.evaluateTransaction('GetAllAssets');
            console.log('Evaluate Transaction: GetAllAssets, result: ' + prettyJSONString(result.toString()) );

            console.log('\n***********************');
            console.log('Submit Transaction: CreateAsset asset13');
            //CreateAsset creates an asset with ID asset13, color yellow, owner Tom, size 5 and appraizedValue of 1300
            await contract.submitTransaction('CreateAsset', 'asset13', 'yellow', 5, 'Tom', 1300);

            console.log('Evaluate Transaction: ReadAsset asset13');
            // ReadAsset returns an asset with given assetID
            result = await contract.evaluateTransaction('ReadAsset', 'asset13');
            console.log('  result: ' + prettyJSONString(result.toString()) );

            console.log('\n***********************');
            console.log('Evaluate Transaction: AssetExists asset1');
            // AssetExists returns 'true' if an asset with given assetID exist
            result = await contract.evaluateTransaction('AssetExists', 'asset1');
            console.log('  result: ' + prettyJSONString(result.toString()) );

            console.log('Submit Transaction: UpdateAsset asset1, new AppraisedValue : 350');
            // UpdateAsset updates an existing asset with new properties. Same args as CreateAsset
            await contract.submitTransaction('UpdateAsset', 'asset1', 'blue', 5, 'Tomoko', 350);

            console.log('Evaluate Transaction: ReadAsset asset1');
            result = await contract.evaluateTransaction('ReadAsset', 'asset1');
            console.log('  result: ' + prettyJSONString(result.toString()) );

            try {
                console.log('\nSubmit Transaction: UpdateAsset asset70');
                //Non existing asset asset70 should throw Error
                await contract.submitTransaction('UpdateAsset', 'asset70', 'blue', 5, 'Tomoko', 300);
            }
            catch (error) {
                let errMsg = 'Expected an error on UpdateAsset of non-existing Asset. ';
                console.log(errMsg + error);
            }
            console.log('\n***********************');

            console.log('Submit Transaction: TransferAsset asset1 from owner Tomoko > owner Tom');
            // TransferAsset transfers an asset with given ID to new owner Tom
            await contract.submitTransaction('TransferAsset', 'asset1', 'Tom');

            console.log('Evaluate Transaction: ReadAsset asset1');
            result = await contract.evaluateTransaction('ReadAsset', 'asset1');
            console.log('  result: ' + prettyJSONString(result.toString()) );

        } finally {
            // Disconnect from the gateway peer when all work for this client identity is complete
            gateway.disconnect();
        }
    } catch (error) {
        console.error(`Failed to evaluate transaction: ${error}`);
        process.exit(1);
    }
}


main();
