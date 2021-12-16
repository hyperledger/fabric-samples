/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

// pre-requisites:
// - fabric-sample two organization test-network setup with two peers, ordering service,
//   and 2 certificate authorities
//         ===> from directory test-network
//         ./network.sh up createChannel -ca
//
// - Use the asset-transfer-events/chaincode-javascript chaincode deployed on
//   the channel "mychannel". The following deploy command will package, install,
//   approve, and commit the javascript chaincode, all the actions it takes
//   to deploy a chaincode to a channel.
//         ===> from directory test-network
//         ./network.sh deployCC -ccn events -ccp ../asset-transfer-events/chaincode-javascript/ -ccl javascript -ccep "OR('Org1MSP.peer','Org2MSP.peer')"
//
// - Be sure that node.js is installed
//         ===> from directory asset-transfer-events/application-javascript
//         node -v
// - npm installed code dependencies
//         ===> from directory asset-transfer-events/application-javascript
//         npm install
// - to build this test application
//         ===> from directory asset-transfer-events/application-javascript
//         npm prepare
// - to run this test application
//         ===> from directory asset-transfer-events/application-javascript
//         npm start

import * as grpc from '@grpc/grpc-js';
import { connect, Contract, Identity, Network, Signer, signers } from '@hyperledger/fabric-gateway';
import * as crypto from 'crypto';
import { promises as fs } from 'fs';
import * as path from 'path';
import { TextDecoder } from 'util';

const channelName = 'mychannel';
const chaincodeName = 'events';
const mspId = 'Org1MSP';

// Path to crypto materials.
const cryptoPath = path.resolve(__dirname, '..', '..', '..', 'test-network', 'organizations', 'peerOrganizations', 'org1.example.com');

// Path to user private key directory.
const keyDirectoryPath = path.resolve(cryptoPath, 'users', 'User1@org1.example.com', 'msp', 'keystore');

// Path to user certificate.
const certPath = path.resolve(cryptoPath, 'users', 'User1@org1.example.com', 'msp', 'signcerts', 'cert.pem');

// Path to peer tls certificate.
const tlsCertPath = path.resolve(cryptoPath, 'peers', 'peer0.org1.example.com', 'tls', 'ca.crt');

// Gateway peer endpoint.
const peerEndpoint = 'localhost:7051';

const utf8Decoder = new TextDecoder();
let assetId = `asset${Date.now()}`;

async function main(): Promise<void> {
    // The gRPC client connection should be shared by all Gateway connections to this endpoint.
    const client = await newGrpcConnection();

    const gateway = connect({
        client,
        identity: await newIdentity(),
        signer: await newSigner(),
    });

    try {

        // Get a network instance representing the channel where the smart contract is deployed.
        const network = gateway.getNetwork(channelName);

        // Get the smart contract from the network.
        const contract = network.getContract(chaincodeName);

        //Start Listening to events.
        startEventListening(network)

        // Create a new asset on the ledger.
        await createAsset(contract);

        // Update an existing asset asynchronously.
        await transferAssetAsync(contract);

        // Get the asset details by assetID.
        await readAssetByID(contract);

        // Delete asset by assetID.
        await deleteAssetByID(contract);

        // Update an asset which does not exist.
        await updateNonExistentAsset(contract)

        console.log('************* BLOCK EVENTS with PRIVATE DATA **************');

        //Generate new assetID
        assetId = `asset${Date.now()}`;

        // Create a new asset with private data on the ledger.
        await createAssetPrivate(contract);

        // Update an existing asset with private data asynchronously.
        await transferAssetAsyncPrivate(contract);

        // Get the asset details along with the private data by assetID.
        await readAssetByIDPrivate(contract);

    } finally {
        gateway.close();
        client.close();
    }
}

main().catch(error => console.error('******** FAILED to run the application:', error));

async function newGrpcConnection(): Promise<grpc.Client> {
    const tlsRootCert = await fs.readFile(tlsCertPath);
    const tlsCredentials = grpc.credentials.createSsl(tlsRootCert);
    return new grpc.Client(peerEndpoint, tlsCredentials, {
        'grpc.ssl_target_name_override': 'peer0.org1.example.com',
    });
}

