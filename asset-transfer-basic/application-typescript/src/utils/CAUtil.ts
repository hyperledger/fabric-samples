/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import * as FabricCAServices from 'fabric-ca-client';
import { Wallet } from 'fabric-network';

const adminUserId = 'admin';
const adminUserPasswd = 'adminpw';

/**
 *
 * @param {*} ccp
 */
const buildCAClient = (ccp: Record<string, any>, caHostName: string): FabricCAServices => {
    // Create a new CA client for interacting with the CA.
    const caInfo = ccp.certificateAuthorities[caHostName]; // lookup CA details from config
    const caTLSCACerts = caInfo.tlsCACerts.pem;
    const caClient = new FabricCAServices(caInfo.url, { trustedRoots: caTLSCACerts, verify: false }, caInfo.caName);

    console.log(`Built a CA Client named ${caInfo.caName}`);
    return caClient;
};

const enrollAdmin = async (caClient: FabricCAServices, wallet: Wallet, orgMspId: string): Promise<void> => {
    try {
        // Check to see if we've already enrolled the admin user.
        const identity = await wallet.get(adminUserId);
        if (identity) {
            console.log('An identity for the admin user already exists in the wallet');
            return;
        }

        // Enroll the admin user, and import the new identity into the wallet.
        const enrollment = await caClient.enroll({ enrollmentID: adminUserId, enrollmentSecret: adminUserPasswd });
        const x509Identity = {
            credentials: {
                certificate: enrollment.certificate,
                privateKey: enrollment.key.toBytes(),
            },
            mspId: orgMspId,
            type: 'X.509',
        };
        await wallet.put(adminUserId, x509Identity);
        console.log('Successfully enrolled admin user and imported it into the wallet');
    } catch (error) {
        console.error(`Failed to enroll admin user : ${error}`);
    }
};

const registerAndEnrollUser = async (caClient: FabricCAServices, wallet: Wallet, orgMspId: string, userId: string, affiliation: string): Promise<void> => {
    try {
        // Check to see if we've already enrolled the user
        const userIdentity = await wallet.get(userId);
        if (userIdentity) {
            console.log(`An identity for the user ${userId} already exists in the wallet`);
            return;
        }

        // Must use an admin to register a new user
        const adminIdentity = await wallet.get(adminUserId);
        if (!adminIdentity) {
            console.log('An identity for the admin user does not exist in the wallet');
            console.log('Enroll the admin user before retrying');
            return;
        }

        // build a user object for authenticating with the CA
        const provider = wallet.getProviderRegistry().getProvider(adminIdentity.type);
        const adminUser = await provider.getUserContext(adminIdentity, adminUserId);

        // Register the user, enroll the user, and import the new identity into the wallet.
        // if affiliation is specified by client, the affiliation value must be configured in CA
        const secret = await caClient.register({
            affiliation,
            enrollmentID: userId,
            role: 'client',
        }, adminUser);
        const enrollment = await caClient.enroll({
            enrollmentID: userId,
            enrollmentSecret: secret,
        });
        const x509Identity = {
            credentials: {
                certificate: enrollment.certificate,
                privateKey: enrollment.key.toBytes(),
            },
            mspId: orgMspId,
            type: 'X.509',
        };
        await wallet.put(userId, x509Identity);
        console.log(`Successfully registered and enrolled user ${userId} and imported it into the wallet`);
    } catch (error) {
        console.error(`Failed to register user : ${error}`);
    }
};

export {
    buildCAClient,
    enrollAdmin,
    registerAndEnrollUser,
};
