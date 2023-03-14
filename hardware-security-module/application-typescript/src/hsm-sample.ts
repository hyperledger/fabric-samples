/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import * as grpc from '@grpc/grpc-js';
import * as crypto from 'crypto';
import { connect, Gateway, HSMSigner, HSMSignerFactory, HSMSignerOptions, signers } from '@hyperledger/fabric-gateway';
import * as fs from 'fs';
import * as jsrsa from 'jsrsasign';
import * as path from 'path';
import { TextDecoder } from 'util';

const mspId = 'Org1MSP';
const user = 'HSMUser';
const assetId = `asset${Date.now()}`;
const utf8Decoder = new TextDecoder();

// Sample uses fabric-ca-client generated HSM identities, certificate is located in the signcerts directory
// and has been stored in a directory of the name given to the identity.

const certPath = path.resolve(__dirname, '..', '..', 'crypto-material', 'hsm', user, 'signcerts', 'cert.pem');

const tlsCertPath = path.resolve(__dirname, '..', '..', '..', 'test-network','organizations','peerOrganizations', 'org1.example.com', 'peers', 'peer0.org1.example.com', 'tls', 'ca.crt');
const peerEndpoint = 'localhost:7051';

async function main() {
    console.log('\nRunning the Node HSM sample');
    let client;
    let gateway;
    let hsmSignerFactory;
    let hsmSigner;

    try {
    // The gRPC client connection should be shared by all Gateway connections to this endpoint
        client = await newGrpcConnection();

        // get an HSMSigner Factory. You only need to do this once for the application
        hsmSignerFactory = signers.newHSMSignerFactory(findSoftHSMPKCS11Lib());
        const credentials = await fs.promises.readFile(certPath);

        // Get the signer function and a close function. The close function closes the signer
        // once there is no further need for it.
        hsmSigner = await newHSMSigner(hsmSignerFactory, credentials.toString());
        gateway = connect({
            client,
            identity: { mspId, credentials },
            signer:hsmSigner.signer,
        });

        await exampleTransaction(gateway);
        console.log();
        console.log('Node HSM sample completed successfully');
    } finally {
        gateway?.close();
        client?.close();
        hsmSigner?.close();
        hsmSignerFactory?.dispose();
    }
}

async function exampleTransaction(gateway: Gateway):Promise<void> {

    const channelName = envOrDefault('CHANNEL_NAME', 'mychannel');
    const chaincodeName = envOrDefault('CHAINCODE_NAME', 'basic');

    const network = gateway.getNetwork(channelName);
    const contract = network.getContract(chaincodeName);

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

    console.log('\n--> Evaluate Transaction: ReadAsset, function returns asset attributes');

    const resultBytes = await contract.evaluateTransaction('ReadAsset', assetId);

    const resultJson = utf8Decoder.decode(resultBytes);
    const result = JSON.parse(resultJson);
    console.log('*** Result:', result);
}

async function newGrpcConnection(): Promise<grpc.Client> {
    const tlsRootCert = await fs.promises.readFile(tlsCertPath);
    const tlsCredentials = grpc.credentials.createSsl(tlsRootCert);

    return new grpc.Client(peerEndpoint, tlsCredentials, {
        'grpc.ssl_target_name_override': 'peer0.org1.example.com'
    });
}

// Create a new HSM Signer
async function newHSMSigner(hsmSignerFactory: HSMSignerFactory, certificatePEM: string): Promise<HSMSigner> {
    const ski = getSKIFromCertificate(certificatePEM);

    // Options for the signer based on using SoftHSM with Token initialized as follows
    // softhsm2-util --init-token --slot 0 --label "ForFabric" --pin 98765432 --so-pin 1234
    const hsmSignerOptions: HSMSignerOptions = {
        label: 'ForFabric',
        pin: '98765432',
        identifier: ski
    }
    return hsmSignerFactory.newSigner(hsmSignerOptions);
}

// Utility to find the SoftHSM PKCS11 library as it's location can vary based on
// operating system and version
function findSoftHSMPKCS11Lib(): string {
    const commonSoftHSMPathNames = [
        '/usr/lib/softhsm/libsofthsm2.so',
        '/usr/lib/x86_64-linux-gnu/softhsm/libsofthsm2.so',
        '/usr/local/lib/softhsm/libsofthsm2.so',
        '/usr/lib/libacsp-pkcs11.so',
        '/opt/homebrew/lib/softhsm/libsofthsm2.so'
    ];
    const pkcs11lib = process.env['PKCS11_LIB'];
    if (pkcs11lib) {
        commonSoftHSMPathNames.push(pkcs11lib);
    }
    for (const pathnameToTry of commonSoftHSMPathNames) {
        if (fs.existsSync(pathnameToTry)) {
            return pathnameToTry
        }
    }

    throw new Error('Unable to find PKCS11 library')
}

// fabric-ca-client set's the CKA_ID of the public/private keys in the HSM to a generated SKI
// value. This function replicates that calculation from a certificate PEM so that the HSM
// object associated with the certificate can be found
function getSKIFromCertificate(certificatePEM: string): Buffer {
    const key = jsrsa.KEYUTIL.getKey(certificatePEM);
    const uncompressedPoint = getUncompressedPointOnCurve(key as jsrsa.KJUR.crypto.ECDSA);
    const hashBuffer = crypto.createHash('sha256');
    hashBuffer.update(uncompressedPoint);

    const digest = hashBuffer.digest('hex');
    return Buffer.from(digest, 'hex');
}

function getUncompressedPointOnCurve(key: jsrsa.KJUR.crypto.ECDSA): Buffer {
    const xyhex = key.getPublicKeyXYHex();
    const xBuffer = Buffer.from(xyhex.x, 'hex');
    const yBuffer = Buffer.from(xyhex.y, 'hex');
    const uncompressedPrefix = Buffer.from('04', 'hex');
    const uncompressedPoint = Buffer.concat([uncompressedPrefix, xBuffer, yBuffer]);
    return uncompressedPoint;
}

/**
 * envOrDefault() will return the value of an environment variable, or a default value if the variable is undefined.
 */
function envOrDefault(key: string, defaultValue: string): string {
    return process.env[key] || defaultValue;
}

main().catch(error => {
    console.error('******** FAILED to run the application:', error);
    process.exitCode = 1;
});