async function newIdentity(): Promise<Identity> {
    const credentials = await fs.readFile(certPath);
    return { mspId, credentials };
}

async function newSigner(): Promise<Signer> {
    const files = await fs.readdir(keyDirectoryPath);
    const keyPath = path.resolve(keyDirectoryPath, files[0]);
    const privateKeyPem = await fs.readFile(keyPath);
    const privateKey = crypto.createPrivateKey(privateKeyPem);
    return signers.newPrivateKeySigner(privateKey);
}

async function startEventListening(network: Network) {
    console.log('Read chaincode events');
    const events = await network.getChaincodeEvents(chaincodeName);
    try {
        for await (const event of events) {
            const payload = utf8Decoder.decode(event.payload);
            console.log(`Received event name: ${event.eventName}, payload: ${payload}, txID: ${event.transactionId}, blockNumber:${event.blockNumber}`);
        }
    } finally {
        // Ensure event iterator is closed when done reading.
        events.close();
    }
}

/**
 * Submit a transaction synchronously, blocking until it has been committed to the ledger.
 */
async function createAsset(contract: Contract): Promise<void> {
    console.log('\n--> Submit Transaction: CreateAsset, creates new asset with ID, Color, Size, Owner and AppraisedValue arguments');

    await contract.submitTransaction(
        'CreateAsset',
        assetId,
        'yellow',
        '5',
        'Tom',
        '1300',
    );

    console.log('*** Transaction committed successfully');
}

/**
 * Submit transaction asynchronously, allowing the application to process the smart contract response (e.g. update a UI)
 * while waiting for the commit notification.
 */
async function transferAssetAsync(contract: Contract): Promise<void> {
    console.log('\n--> Async Submit Transaction: TransferAsset, updates existing asset owner');

    const commit = await contract.submitAsync('TransferAsset', {
        arguments: [assetId, 'Saptha'],
    });
    const oldOwner = utf8Decoder.decode(commit.getResult());

    console.log(`*** Successfully submitted transaction to transfer ownership from ${oldOwner} to Saptha`);
    console.log('*** Waiting for transaction commit');

    const status = await commit.getStatus();
    if (!status.successful) {
        throw new Error(`Transaction ${status.transactionId} failed to commit with status code ${status.code}`);
    }

    console.log('*** Transaction committed successfully');
}

// Evaluate a transaction to query ledger state by given ID.
async function readAssetByID(contract: Contract): Promise<void> {
    console.log('\n--> Evaluate Transaction: ReadAsset, function returns asset attributes');

    const resultBytes = await contract.evaluateTransaction('ReadAsset', assetId);

    const resultJson = utf8Decoder.decode(resultBytes);
    const result = JSON.parse(resultJson);
    checkAsset(mspId, result,'yellow','5','Saptha','1300')

    console.log('*** Result:', result);
}

/**
 * Submit a transaction synchronously to delete an asset by ID.
 */
async function deleteAssetByID(contract:Contract): Promise<void>{
    console.log('\n--> Submit Transaction: DeleteAsset asset70');

    await contract.submitTransaction(
        'DeleteAsset',
        assetId
    );

    console.log('*** Transaction committed successfully');
}

/**
 * submitTransaction() will throw an error containing details of any error responses from the smart contract.
 */
async function updateNonExistentAsset(contract: Contract): Promise<void>{
    console.log('\n--> Submit Transaction: UpdateAsset asset70, asset70 does not exist and should return an error');

    try {
        await contract.submitTransaction(
            'UpdateAsset',
            'asset70',
            'blue',
            '5',
            'Tomoko',
            '300',
        );
        console.log('******** FAILED to return an error');
    } catch (error) {
        console.log('*** Successfully caught the error: \n', error);
    }
}

/**
 * Verify asset details.
 */
