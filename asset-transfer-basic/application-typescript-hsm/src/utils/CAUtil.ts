/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import * as FabricCAServices from 'fabric-ca-client';
import { Wallet } from 'fabric-network';

export interface UserToEnroll {
    caClient: FabricCAServices;
    wallet: Wallet;
    orgMspId: string;
    userId: string;
    userIdSecret: string;
}

/**
 * enroll a registered CA user and store the credentials in the wallet
 * @param userToEnroll details about the user and the wallet to use
 */
export const enrollUserToWallet = async (userToEnroll: UserToEnroll): Promise<void> => {
    try {

        // check that the identity isn't already in the wallet
        const identity = await userToEnroll.wallet.get(userToEnroll.userId);
        if (identity) {
            console.log(`Identity ${userToEnroll.userId} already exists in the wallet`);
            return;
        }

        // Enroll the user
        const enrollment = await userToEnroll.caClient.enroll({ enrollmentID: userToEnroll.userId, enrollmentSecret: userToEnroll.userIdSecret });

        // store the user
        const hsmIdentity = {
            credentials: {
                certificate: enrollment.certificate,
            },
            mspId: userToEnroll.orgMspId,
            type: 'HSM-X.509',
        };
        await userToEnroll.wallet.put(userToEnroll.userId, hsmIdentity);
        console.log(`Successfully enrolled user ${userToEnroll.userId} and imported it into the wallet`);
    } catch (error) {
        console.error(`Failed to enroll user ${userToEnroll.userId}: ${error}`);
    }
};

export interface UserToRegister {
    caClient: FabricCAServices;
    wallet: Wallet;
    orgMspId: string;
    adminId: string;
    userId: string;
    userIdSecret: string;
    affiliation: string;
}

export const registerUser = async (userToRegister: UserToRegister): Promise<void> => {
    try {

        // Must use a CA admin (registrar) to register a new user
        const adminIdentity = await userToRegister.wallet.get(userToRegister.adminId);
        if (!adminIdentity) {
            console.log('An identity for the admin user does not exist in the wallet');
            console.log('Enroll the admin user before retrying');
            return;
        }

        // build a user object for authenticating with the CA
        const provider = userToRegister.wallet.getProviderRegistry().getProvider(adminIdentity.type);
        const adminUser = await provider.getUserContext(adminIdentity, userToRegister.adminId);

        // Register the user
        // if affiliation is specified by client, the affiliation value must be configured in CA
        await userToRegister.caClient.register({
            affiliation: userToRegister.affiliation,
            enrollmentID: userToRegister.userId,
            enrollmentSecret: userToRegister.userIdSecret,
            role: 'client',
        }, adminUser);
        console.log(`Successfully registered ${userToRegister.userId}.`);
        return;

    } catch (error) {
        // check to see if it's an already registered error, if it is, then we can ignore it
        // otherwise we rethrow the error
        if (error.errors[0].code !== 74) {
            console.error(`Failed to register user : ${error}`);
            throw error;
        }
    }
};
