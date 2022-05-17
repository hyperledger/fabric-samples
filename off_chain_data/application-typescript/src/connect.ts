/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import * as grpc from '@grpc/grpc-js';
import { ConnectOptions, Identity, Signer, signers } from '@hyperledger/fabric-gateway';
import * as crypto from 'crypto';
import { promises as fs } from 'fs';
import * as path from 'path';

export const channelName = process.env.CHANNEL_NAME ?? 'mychannel';
export const chaincodeName = process.env.CHAINCODE_NAME ?? 'basic';

const peerName = 'peer0.org1.example.com';
const mspId = process.env.MSP_ID ?? 'Org1MSP';

// Path to crypto materials.
const cryptoPath = path.resolve(process.env.CRYPTO_PATH ?? path.resolve(__dirname, '..', '..', '..', 'test-network', 'organizations', 'peerOrganizations', 'org1.example.com'));

// Path to user private key directory.
const keyDirectoryPath = path.resolve(process.env.KEY_DIRECTORY_PATH ?? path.resolve(__dirname, cryptoPath, 'users', 'User1@org1.example.com', 'msp', 'keystore'));

// Path to user certificate.
const certPath = path.resolve(process.env.CERT_PATH ?? path.resolve(__dirname, cryptoPath, 'users', 'User1@org1.example.com', 'msp', 'signcerts', 'cert.pem'));

// Path to peer tls certificate.
const tlsCertPath = path.resolve(process.env.TLS_CERT_PATH ?? path.resolve(__dirname, cryptoPath, 'peers', peerName, 'tls', 'ca.crt'));

// Gateway peer endpoint.
const peerEndpoint = process.env.PEER_ENDPOINT ?? 'localhost:7051';

// Gateway peer SSL host name override.
const peerHostAlias = process.env.PEER_HOST_ALIAS ?? peerName;

export async function newGrpcConnection(): Promise<grpc.Client> {
    const tlsRootCert = await fs.readFile(tlsCertPath);
    const tlsCredentials = grpc.credentials.createSsl(tlsRootCert);
    return new grpc.Client(peerEndpoint, tlsCredentials, {
        'grpc.ssl_target_name_override': peerHostAlias,
    });
}

export async function newConnectOptions(client: grpc.Client): Promise<ConnectOptions> {
    return {
        client,
        identity: await newIdentity(),
        signer: await newSigner(),
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
    };
}

async function newIdentity(): Promise<Identity> {
    const credentials = await fs.readFile(certPath);
    return { mspId, credentials };
}

async function newSigner(): Promise<Signer> {
    const keyFiles = await fs.readdir(keyDirectoryPath);
    if (keyFiles.length === 0) {
        throw new Error(`No private key files found in directory ${keyDirectoryPath}`);
    }
    const keyPath = path.resolve(keyDirectoryPath, keyFiles[0]);
    const privateKeyPem = await fs.readFile(keyPath);
    const privateKey = crypto.createPrivateKey(privateKeyPem);
    return signers.newPrivateKeySigner(privateKey);
}
