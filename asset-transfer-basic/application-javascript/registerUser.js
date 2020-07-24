/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

const { Wallets } = require('fabric-network');
const FabricCAServices = require('fabric-ca-client');
const fs = require('fs');
const path = require('path');
const enrollAdmin = require('./enrollAdmin');
const caChaincodeUserRole = 'client';
const applicationUserId = 'appUser';
const walletPath = path.join(__dirname, 'wallet');

async function registerAppUser() {
    try {
        // load the network configuration
        const ccpPath = path.resolve(__dirname, '..', '..', 'test-network', 'organizations', 'peerOrganizations', 'org1.example.com', 'connection-org1.json');
        const fileExists = fs.existsSync(ccpPath);
        if (!fileExists) {
            throw new Error(`no such file or directory: ${ccpPath}`);
        }
        const ccp = JSON.parse(fs.readFileSync(ccpPath, 'utf8'));

        // Create a new CA client for interacting with the CA.
        const caURL = ccp.certificateAuthorities['ca.org1.example.com'].url;
        const ca = new FabricCAServices(caURL);

        // Create a new file system based wallet for managing identities.        ;
        const wallet = await Wallets.newFileSystemWallet(walletPath);

        // Check to see if we've already enrolled the user.
        const userIdentity = await wallet.get(applicationUserId);
        if (userIdentity) {
            console.log(`An identity for the user ${applicationUserId} already exists in the wallet`);
            return;
        }

        // Check to see if we've already enrolled the admin user.
        const adminIdentity = await wallet.get(enrollAdmin.AdminUserId);
        if (!adminIdentity) {
            console.log('An identity for the admin user does not exist in the wallet');
            console.log('Run the enrollAdmin.js application before retrying');
            return;
        }

        // build a user object for authenticating with the CA
        const provider = wallet.getProviderRegistry().getProvider(adminIdentity.type);
        const adminUser = await provider.getUserContext(adminIdentity, enrollAdmin.AdminUserId);

        // Register the user, enroll the user, and import the new identity into the wallet.
        // if affiliation is specified by client, the affiliation value must be configured in CA
        const secret = await ca.register({
            affiliation: 'org1.department1',
            enrollmentID: applicationUserId,
            role: caChaincodeUserRole
        }, adminUser);
        const enrollment = await ca.enroll({
            enrollmentID: applicationUserId,
            enrollmentSecret: secret
        });
        const x509Identity = {
            credentials: {
                certificate: enrollment.certificate,
                privateKey: enrollment.key.toBytes(),
            },
            mspId: 'Org1MSP',
            type: 'X.509',
        };
        await wallet.put(applicationUserId, x509Identity);
        console.log(`Successfully registered and enrolled user ${applicationUserId} and imported it into the wallet`);

    } catch (error) {
        console.error(`Failed to register user : ${error}`);
        process.exit(1);
    }
}

exports.ApplicationUserId = applicationUserId;
exports.RegisterAppUser = registerAppUser;
