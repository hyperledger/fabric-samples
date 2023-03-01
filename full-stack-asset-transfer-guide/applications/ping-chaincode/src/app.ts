/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { connect, Contract, Identity, Signer, signers } from '@hyperledger/fabric-gateway';
import * as path from 'path';
import { TextDecoder } from 'util';
import { ConnectionHelper } from './fabric-connection-profile';
import JSONIDAdapter from './jsonid-adapter';

import { dump } from 'js-yaml';

import {config} from 'dotenv';
config({path:'app.env'});
import * as env from 'env-var'

const channelName = env.get('CHANNEL_NAME').default('mychannel').asString();
const chaincodeName = env.get('CHAINCODE_NAME').default('conga-nft-contract').asString();

const connectionProfile = env.get('CONN_PROFILE_FILE').required().asString();
const identityFile = env.get('ID_FILE').required().asString()
const identityDir = env.get('ID_DIR').required().asString()
const mspID = env.get('MSPID').required().asString()
const tls = env.get('TLS_ENABLED').default("false").asBool();

const utf8Decoder = new TextDecoder();


async function main(): Promise<void> {

    const cp = await ConnectionHelper.loadProfile(connectionProfile);
    

    // The gRPC client connection should be shared by all Gateway connections to this endpoint.
    const client = await ConnectionHelper.newGrpcConnection(cp,tls);
    console.log("Created GRPC Connection")

    const jsonAdapter: JSONIDAdapter = new JSONIDAdapter(path.resolve(identityDir),mspID);
    const identity = await jsonAdapter.getIdentity(identityFile);
    const signer = await jsonAdapter.getSigner(identityFile);

    console.log("Loaded Identity")
    const gateway = connect({
        client,
        identity,
        signer,
        // Default timeouts for different gRPC calls
        evaluateOptions: () => {
            return { deadline: Date.now() + 5000 }; // 5 seconds
        },
        endorseOptions: () => {
            return { deadline: Date.now() + 15000 }; // 15 seconds
        },
        submitOptions: () => {
            return { deadline: Date.now() + 5000 }; // 5 seconds
        },
        commitStatusOptions: () => {
            return { deadline: Date.now() + 60000 }; // 1 minute
        },
    });

    try {
        // Get a network instance representing the channel where the smart contract is deployed.
        const network = gateway.getNetwork(channelName);

        // Get the smart contract from the network.
        const contract = network.getContract(chaincodeName);

        // Return all the current assets on the ledger.
        await ping(contract);

    } finally {
        gateway.close();
        client.close();
    }
}

main().catch(error => {
    console.error('******** FAILED to run the application:', error);
    process.exitCode = 1;
});

/**
 * Evaluate a transaction to query ledger state.
 */
async function ping(contract: Contract): Promise<void> {
    console.log('\n--> Evaluate Transaction: Get Contract Metdata from :  org.hyperledger.fabric:GetMetadata');

    const resultBytes = await contract.evaluateTransaction('org.hyperledger.fabric:GetMetadata');

    const resultJson = utf8Decoder.decode(resultBytes);
    const result = JSON.parse(resultJson);
    console.log('*** Result:');
    console.log(dump(result));
}