function checkAsset(mspId:string, asset:{
    ID: string,
    Color: string,
    Size: string,
    Owner: string,
    AppraisedValue: string,
    asset_properties:{
        object_type: string,
		asset_id: string,
		Price: string,
		salt: string
    }
}, color:string, size:string, owner:string, appraisedValue:string, price?:string) {
    console.log(`<-- Query results from ${mspId}`);

    console.log(`*** verify asset ${asset.ID}`);

    if (asset) {
        if (asset.Color === color) {
            console.log(`*** asset ${asset.ID} has color ${asset.Color}`);
        } else {
            console.log(`*** asset ${asset.ID} has color of ${asset.Color}`);
        }
        if (asset.Size === size) {
            console.log(`*** asset ${asset.ID} has size ${asset.Size}`);
        } else {
            console.log(`*** Failed size check from ${mspId} - asset ${asset.ID} has size of ${asset.Size}`);
        }
        if (asset.Owner === owner) {
            console.log(`*** asset ${asset.ID} owned by ${asset.Owner}`);
        } else {
            console.log(`*** Failed owner check from ${mspId} - asset ${asset.ID} owned by ${asset.Owner}`);
        }
        if (asset.AppraisedValue === appraisedValue) {
            console.log(`*** asset ${asset.ID} has appraised value ${asset.AppraisedValue}`);
        } else {
            console.log(`*** Failed appraised value check from ${mspId} - asset ${asset.ID} has appraised value of ${asset.AppraisedValue}`);
        }
        if (price) {
            if (asset.asset_properties && asset.asset_properties.Price === price) {
                console.log(`*** asset ${asset.ID} has price ${asset.asset_properties.Price}`);
            } else {
                console.log(`*** Failed price check from ${mspId} - asset ${asset.ID} has price of ${asset.asset_properties.Price}`);
            }
        }
    }
}

/**
 *  Submit transaction, blocking until the transaction has been committed on the ledger.
    The 'transient' data will not get written to the ledger, and is used to send sensitive data to the trusted endorsing peers.
    The gateway will only send this to peers that are included in the ownership policy of all collections accessed by the chaincode function.
 */
async function createAssetPrivate(contract: Contract): Promise<void> {
    console.log('\n--> Submit Transaction: CreateAsset, creates new asset with ID, Color, Size, Owner and AppraisedValue arguments');

    // create the private data with salt and assign to the transaction
    const asset_properties = {
        object_type: 'asset_properties',
        asset_id: assetId,
        Price: '90',
        salt: Buffer.from(Date.now().toString()).toString('hex')
    };

    await contract.submit(
        'CreateAsset',
        {   arguments:[assetId, 'blue', '10', 'James', '100'],
            transientData: asset_properties,
            endorsingOrganizations: [mspId]
        }
    );

    console.log('*** Transaction committed successfully');
}

/**
 * Submit transaction asynchronously, allowing the application to process the smart contract response (e.g. update a UI)
 * while waiting for the commit notification.
 */
async function transferAssetAsyncPrivate(contract: Contract): Promise<void> {
    console.log('\n--> Async Submit Transaction: TransferAsset, updates existing asset owner');

    // update the private data with new salt and assign to the transaction
    const asset_properties = {
        object_type: 'asset_properties',
        asset_id: assetId,
        Price: '90',
        salt: Buffer.from(Date.now().toString()).toString('hex')
    };
    const commit = await contract.submitAsync('TransferAsset', {
        arguments: [assetId, 'David'],
        transientData: asset_properties,
        endorsingOrganizations: [mspId]
    });
    const oldOwner = utf8Decoder.decode(commit.getResult());

    console.log(`*** Successfully submitted transaction to transfer ownership from ${oldOwner} to Davids`);
    console.log('*** Waiting for transaction commit');

    const status = await commit.getStatus();
    if (!status.successful) {
        throw new Error(`Transaction ${status.transactionId} failed to commit with status code ${status.code}`);
    }

    console.log('*** Transaction committed successfully');
}

// Evaluate a transaction to query ledger state by given ID.
async function readAssetByIDPrivate(contract: Contract): Promise<void> {
    console.log('\n--> Evaluate Transaction: ReadAsset, function returns asset attributes');

    const resultBytes = await contract.evaluateTransaction('ReadAsset', assetId);
    const resultJson = utf8Decoder.decode(resultBytes);

    const result = JSON.parse(resultJson);
    checkAsset(mspId, result,'blue','10','David','100')

    console.log('*** Result:', result);
}