/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { resolve } from 'path';
import { assertDefined } from './utils';

export const GATEWAY_ENDPOINT = assertEnv('ENDPOINT');
export const MSP_ID = assertEnv('MSP_ID');
export const CLIENT_CERT_PATH = resolve(assertEnv('CERTIFICATE'));
export const PRIVATE_KEY_PATH = resolve(assertEnv('PRIVATE_KEY'));
export const TLS_CERT_PATH = resolvePathIfPresent(process.env.TLS_CERT);
export const CHANNEL_NAME = process.env.CHANNEL_NAME ?? 'mychannel';
export const CHAINCODE_NAME = process.env.CHAINCODE_NAME ?? 'asset-transfer';

// Gateway peer SSL host name override.
export const HOST_ALIAS = process.env.HOST_ALIAS;

function assertEnv(envName: string): string {
    return assertDefined(process.env[envName], () => {
        console.error('The following environment variables must be set:' +
            '\n    ENDPOINT       - Endpoint address of the gateway service' +
            '\n    MSP_ID         - User\'s organization Member Services Provider ID' +
            '\n    CERTIFICATE    - User\'s certificate file' +
            '\n    PRIVATE_KEY    - User\'s private key file' +
            '\n' +
            '\nThe following environment variables are optional:' +
            '\n    CHANNEL_NAME   - Channel to which the chaincode is deployed' +
            '\n    CHAINCODE_NAME - Channel to which the chaincode is deployed' +
            '\n    TLS_CERT       - TLS CA root certificate (only if using TLS and private CA)' +
            '\n    HOST_ALIAS     - TLS hostname override (only if TLS cert does not match endpoint)' +
            '\n');
        return `Environment variable ${envName} not set`;
    });
}

function resolvePathIfPresent(path: string | undefined): string | undefined {
    if (path == undefined) {
        return undefined;
    }

    return resolve(path);
}
