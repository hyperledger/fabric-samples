/*
 *  SPDX-License-Identifier: Apache-2.0
 */

'use strict';

// Bring key classes into scope, most importantly Fabric SDK network class
const fs = require('fs');
const { Wallets } = require('fabric-network');
const path = require('path');

<<<<<<< HEAD:library-tracker/organization/cannavino/application/addToWallet.js
const fixtures = path.resolve(__dirname, '../../../../basic-network');

// A wallet stores a collection of identities
const wallet = new FileSystemWallet('../identity/user/balaji/wallet');
=======
const fixtures = path.resolve(__dirname, '../../../../test-network');
>>>>>>> 3dbe116a30d517e1e828afb61b2198763141f2e6:commercial-paper/organization/magnetocorp/application/addToWallet.js

async function main() {

    // Main try/catch block
    try {
        // A wallet stores a collection of identities
        const wallet = await Wallets.newFileSystemWallet('../identity/user/isabella/wallet');

        // Identity to credentials to be stored in the wallet
<<<<<<< HEAD:library-tracker/organization/cannavino/application/addToWallet.js
        const credPath = path.join(fixtures, '/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com');
        const cert = fs.readFileSync(path.join(credPath, '/msp/signcerts/Admin@org1.example.com-cert.pem')).toString();
        const key = fs.readFileSync(path.join(credPath, '/msp/keystore/cd96d5260ad4757551ed4a5a991e62130f8008a0bf996e4e4b84cd097a747fec_sk')).toString();

        // Load credentials into wallet
        const identityLabel = 'Admin@org1.example.com';
        const identity = X509WalletMixin.createIdentity('Org1MSP', cert, key);

        await wallet.import(identityLabel, identity);
=======
        const credPath = path.join(fixtures, '/organizations/peerOrganizations/org2.example.com/users/User1@org2.example.com');
        const certificate = fs.readFileSync(path.join(credPath, '/msp/signcerts/User1@org2.example.com-cert.pem')).toString();
        const privateKey = fs.readFileSync(path.join(credPath, '/msp/keystore/priv_sk')).toString();

        // Load credentials into wallet
        const identityLabel = 'isabella';

        const identity = {
            credentials: {
                certificate,
                privateKey
            },
            mspId: 'Org2MSP',
            type: 'X.509'
        }

    
        await wallet.put(identityLabel,identity);
>>>>>>> 3dbe116a30d517e1e828afb61b2198763141f2e6:commercial-paper/organization/magnetocorp/application/addToWallet.js

    } catch (error) {
        console.log(`Error adding to wallet. ${error}`);
        console.log(error.stack);
    }
}

main().then(() => {
    console.log('done');
}).catch((e) => {
    console.log(e);
    console.log(e.stack);
    process.exit(-1);
});