/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

const { Gateway, Wallets } = require('fabric-network');
const FabricCAServices = require('fabric-ca-client');

const path = require('path');
const fs = require('fs');
const caUtils = require('./caUtils');

const myChannel = 'mychannel';
const myChaincodeName = 'private';

const memberAssetCollectionName = 'assetCollection';
const org1PrivateCollectionName = 'Org1MSPPrivateCollection';
const org2PrivateCollectionName = 'Org2MSPPrivateCollection';
const mspOrg2 = 'Org2MSP';
const mspOrg1 = 'Org1MSP';
function prettyJSONString(inputString) {
    if (inputString) {
        return JSON.stringify(JSON.parse(inputString), null, 2);
    }
    else {
        return inputString;
    }
}

async function initContractFromOrg1Identity() {
    console.log('\nFabric client user & Gateway init: Using Org1 identity to Org1 Peer');
    // load the network configuration
    let ccpPath = path.resolve(__dirname, '..', '..', 'test-network', 'organizations', 'peerOrganizations', 'org1.example.com', 'connection-org1.json');
    let fileExists = fs.existsSync(ccpPath);
    if (!fileExists) {
        throw new Error(`no such file or directory: ${ccpPath}`);
    }
    let ccpOrg1 = JSON.parse(fs.readFileSync(ccpPath, 'utf8'));
    // Create a new file system based wallet for managing identities.
    const walletPathOrg1 = path.join(__dirname, 'wallet/org1');
    const walletOrg1 = await Wallets.newFileSystemWallet(walletPathOrg1);
    console.log(`Org1 wallet path: ${walletPathOrg1}`);

    // Create a new CA client for interacting with this Orgs CA.
    const caInfo = ccpOrg1.certificateAuthorities['ca.org1.example.com'];
    const caTLSCACerts = caInfo.tlsCACerts.pem;
    const caService = new FabricCAServices(caInfo.url, { trustedRoots: caTLSCACerts, verify: false }, caInfo.caName);

    // register & enroll admin user with CA, stores admin identity in local wallet
    await caUtils.EnrollOrgAdminUser(mspOrg1, walletOrg1, caService);

    // register & enroll application user with CA, which is used as client identify to make chaincode calls, stores app user identity in local wallet
    await caUtils.RegisterOrgUser(caUtils.Org1UserId, mspOrg1, walletOrg1, caService);

    try {
        // Create a new gateway for connecting to Org's peer node.
        const gatewayOrg1 = new Gateway();
        //connect using Discovery enabled
        await gatewayOrg1.connect(ccpOrg1,
            { wallet: walletOrg1, identity: caUtils.Org1UserId, discovery: { enabled: true, asLocalhost: true } });

        return gatewayOrg1;
    } catch (error) {
        console.error(`Error in connecting to gateway: ${error}`);
        process.exit(1);
    }
}

async function initContractFromOrg2Identity() {
    console.log('\nFabric client user & Gateway init: Using Org2 identity to Org2 Peer');
    // load the network configuration
    let ccpPath = path.resolve(__dirname, '..', '..', 'test-network', 'organizations', 'peerOrganizations', 'org2.example.com', 'connection-org2.json');
    let fileExists = fs.existsSync(ccpPath);
    if (!fileExists) {
        throw new Error(`no such file or directory: ${ccpPath}`);
    }
    const ccpOrg2 = JSON.parse(fs.readFileSync(ccpPath, 'utf8'));

    // Create a new file system based wallet for managing identities.
    const walletPathOrg2 = path.join(__dirname, 'wallet/org2');
    const walletOrg2 = await Wallets.newFileSystemWallet(walletPathOrg2);
    console.log(`Org2 wallet path: ${walletPathOrg2}`);

    // Create a new CA client for interacting with this Orgs CA.
    const caInfo = ccpOrg2.certificateAuthorities['ca.org2.example.com'];
    const caTLSCACerts = caInfo.tlsCACerts.pem;
    const caService = new FabricCAServices(caInfo.url, { trustedRoots: caTLSCACerts, verify: false }, caInfo.caName);

    await caUtils.EnrollOrgAdminUser(mspOrg2, walletOrg2, caService);

    // register & enroll application user with CA, which is used as client identify to make chaincode calls, stores app user identity in local wallet
    await caUtils.RegisterOrgUser(caUtils.Org2UserId, mspOrg2, walletOrg2, caService);

    try {
        // Create a new gateway for connecting to Org's peer node.
        const gatewayOrg2 = new Gateway();
        await gatewayOrg2.connect(ccpOrg2,
            { wallet: walletOrg2, identity: caUtils.Org2UserId, discovery: { enabled: true, asLocalhost: true } });

        return gatewayOrg2;
    } catch (error) {
        console.error(`Error in connecting to gateway: ${error}`);
        process.exit(1);
    }
}

