/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

const adminUserId = 'admin';
const adminUserPasswd = 'adminpw';
const org1UserId = 'appUser1';
const org2UserId = 'appUser2';
const caChaincodeUserRole = 'client';

async function registerOrgUser(appUserId, mspId, wallet, caService) {
    try {
        // Check to see if we've already enrolled the user.
        const userIdentity = await wallet.get(appUserId);
        if (userIdentity) {
            console.log('An identity for the user ' + appUserId + ' already exists in the wallet');
            return;
        }

        // Check to see if we've already enrolled the admin user.
        const adminIdentity = await wallet.get(adminUserId);
        if (!adminIdentity) {
            console.log('An identity for the admin user does not exist in the wallet');
            console.log('Call enrollAdmin for admin user enroll before retrying');
            return;
        }

        // build a user object for authenticating with the CA
        const provider = wallet.getProviderRegistry().getProvider(adminIdentity.type);
        const adminUser = await provider.getUserContext(adminIdentity, adminUserId);

        // Register the user, enroll the user, and import the new identity into the wallet.
        // if affiliation is specified by client, the affiliation value must be configured in CA
        const secret = await caService.register({
            affiliation: 'org2.department1',
            enrollmentID: appUserId,
            role: caChaincodeUserRole
        }, adminUser);
        const enrollment = await caService.enroll({
            enrollmentID: appUserId,
            enrollmentSecret: secret
        });
        const x509Identity = {
            credentials: {
                certificate: enrollment.certificate,
                privateKey: enrollment.key.toBytes(),
            },
            mspId: mspId,
            type: 'X.509',
        };
        await wallet.put(appUserId, x509Identity);
        console.log('Successfully registered and enrolled user ' + appUserId + ' and imported it into the wallet');

    } catch (error) {
        console.error(`Failed to register user : ${error}`);
        process.exit(1);
    }
}

async function enrollOrgAdminUser(mspId, wallet, caService) {
    try {

        // Check to see if we've already enrolled the admin user.
        const identity = await wallet.get(adminUserId);
        if (identity) {
            console.log('An identity for the admin user already exists in the wallet');
            return;
        }

        // Enroll the admin user, and import the new identity into the wallet.
        const enrollment = await caService.enroll({ enrollmentID: adminUserId, enrollmentSecret: adminUserPasswd });
        const x509Identity = {
            credentials: {
                certificate: enrollment.certificate,
                privateKey: enrollment.key.toBytes(),
            },
            mspId: mspId,
            type: 'X.509',
        };
        await wallet.put(adminUserId, x509Identity);
        console.log('Successfully enrolled admin user and imported it into the wallet');

    } catch (error) {
        console.error(`Failed to enroll admin user : ${error}`);
        process.exit(1);
    }
}

exports.AdminUserId = adminUserId;
exports.Org1UserId = org1UserId;
exports.Org2UserId = org2UserId;
exports.RegisterOrgUser = registerOrgUser;
exports.EnrollOrgAdminUser = enrollOrgAdminUser;