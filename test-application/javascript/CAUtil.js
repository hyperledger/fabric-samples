/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

	// provider types
const HSM_PROVIDER = 'HSM-X.509';
const X509_PROVIDER = 'X.509';

const adminUserId = 'admin';
const adminUserPasswd = 'adminpw';

/**
 *
 * @param {*} FabricCAServices
 * @param {*} ccp
 */
exports.buildCAClient = (FabricCAServices, ccp, caHostName, cryptoSuite) => {
	// Create a new CA client for interacting with the CA.
	const caInfo = ccp.certificateAuthorities[caHostName]; //lookup CA details from config
	const caTLSCACerts = caInfo.tlsCACerts.pem;
	const caClient = new FabricCAServices(caInfo.url, { trustedRoots: caTLSCACerts, verify: false }, caInfo.caName, cryptoSuite);

	console.log(`Built a CA Client named ${caInfo.caName}`);
	return caClient;
};

exports.enrollAdmin = async (caClient, wallet, orgMspId) => {
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
			type: X509_PROVIDER,
		};
		await wallet.put(adminUserId, x509Identity);
		console.log('Successfully enrolled admin user and imported it into the wallet');
	} catch (error) {
		console.error(`Failed to enroll admin user : ${error}`);
	}
};

exports.registerAndEnrollUser = async (nonHSMcaClient, wallet, orgMspId, userId, affiliation, hsmCaClient) => {
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
		const provider = wallet.getProviderRegistry().getProvider(X509_PROVIDER);
		const adminUser = await provider.getUserContext(adminIdentity, adminUserId);

		// Register the user, enroll the user, and import the new identity into the wallet.
		// if affiliation is specified by client, the affiliation value must be configured in CA
		let caClient = nonHSMcaClient;
		let type = X509_PROVIDER;

		if (hsmCaClient) {
			// Will use the HSM CA client which has been initialized with a pkcs11 crypto suite
			// to work the HSM for generating keys and signing.
			caClient = hsmCaClient;
			type = HSM_PROVIDER;
			console.log(' ---> Using HSM identity');
		}

		const secret = await caClient.register({
			affiliation: affiliation,
			enrollmentID: userId,
			role: 'client'
		}, adminUser);
		const enrollment = await caClient.enroll({
			enrollmentID: userId,
			enrollmentSecret: secret
		});

		const x509Identity = {
			mspId: orgMspId,
			type: type,
			credentials: {
				certificate: enrollment.certificate,
				privateKey: enrollment.key.toBytes()
			}
		}

		await wallet.put(userId, x509Identity);
		console.log(`Successfully registered and enrolled user ${userId} and imported it into the wallet`);
	} catch (error) {
		console.error(`Failed to register user : ${error}`);
	}
};

exports.getHSMLibPath = (fs) => {
	const pathnames = [
		'/usr/lib/softhsm/libsofthsm2.so', // Ubuntu
		'/usr/lib/x86_64-linux-gnu/softhsm/libsofthsm2.so', // Ubuntu  apt-get install
		'/usr/local/lib/softhsm/libsofthsm2.so', // Ubuntu, OSX (tar ball install)
		'/usr/lib/libacsp-pkcs11.so' // LinuxOne
	];
	let pkcsLibPath = 'NOT FOUND';
	if (typeof process.env.PKCS11_LIB === 'string' && process.env.PKCS11_LIB !== '') {
		pkcsLibPath  = process.env.PKCS11_LIB;
	} else {
		//
		// Check common locations for PKCS library
		//
		for (let i = 0; i < pathnames.length; i++) {
			if (fs.existsSync(pathnames[i])) {
				pkcsLibPath = pathnames[i];
				break;
			}
		}
	}

	return pkcsLibPath;
}