// Main workflow : usecase details at asset-transfer-private-data/chaincode-go/README.md
// This app uses fabric-samples/test-network based setup and the companion chaincode
// For this usecase illustration, we will use both Org1 & Org2 client identity from this same app
// In real world the Org1 & Org2 identity will be used in different apps to achieve asset transfer.
async function main() {
    try {

        /** ******* Fabric client init: Using Org1 identity to Org1 Peer ********** */
        const gatewayOrg1 = await initContractFromOrg1Identity();
        const networkOrg1 = await gatewayOrg1.getNetwork(myChannel);
        const contractOrg1 = networkOrg1.getContract(myChaincodeName);
        // Since this sample chaincode uses, Private Data Collection level endorsement policy, addDiscoveryInterest
        // scopes the discovery service further to use the endorsement policies of collections, if any
        contractOrg1.addDiscoveryInterest({ name: myChaincodeName, collectionNames: [memberAssetCollectionName, org1PrivateCollectionName] });

        /** ~~~~~~~ Fabric client init: Using Org2 identity to Org2 Peer ~~~~~~~ */
        const gatewayOrg2 = await initContractFromOrg2Identity();
        const networkOrg2 = await gatewayOrg2.getNetwork(myChannel);
        const contractOrg2 = networkOrg2.getContract(myChaincodeName);
        contractOrg2.addDiscoveryInterest({ name: myChaincodeName, collectionNames: [memberAssetCollectionName, org2PrivateCollectionName] });
        try {
            // Sample transactions are listed below
            // Add few sample Assets & transfers one of the asset from Org1 to Org2 as the new owner
            let assetID1 = 'asset1';
            let assetID2 = 'asset2';
            const assetType = 'ValuableAsset';
            let result;
            let asset1Data = { objectType: assetType, assetID: assetID1, color: 'green', size: 20, appraisedValue: 100 };
            let asset2Data = { objectType: assetType, assetID: assetID2, color: 'blue', size: 35, appraisedValue: 727 };

            console.log('\n**************** As Org1 Client ****************');
            console.log('Adding Assets to work with: Submit Transaction: CreateAsset ' + assetID1);
            let statefulTxn = contractOrg1.createTransaction('CreateAsset');
            //if you need to customize endorsement to specific set of Orgs, use setEndorsingOrganizations
            //statefulTxn.setEndorsingOrganizations(mspOrg1);
            let tmapData = Buffer.from(JSON.stringify(asset1Data));
            statefulTxn.setTransient({
                asset_properties: tmapData
            });
            result = await statefulTxn.submit();

            //Add asset2
            console.log('Submit Transaction: CreateAsset ' + assetID2);
            statefulTxn = contractOrg1.createTransaction('CreateAsset');
            tmapData = Buffer.from(JSON.stringify(asset2Data));
            statefulTxn.setTransient({
                asset_properties: tmapData
            });
            result = await statefulTxn.submit();

            console.log('\n***********************');
            console.log('Evaluate Transaction: GetAssetByRange asset0-asset9');
            // GetAssetByRange returns assets on the ledger with ID in the range of startKey (inclusive) and endKey (exclusive)
            result = await contractOrg1.evaluateTransaction('GetAssetByRange', 'asset0', 'asset9');
            console.log('  result: ' + prettyJSONString(result.toString()));

            console.log('\n***********************');
            console.log('Evaluate Transaction: ReadAssetPrivateDetails from ' + org1PrivateCollectionName);
            // ReadAssetPrivateDetails reads data from Org's private collection. Args: collectionName, assetID
            result = await contractOrg1.evaluateTransaction('ReadAssetPrivateDetails', org1PrivateCollectionName, assetID1);
            console.log('  result: ' + prettyJSONString(result.toString()));


            console.log('\n~~~~~~~~~~~~~~~~ As Org2 Client ~~~~~~~~~~~~~~~~');
            console.log('Evaluate Transaction: ReadAsset ' + assetID1);
            result = await contractOrg2.evaluateTransaction('ReadAsset', assetID1);
            console.log('  result: ' + prettyJSONString(result.toString()));
            let assetOwner = JSON.parse(result.toString()).owner;
            console.log('  Asset owner: ' + Buffer.from(assetOwner, 'base64').toString());

            // Org2 cannot ReadAssetPrivateDetails from Org1's private collection due to Collection policy
            //    Will fail: await contractOrg2.evaluateTransaction('ReadAssetPrivateDetails', org1PrivateCollectionName, assetID1);

            // Buyer from Org2 agrees to buy the asset assetID1 //
            // To purchase the asset, the buyer needs to agree to the same value as the asset owner
            let dataForAgreement = { assetID: assetID1, appraisedValue: 100 };
            console.log('\nSubmit Transaction: AgreeToTransfer payload ' + JSON.stringify(dataForAgreement));
            statefulTxn = contractOrg2.createTransaction('AgreeToTransfer');
            tmapData = Buffer.from(JSON.stringify(dataForAgreement));
            statefulTxn.setTransient({
                asset_value: tmapData
            });
            result = await statefulTxn.submit();

            //Buyer can withdraw the Agreement, using DeleteTranferAgreement
            /*statefulTxn = contractOrg2.createTransaction('DeleteTranferAgreement');
            statefulTxn.setEndorsingOrganizations(mspOrg2);
            let dataForDeleteAgreement = { assetID: assetID1 };
            tmapData = Buffer.from(JSON.stringify(dataForDeleteAgreement));
            statefulTxn.setTransient({
                agreement_delete: tmapData
            });
            result = await statefulTxn.submit();*/

            console.log('\n**************** As Org1 Client ****************');
            // All members can send txn ReadTransferAgreement, set by Org2 above
            console.log('Evaluate Transaction: ReadTransferAgreement ' + assetID1);
            result = await contractOrg1.evaluateTransaction('ReadTransferAgreement', assetID1);
            console.log('  result: ' + prettyJSONString(result.toString()));

            // Transfer the asset to Org2 //
            // To transfer the asset, the owner needs to pass the MSP ID of new asset owner, and initiate the transfer
            console.log('Submit Transaction: TransferAsset ' + assetID1);
            let buyerDetails = { assetID: assetID1, buyerMSP: mspOrg2 };
            statefulTxn = contractOrg1.createTransaction('TransferAsset');
            tmapData = Buffer.from(JSON.stringify(buyerDetails));
            statefulTxn.setTransient({
                asset_owner: tmapData
            });
            result = await statefulTxn.submit();

            console.log('\n***********************');
            //Again ReadAsset : results will show that the buyer identity now owns the asset:
            console.log('Evaluate Transaction: ReadAsset ' + assetID1);
            result = await contractOrg1.evaluateTransaction('ReadAsset', assetID1);
            console.log('  result: ' + prettyJSONString(result.toString()));
            assetOwner = JSON.parse(result.toString()).owner;
            console.log('  Asset owner: ' + Buffer.from(assetOwner, 'base64').toString());

            //Confirm that transfer removed the private details from the Org1 collection:
            console.log('Evaluate Transaction: ReadAssetPrivateDetails');
            // ReadAssetPrivateDetails reads data from Org's private collection: Should return empty
            result = await contractOrg1.evaluateTransaction('ReadAssetPrivateDetails', org1PrivateCollectionName, assetID1);
            console.log('  result: ' + prettyJSONString(result.toString()));

            console.log('Evaluate Transaction: ReadAsset ' + assetID2);
            result = await contractOrg1.evaluateTransaction('ReadAsset', assetID2);
            console.log('  result: ' + prettyJSONString(result.toString()));

            console.log('\n***********************');
            // Delete Asset2
            console.log('Deleting Asset ' + assetID2);
            statefulTxn = contractOrg1.createTransaction('DeleteAsset');

            let dataForDelete = { assetID: assetID2 };
            tmapData = Buffer.from(JSON.stringify(dataForDelete));
            statefulTxn.setTransient({
                asset_delete: tmapData
            });
            result = await statefulTxn.submit();
            console.log('Evaluate Transaction: ReadAsset ' + assetID2);
            result = await contractOrg1.evaluateTransaction('ReadAsset', assetID2);
            console.log('  result: ' + prettyJSONString(result.toString()));

            console.log('\n~~~~~~~~~~~~~~~~ As Org2 Client ~~~~~~~~~~~~~~~~');
            // Org2 can ReadAssetPrivateDetails: Org2 is owner, and private details exist in new owner's Collection
            console.log('Evaluate Transaction as Org2: ReadAssetPrivateDetails ' + assetID1 + ' from ' + org2PrivateCollectionName);
            result = await contractOrg2.evaluateTransaction('ReadAssetPrivateDetails', org2PrivateCollectionName, assetID1);
            console.log('  result: ' + prettyJSONString(result.toString()));
        } finally {
            // Disconnect from the gateway peer when all work for this client identity is complete
            gatewayOrg1.disconnect();
            gatewayOrg2.disconnect();
        }
    } catch (error) {
        console.error(`Error in transaction: ${error}`);
        if (error.stack) {
            console.error(error.stack);
        }
        process.exit(1);
    }
}

main();