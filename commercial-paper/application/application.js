/*
SPDX-License-Identifier: Apache-2.0
*/

/*
 * This application has 6 basic steps:
 * 1. Select an identity from a wallet
 * 2. Connect to network gateway
 * 3. Access PaperNet network
 * 4. Construct request to issue commercial paper
 * 5. Submit transaction
 * 6. Process response
 */

'use strict';

// Bring key classes into scope, most importantly Fabric SDK network class
const file = require("fs");
const yaml = require('js-yaml');
const { FileSystemWallet, Gateway } = require('fabric-network');
const { CommercialPaper } = require('./paper.js');

// A wallet stores a collection of identities for use
const wallet = new FileSystemWallet('./wallet');

// A gateway defines the peers used to access Fabric networks
const gateway = new Gateway();

// Main try/catch/finally block
try {

  // Load connection profile; will be used to locate a gateway
  connectionProfile = yaml.safeLoad(file.readFileSync('./gateway/connectionProfile.yaml', 'utf8'));

  // Set connection options; use 'admin' identity from application wallet
  let connectionOptions = {
    identity: 'isabella.the.issuer@magnetocorp.com',
    wallet: wallet,
    commitTimeout: 100,
    strategy: MSPID_SCOPE_ANYFORTX,
    commitNotifyStrategy: WAIT_FOR_ALL_CHANNEL_PEER
  }

  // Connect to gateway using application specified parameters
  await gateway.connect(connectionProfile, connectionOptions);

  console.log('Connected to Fabric gateway.')

  // Get addressability to PaperNet network
  const network = await gateway.getNetwork('PaperNet');

  // Get addressability to commercial paper contract
  const contract = await network.getContract('papercontract', 'org.papernet.commercialpaper');

  console.log('Submit commercial paper issue transaction.')

  // issue commercial paper
  const response = await contract.submitTransaction('issue', 'MagnetoCorp', '00001', '2020-05-31', '2020-11-30', '5000000');

  let paper = CommercialPaper.deserialize(response);

  console.log(`${paper.issuer} commercial paper : ${paper.paperNumber} successfully issued for value ${paper.faceValue}`);

  console.log('Transaction complete.')

} catch (error) {

  console.log(`Error processing transaction. ${error}`);

} finally {

  // Disconnect from the gateway
  console.log('Disconnect from Fabric gateway.')
  gateway.disconnect();

}