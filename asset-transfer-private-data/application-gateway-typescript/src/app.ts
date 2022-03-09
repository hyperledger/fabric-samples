/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { connect, Contract } from '@hyperledger/fabric-gateway';
import { TextDecoder } from 'util';
import {
    certPathOrg1, certPathOrg2, keyDirectoryPathOrg1, keyDirectoryPathOrg2, newGrpcConnection, newIdentity,
    newSigner, peerEndpointOrg1, peerEndpointOrg2, peerNameOrg1, peerNameOrg2, tlsCertPathOrg1, tlsCertPathOrg2
} from './connect';

const channelName = 'mychannel';
const chaincodeName = 'private';
const mspIdOrg1 = 'Org1MSP';
const mspIdOrg2 = 'Org2MSP';

const utf8Decoder = new TextDecoder();

// Collection Names
const org1PrivateCollectionName = 'Org1MSPPrivateCollection';
const org2PrivateCollectionName = 'Org2MSPPrivateCollection';

const RED = '\x1b[31m\n';
const RESET = '\x1b[0m';

// Use a unique key so that we can run multiple times
const now = Date.now();
const assetID1 = `asset${now}`;
const assetID2 = `asset${now + 1}`;

async function main(): Promise<void> {
    const clientOrg1 = await newGrpcConnection(
        tlsCertPathOrg1,
        peerEndpointOrg1,
        peerNameOrg1
    );

    const gatewayOrg1 = connect({
        client: clientOrg1,
        identity: await newIdentity(certPathOrg1, mspIdOrg1),
        signer: await newSigner(keyDirectoryPathOrg1),
    });

    const clientOrg2 = await newGrpcConnection(
        tlsCertPathOrg2,
        peerEndpointOrg2,
        peerNameOrg2
    );

    const gatewayOrg2 = connect({
        client: clientOrg2,
        identity: await newIdentity(certPathOrg2, mspIdOrg2),
        signer: await newSigner(keyDirectoryPathOrg2),
    });

    try {
        // Get the smart contract as an Org1 client.
        const contractOrg1 = gatewayOrg1
            .getNetwork(channelName)
            .getContract(chaincodeName);

        // Get the smart contract as an Org2 client.
        const contractOrg2 = gatewayOrg2
            .getNetwork(channelName)
            .getContract(chaincodeName);

        console.log('\n~~~~~~~~~~~~~~~~ As Org1 Client ~~~~~~~~~~~~~~~~');

        // Create new assets on the ledger.
        await createAssets(contractOrg1);

        // Read asset from the Org1's private data collection with ID in the given range.
        await getAssetsByRange(contractOrg1);

        try{
            //Attempt to transfer asset without prior aprroval from Org2, transaction expected to fail.
            console.log('\nAttempt TransferAsset without prior AgreeToTransfer');
            await transferAsset(contractOrg1, assetID1);
            doFail('TransferAsset transaction succeeded when it was expected to fail');
        }
        catch(e){
            console.log(`*** Received expected error: ${e}`);
        }

        console.log('\n~~~~~~~~~~~~~~~~ As Org2 Client ~~~~~~~~~~~~~~~~');

        // Read the asset by ID.
        await readAssetByID(contractOrg2, assetID1);

        // Make agreement to transfer the asset from Org1 to Org2.
        await agreeToTransfer(contractOrg2, assetID1);

        console.log('\n~~~~~~~~~~~~~~~~ As Org1 Client ~~~~~~~~~~~~~~~~');

        // Read transfer agreement.
        await readTransferAgreement(contractOrg1, assetID1);

        // Transfer asset to Org2.
        await transferAsset(contractOrg1, assetID1);

        // Again ReadAsset : results will show that the buyer identity now owns the asset.
        await readAssetByID(contractOrg1, assetID1);

        // Confirm that transfer removed the private details from the Org1 collection.
        const org1ReadSuccess = await readAssetPrivateDetails(contractOrg1, assetID1, org1PrivateCollectionName);
        if (org1ReadSuccess) {
            doFail(`Asset private data still exists in ${org1PrivateCollectionName}`);
        }

        console.log('\n~~~~~~~~~~~~~~~~ As Org2 Client ~~~~~~~~~~~~~~~~');

        // Org2 can read asset private details: Org2 is owner, and private details exist in new owner's Collection
        const org2ReadSuccess = await readAssetPrivateDetails(contractOrg2, assetID1, org2PrivateCollectionName);
        if (!org2ReadSuccess) {
            doFail(`Asset private data not found in ${org2PrivateCollectionName}`);
        }

        try {
            console.log('\nAttempt DeleteAsset using non-owner organization');
            await deleteAsset(contractOrg2, assetID2);
            doFail('DeleteAsset transaction succeeded when it was expected to fail');
        } catch (e) {
            console.log(`*** Received expected error: ${e}`);
        }

        console.log('\n~~~~~~~~~~~~~~~~ As Org1 Client ~~~~~~~~~~~~~~~~');

        // Delete AssetID2 as Org1.
        await deleteAsset(contractOrg1, assetID2);
    } finally {
        gatewayOrg1.close();
        clientOrg1.close();

        gatewayOrg2.close();
        clientOrg2.close();
    }
}

