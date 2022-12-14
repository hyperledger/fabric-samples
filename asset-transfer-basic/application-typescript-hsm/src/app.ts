/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import * as FabricCAServices from 'fabric-ca-client';
import { Contract, Gateway, GatewayOptions, HsmOptions, HsmX509Provider, Wallets } from 'fabric-network';
import * as fs from 'fs';
import * as path from 'path';
import { buildCCPOrg1, prettyJSONString } from './utils//AppUtil';
import { enrollUserToWallet, registerUser, UserToEnroll, UserToRegister } from './utils/CAUtil';

const walletPath = path.join(__dirname, 'wallet');

// define information about the channel and chaincode that will be driven by this application
const channelName = envOrDefault('CHANNEL_NAME', 'mychannel');
const chaincodeName = envOrDefault('CHAINCODE_NAME', 'default-basic');


// define the CA Registrar
const mspOrg1 = 'Org1MSP';
const org1CAAdminUserId = 'admin';
const org1CAAdminSecret = 'adminpw';

// define the user in org1 to use
const org1UserId = 'appUser';
const org1Secret = 'appUserSecret';
const org1Affiliation = 'org1.department1';

type Request = 'submit' | 'evaluate';

interface TransactionToSendFormat {
    request: Request;
    txName: string;
    txArgs: string[];
    description?: string;
}

// The set of transactions to be invoked
const transactionsToSend: TransactionToSendFormat[] = [
    {
        request: 'submit',
        txName: 'InitLedger',
        txArgs: [],
        description: '\n--> Submit Transaction: InitLedger, function creates the initial set of assets on the ledger',
    },
    {
        request: 'evaluate',
        txName: 'GetAllAssets',
        txArgs: [],
        description: '\n--> Evaluate Transaction: GetAllAssets, function returns all the current assets on the ledger',
    },
    {
        request: 'submit',
        txName: 'CreateAsset',
        txArgs: ['asset13', 'yellow', '5', 'Tom', '1300'],
        description: '\n--> Submit Transaction: CreateAsset, creates new asset with ID, color, owner, size, and appraisedValue arguments',
    },
    {
        request: 'evaluate',
        txName: 'ReadAsset',
        txArgs: ['asset13'],
        description: '\n--> Evaluate Transaction: ReadAsset, function returns an asset with a given assetID',
    },
    {
        request: 'evaluate',
        txName: 'AssetExists',
        txArgs: ['asset1'],
        description: '\n--> Evaluate Transaction: AssetExists, function returns "true" if an asset with given assetID exist',
    },
    {
        request: 'submit',
        txName: 'UpdateAsset',
        txArgs: ['asset1', 'blue', '5', 'Tomoko', '350'],
        description: '\n--> Submit Transaction: UpdateAsset asset1, change the appraisedValue to 350',
    },
    {
        request: 'evaluate',
        txName: 'ReadAsset',
        txArgs: ['asset1'],
        description: '\n--> Evaluate Transaction: ReadAsset, function returns "asset1" attributes',
    },
    {
        request: 'submit',
        txName: 'UpdateAsset',
        txArgs: ['asset70', 'blue', '5', 'Tomoko', '300'],
        description: '\n--> Submit Transaction: UpdateAsset asset70, asset70 does not exist and should return an error',
    },
    {
        request: 'submit',
        txName: 'TransferAsset',
        txArgs: ['asset1', 'Tom'],
        description: '\n--> Submit Transaction: TransferAsset asset1, transfer to new owner of Tom',
    },
    {
        request: 'evaluate',
        txName: 'ReadAsset',
        txArgs: ['asset1'],
        description: '\n--> Evaluate Transaction: ReadAsset, function returns "asset1" attributes',
    },
];

/**
 *  A test application to show basic queries operations with any of the asset-transfer-basic chaincodes
 *   -- How to submit a transaction
 *   -- How to query and check the results
 *
 * To see the SDK workings, try setting the logging to show on the console before running
 *        export HFC_LOGGING='{"debug":"console"}'
 */
