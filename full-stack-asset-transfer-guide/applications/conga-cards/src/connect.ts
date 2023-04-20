/*
 * Copyright contributors to the Hyperledgendary Full Stack Asset Transfer Guide project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import * as grpc from '@grpc/grpc-js';
import { connect, Gateway, Identity, Signer, signers } from '@hyperledger/fabric-gateway';
import * as crypto from 'crypto';
import * as fs from 'fs';
import * as path from 'path';
import { CLIENT_CERT_PATH, GATEWAY_ENDPOINT, HOST_ALIAS, MSP_ID, PRIVATE_KEY_PATH, TLS_CERT_PATH } from './config';

export async function newGrpcConnection(): Promise<grpc.Client> {
    if (TLS_CERT_PATH) {
        const tlsRootCert = await fs.promises.readFile(TLS_CERT_PATH);
        const tlsCredentials = grpc.credentials.createSsl(tlsRootCert);
        return new grpc.Client(GATEWAY_ENDPOINT, tlsCredentials, newGrpcClientOptions());
    }

    return new grpc.Client(GATEWAY_ENDPOINT, grpc.ChannelCredentials.createInsecure());
}

function newGrpcClientOptions(): grpc.ClientOptions {
    const result: grpc.ClientOptions = {};
    if (HOST_ALIAS) {
        result['grpc.ssl_target_name_override'] = HOST_ALIAS; // Only required if server TLS cert does not match the endpoint address we use
    }
    return result;
}

export async function newGatewayConnection(client: grpc.Client): Promise<Gateway> {
    return connect({
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
    });
}

async function newIdentity(): Promise<Identity> {
    const certPath = path.resolve(CLIENT_CERT_PATH);
    const credentials = await fs.promises.readFile(certPath);

    return { mspId: MSP_ID, credentials };
}

async function newSigner(): Promise<Signer> {
    const keyPath = path.resolve(PRIVATE_KEY_PATH);
    const privateKeyPem = await fs.promises.readFile(keyPath);
    const privateKey = crypto.createPrivateKey(privateKeyPem);

    return signers.newPrivateKeySigner(privateKey);
}