main().catch((error) => {
    console.error('******** FAILED to run the application:', error);
    process.exitCode = 1;
});

/**
 * Submit a transaction synchronously, blocking until it has been committed to the ledger.
 */
async function createAssets(contract: Contract): Promise<void> {
    const assetType = 'ValuableAsset';

    console.log(`\n--> Submit Transaction: CreateAsset, ID: ${assetID1}`);

    const asset1Data = {
        objectType: assetType,
        assetID: assetID1,
        color: 'green',
        size: 20,
        appraisedValue: 100,
    };

    await contract.submit('CreateAsset', {
        transientData: { asset_properties: JSON.stringify(asset1Data) },
    });

    console.log('*** Transaction committed successfully');
    console.log(`\n--> Submit Transaction: CreateAsset, ID: ${assetID2}`);

    const asset2Data = {
        objectType: assetType,
        assetID: assetID2,
        color: 'blue',
        size: 35,
        appraisedValue: 727,
    };

    await contract.submit('CreateAsset', {
        transientData: { asset_properties: JSON.stringify(asset2Data) },
    });

    console.log('*** Transaction committed successfully');
}

async function getAssetsByRange(contract: Contract): Promise<void> {
    // GetAssetByRange returns assets on the ledger with ID in the range of startKey (inclusive) and endKey (exclusive).
    console.log(`\n--> Evaluate Transaction: ReadAssetPrivateDetails from ${org1PrivateCollectionName}`);

    const resultBytes = await contract.evaluateTransaction(
        'GetAssetByRange',
        assetID1,
        `asset${now + 2}`
    );

    const resultString = utf8Decoder.decode(resultBytes);
    if (!resultString) {
        doFail('Received empty query list for readAssetPrivateDetailsOrg1');
    }
    const result = JSON.parse(resultString);
    console.log('*** Result:', result);
}

async function readAssetByID(contract: Contract, assetID: string): Promise<void> {
    console.log(`\n--> Evaluate Transaction: ReadAsset, ID: ${assetID}`);
    const resultBytes = await contract.evaluateTransaction('ReadAsset', assetID);

    const resultString = utf8Decoder.decode(resultBytes);
    if (!resultString) {
        doFail('Received empty result for ReadAsset');
    }
    const result = JSON.parse(resultString);
    console.log('*** Result:', result);
}

async function agreeToTransfer(contract: Contract, assetID: string): Promise<void> {
    // Buyer from Org2 agrees to buy the asset//
    // To purchase the asset, the buyer needs to agree to the same value as the asset owner

    const dataForAgreement = { assetID, appraisedValue: 100 };
    console.log('\n--> Submit Transaction: AgreeToTransfer, payload:', dataForAgreement);

    await contract.submit('AgreeToTransfer', {
        transientData: { asset_value: JSON.stringify(dataForAgreement) },
    });

    console.log('*** Transaction committed successfully');
}

async function readTransferAgreement(contract: Contract, assetID: string): Promise<void> {
    console.log(`\n--> Evaluate Transaction: ReadTransferAgreement, ID: ${assetID}`);

    const resultBytes = await contract.evaluateTransaction(
        'ReadTransferAgreement',
        assetID
    );

    const resultString = utf8Decoder.decode(resultBytes);
    if (!resultString) {
        doFail('Received no result for ReadTransferAgreement');
    }
    const result = JSON.parse(resultString);
    console.log('*** Result:', result);
}

async function transferAsset(contract: Contract, assetID: string): Promise<void> {
    console.log(`\n--> Submit Transaction: TransferAsset, ID: ${assetID}`);

    const buyerDetails = { assetID, buyerMSP: mspIdOrg2 };
    await contract.submit('TransferAsset', {
        transientData: { asset_owner: JSON.stringify(buyerDetails) },
    });

    console.log('*** Transaction committed successfully');
}

async function deleteAsset(contract: Contract, assetID: string): Promise<void> {
    console.log('\n--> Submit Transaction: DeleteAsset, ID:', assetID);
    const dataForDelete = { assetID };
    await contract.submit('DeleteAsset', {
        transientData: { asset_delete: JSON.stringify(dataForDelete) },
    });

    console.log('*** Transaction committed successfully');
}
async function readAssetPrivateDetails(contract: Contract, assetID: string, collectionName: string): Promise<boolean> {
    console.log(`\n--> Evaluate Transaction: ReadAssetPrivateDetails from ${collectionName}, ID: ${assetID}`);

    const resultBytes = await contract.evaluateTransaction(
        'ReadAssetPrivateDetails',
        collectionName,
        assetID
    );

    const resultJson = utf8Decoder.decode(resultBytes);
    if (!resultJson) {
        console.log('*** No result');
        return false;
    }
    const result = JSON.parse(resultJson);
    console.log('*** Result:', result);
    return true;
}

export function doFail(msgString: string): never {
    console.error(`${RED}\t${msgString}${RESET}`);
    throw new Error(msgString);
}