async function main() {
    try {
        // build an in memory object with the network configuration (also known as a connection profile)
        const ccp = buildCCPOrg1();

        // softhsm pkcs11 options to use
        const softHSMOptions: HsmOptions = {
            lib: await findSoftHSMPKCS11Lib(),
            pin: process.env.PKCS11_PIN || '98765432',
            label: process.env.PKCS11_LABEL || 'ForFabric',
        };

        // setup the wallet to hold the credentials used by this application
        // Here we add the HSM provider to the provider registry of the wallet
        const wallet = await Wallets.newFileSystemWallet(walletPath);
        const hsmProvider = new HsmX509Provider(softHSMOptions);
        wallet.getProviderRegistry().addProvider(hsmProvider);

        // build an instance of the fabric ca services client based on
        // the information in the network configuration
        const caInfo = ccp.certificateAuthorities['ca.org1.example.com']; // lookup CA details from config
        const caTLSCACerts = caInfo.tlsCACerts.pem;

        // Here we make sure we pass in an HSM enabled cryptosuite to this CA instance
        const hsmCAClient = new FabricCAServices(caInfo.url, { trustedRoots: caTLSCACerts, verify: false }, caInfo.caName, hsmProvider.getCryptoSuite());
        console.log(`Built a CA Client named ${caInfo.caName}`);

        // enroll the CA admin into the wallet if it doesn't already exist in the wallet
        if (!await wallet.get(org1CAAdminUserId)) {
            await enrollUserToWallet({
                caClient: hsmCAClient,
                wallet,
                orgMspId: mspOrg1,
                userId: org1CAAdminUserId,
                userIdSecret: org1CAAdminSecret,
            } as UserToEnroll);
        }

        // if we don't have the required user in the wallet then
        // register this user to the CA (if it's not already registered)
        // then enroll that user into the wallet
        if (!await wallet.get(org1UserId)) {
            await registerUser({
                caClient: hsmCAClient,
                adminId: org1CAAdminUserId,
                wallet,
                orgMspId: mspOrg1,
                userId: org1UserId,
                userIdSecret: org1Secret,
                affiliation: org1Affiliation,
            } as UserToRegister);

            // By default you can only enroll a user once, after that you would have to re-enroll using the current
            // certificate rather than using the secret.
            await enrollUserToWallet({
                caClient: hsmCAClient,
                wallet,
                orgMspId: mspOrg1,
                userId: org1UserId,
                userIdSecret: org1Secret,
            } as UserToEnroll);
        }

        // Create a new gateway instance for interacting with the fabric network bound to our user
        // This sample expects a locally deployed fabric network (ie running on the same machine in docker containers)
        // therefore we set asLocalhost to true
        const gateway = new Gateway();

        const gatewayOpts: GatewayOptions = {
            wallet,
            identity: org1UserId,
            discovery: { enabled: true, asLocalhost: true }, // using asLocalhost as this gateway is using a fabric network deployed locally
        };

        try {
            // setup the gateway instance
            // The user will now be able to create connections to the fabric network and be able to
            // submit transactions and query. All transactions submitted by this gateway will be
            // signed by this user using the credentials stored in the wallet.
            await gateway.connect(ccp, gatewayOpts);

            // Build a network instance based on the channel where the smart contract is deployed
            const network = await gateway.getNetwork(channelName);

            // Get the contract from the network.
            const contract = network.getContract(chaincodeName);

            // loop around all transactions to send, each one will be sent sequentially
            // through the same gateway/network/contract as subsequent transations expect the
            // previous submits to have been committed
            // Note however that a gateway/network/contract can support concurrent submitted
            // transactions.
            for (const transactionToSend of transactionsToSend) {
                await interactWithFabric(contract, transactionToSend);
            }

            console.log('*** The sample ran successfully ***');
        } finally {

            // Disconnect from the gateway when the application is closing
            // This will close all connections to the network
            gateway.disconnect();
        }
    } catch (error) {
        console.error(`******** FAILED to run the application: ${error}`);
    }
}

/**
 * Determine the location of the SoftHSM PKCS11 Library
 * @returns the location of the SoftHSM PKCS11 Library or 'NOT FOUND' if not found
 */
async function findSoftHSMPKCS11Lib() {
    const commonSoftHSMPathNames = [
        '/usr/lib/softhsm/libsofthsm2.so',
        '/usr/lib/x86_64-linux-gnu/softhsm/libsofthsm2.so',
        '/usr/local/lib/softhsm/libsofthsm2.so',
        '/usr/lib/libacsp-pkcs11.so',
    ];

    let pkcsLibPath = 'NOT FOUND';
    if (typeof process.env.PKCS11_LIB === 'string' && process.env.PKCS11_LIB !== '') {
        pkcsLibPath = process.env.PKCS11_LIB;
    } else {

        // Check common locations for PKCS library

        for (const pathnameToTry of commonSoftHSMPathNames) {
            if (fs.existsSync(pathnameToTry)) {
                pkcsLibPath = pathnameToTry;
                break;
            }
        }
    }

    return pkcsLibPath;
}

/**
 * Interact with the Fabric Network via the Contact with the required transaction to perform.
 * @param contract The contract to send the transaction to
 * @param transactionToPerform the transaction to perform
 */
async function interactWithFabric(contract: Contract, transactionToPerform: TransactionToSendFormat): Promise<void> {
    console.log(transactionToPerform.description);
    try {
        switch (transactionToPerform.request) {
            case 'submit': {
                await contract.submitTransaction(transactionToPerform.txName, ...transactionToPerform.txArgs);
                console.log('*** Result: committed');
                break;
            }

            case 'evaluate': {
                const result = await contract.evaluateTransaction(transactionToPerform.txName, ...transactionToPerform.txArgs);
                console.log(`*** Result: ${prettyJSONString(result.toString())}`);
            }
        }
    } catch (error) {
        console.log(`*** Successfully caught the error: \n    ${error}`);
        // In reality applications should check the returned error to decide whether the transaction needs to be retried (ie go through
        // endorsement again) for example an MVCC_READ_CONFLICT error, resubmitted to the orderer for example a timeout because it was
        // never committed (for example due to networking issues the transaction never gets included in a block), or whether it should
        // be reported back, for example they tried to perform an invalid application action.
    }
}

/**
 * envOrDefault() will return the value of an environment variable, or a default value if the variable is undefined.
 */
function envOrDefault(key: string, defaultValue: string): string {
    return process.env[key] || defaultValue;
}

// execute the main function
main();
