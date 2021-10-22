/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import * as grpc from '@grpc/grpc-js';
import * as crypto from 'crypto';
import { connect, Identity, Signer, signers ,Contract} from 'fabric-gateway';
import { promises as fs } from 'fs';
import * as path from 'path';

const channelName = 'mychannel';
const chaincodeName = 'basic';
const mspId = 'Org1MSP';

// path to crypto materials
const cryptoPath = path.resolve(
    __dirname,
    '..',
    '..',
    '..',
    'test-network',
    'organizations',
    'peerOrganizations',
    'org1.example.com',
);

//path to user private key directory
const keyDirectoryPath = path.resolve(
    cryptoPath,
    'users',
    'User1@org1.example.com',
    'msp',
    'keystore'
);
//path to user certificate
const certPath = path.resolve(
    cryptoPath,
    'users',
    'User1@org1.example.com',
    'msp',
    'signcerts',
    'cert.pem',
);
//path to peer tls certificate
const tlsCertPath = path.resolve(
    cryptoPath,
    'peers',
    'peer0.org1.example.com',
    'tls',
    'ca.crt',
);

//Gateway peer endpoint
const peerEndpoint = 'localhost:7051';

// pre-requisites:
// - fabric-sample two organization test-network setup with two peers and ordering service and and 2 certificate authorities
//         ===> from directory /fabric-samples/test-network
//         ./network.sh up createChannel
// - Use any of the asset-transfer-basic chaincodes deployed on the channel "mychannel"
//   with the chaincode name of "basic". The following deploy command will package,
//   install, approve, and commit the javascript chaincode, all the actions it takes
//   to deploy a chaincode to a channel.
//         ===> from directory /fabric-samples/test-network
//         ./network.sh deployCC -ccn basic -ccp ../asset-transfer-basic/chaincode-typescript/ -ccl typescript
// - Be sure that node.js is installed
//         ===> from directory /fabric-samples/asset-transfer-basic/application-typescript
//         node -v
// - npm installed code dependencies
//         ===> from directory /fabric-samples/asset-transfer-basic/application-typescript
//         npm install
// - to run this test application
//         ===> from directory /fabric-samples/asset-transfer-basic/application-typescript
//         npm start

/**
 *  A test application to show basic queries operations with any of the asset-transfer-basic chaincodes
 *   -- How to submit a transaction
 *   -- How to query and check the results
 *
 * To see the SDK workings, try setting the logging to show on the console before running
 *        export HFC_LOGGING='{"debug":"console"}'
 */

async function main():Promise<void> {

    // The gRPC client connection should be shared by all Gateway connections to this endpoint
    const client = await newGrpcConnection();

    const gateway = connect({
        client,
        identity: await newIdentity(),
        signer: await newSigner(),
    });

    try {

        // Build a network instance based on the channel where the smart contract is deployed
        const network = gateway.getNetwork(channelName);

        // Get the contract from the network.
        const contract = network.getContract(chaincodeName);

        // Initialize a set of asset data on the channel using the chaincode 'InitLedger' function.
        await initLedger(contract);

        //Return all the current assets on the ledger.
        await getAllAssets(contract);

        //Create new asset on the ledger.
        await createAsset(contract);

        //Update an existing asset asynchronously.
        await updateAssetAsync(contract);

        //Get the asset details by assetID
        await readAssetByID(contract);

        //Update an asset which does not exist.
        await updateNonExistentAsset(contract)

    } finally {
        gateway.close();
        client.close();
    }

}

main().catch(error => console.error('******** FAILED to run the application:', error));

async function newGrpcConnection(): Promise<grpc.Client> {
    const tlsRootCert = await fs.readFile(tlsCertPath);
    const tlsCredentials = grpc.credentials.createSsl(tlsRootCert);
    const GrpcClient = grpc.makeGenericClientConstructor({}, '');
    return new GrpcClient(peerEndpoint, tlsCredentials, {
        'grpc.ssl_target_name_override': 'peer0.org1.example.com',
    });
}

async function newIdentity(): Promise<Identity> {
    const credentials = await fs.readFile(certPath);
    return { mspId, credentials };
}

async function newSigner(): Promise<Signer> {
    const files = await fs.readdir(keyDirectoryPath);
    const privateKeyPem = await fs.readFile(path.resolve(keyDirectoryPath,files[0]));
    const privateKey = crypto.createPrivateKey(privateKeyPem);
    return signers.newPrivateKeySigner(privateKey);
}


async function initLedger(contract:Contract):Promise<void> {
    // This type of transaction would only be run once by an application the first time it was started after it
    // deployed the first time. Any updates to the chaincode deployed later would likely not need to run
    // an "init" type function.

    console.log(
        '\n--> Submit Transaction: InitLedger, function creates the initial set of assets on the ledger',
    );

    // Submit a transaction, blocking until the transaction has been committed on the ledger
    await contract.submitTransaction('InitLedger');

    console.log('*** Result: committed');

}

async function getAllAssets(contract:Contract):Promise<void> {
    console.log(
        '\n--> Evaluate Transaction: GetAllAssets, function returns all the current assets on the ledger',
    );
    const result = await contract.evaluateTransaction('GetAllAssets');
    console.log(`*** Result: ${Buffer.from(result).toString()}`);
}

async function createAsset(contract:Contract):Promise<void> {
    console.log(
        '\n--> Submit Transaction: CreateAsset, creates new asset with ID, color, owner, size, and appraisedValue arguments',
    );
    await contract.submitTransaction(
        'CreateAsset',
        'asset13',
        'yellow',
        '5',
        'Tom',
        '1300',
    );
    console.log('*** Result: committed');
}

async function updateAssetAsync(contract:Contract):Promise<void> {

    // Submit transaction asynchronously, blocking until the transaction has been sent to the orderer, and allowing
    // this thread to process the chaincode response (e.g. update a UI) without waiting for the commit notification

    const commit = await contract.submitAsync('UpdateAsset', {
        arguments: ['asset1', 'blue', '5', 'Tomoko', '400'],
    });

    console.log('*** Waiting for transaction commit');


    const status = await commit.getStatus();
    if (!status.successful) {
        throw new Error(`Transaction ${status.transactionId} failed to commit with status code ${status.code}`);
    }

    console.log('*** Transaction committed successfully');

}

async function readAssetByID(contract:Contract):Promise<void> {
    console.log(
        '\n--> Evaluate Transaction: ReadAsset, function returns "asset1" attributes',
    );
    const result = await contract.evaluateTransaction('ReadAsset', 'asset1');
    console.log(`*** Result: ${Buffer.from(result).toString()}`);

}

async function updateNonExistentAsset(contract:Contract):Promise<void>{
    try {
        // How about we try a transaction where the executing chaincode throws an error
        // Notice how the submitTransaction will throw an error containing the error thrown by the chaincode
        console.log(
            '\n--> Submit Transaction: UpdateAsset asset70, asset70 does not exist and should return an error',
        );
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
        console.log(`*** Successfully caught the error: \n ${error}`);
    }
}



