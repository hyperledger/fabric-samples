/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

/*

blockEventListener.js is an nodejs application to listen for block events from
a specified channel.

Configuration is stored in config.json:

{
   "peer_name": "peer0.org1.example.com",
   "channelid": "mychannel",
   "use_couchdb":false,
   "couchdb_address": "http://localhost:5990"
}

peer_name:  target peer for the listener
channelid:  channel name for block events
use_couchdb:  if set to true, events will be stored in a local couchdb
couchdb_address:  local address for an off chain couchdb database

Note:  If use_couchdb is set to false, only a local log of events will be stored.

Usage:

node bockEventListener.js

The block event listener will log events received to the console and write event blocks to
a log file based on the channelid and chaincode name.

The event listener stores the next block to retrieve in a file named nextblock.txt.  This file
is automatically created and initialized to zero if it does not exist.

*/

'use strict';

const { Wallets, Gateway } = require('fabric-network');
const fs = require('fs');
const path = require('path');

const couchdbutil = require('./couchdbutil.js');
const blockProcessing = require('./blockProcessing.js');

const config = require('./config.json');
const channelid = config.channelid;
const peer_name = config.peer_name;
const use_couchdb = config.use_couchdb;
const couchdb_address = config.couchdb_address;

const configPath = path.resolve(__dirname, 'nextblock.txt');

const nano = require('nano')(couchdb_address);

// simple map to hold blocks for processing
class BlockMap {
    constructor() {
        this.list = []
    }
    get(key) {
        key = parseInt(key, 10).toString();
        return this.list[`block${key}`];
    }
    set(key, value) {
        this.list[`block${key}`] = value;
    }
    remove(key) {
        key = parseInt(key, 10).toString();
        delete this.list[`block${key}`];
    }
}

let ProcessingMap = new BlockMap()

async function main() {
    try {

        // initialize the next block to be 0
        let nextBlock = 0;

        // check to see if there is a next block already defined
        if (fs.existsSync(configPath)) {
            // read file containing the next block to read
            nextBlock = fs.readFileSync(configPath, 'utf8');
        } else {
            // store the next block as 0
            fs.writeFileSync(configPath, parseInt(nextBlock, 10))
        }

        // Create a new file system based wallet for managing identities.
        const walletPath = path.join(process.cwd(), 'wallet');
        const wallet = await Wallets.newFileSystemWallet(walletPath);
        console.log(`Wallet path: ${walletPath}`);

        // Check to see if we've already enrolled the user.
        const userExists = await wallet.get('appUser');
        if (!userExists) {
            console.log('An identity for the user "appUser" does not exist in the wallet');
            console.log('Run the enrollUser.js application before retrying');
            return;
        }

        // Parse the connection profile. This would be the path to the file downloaded
        // from the IBM Blockchain Platform operational console.
        const ccpPath = path.resolve(__dirname, '..', 'test-network','organizations','peerOrganizations','org1.example.com', 'connection-org1.json');
        const ccp = JSON.parse(fs.readFileSync(ccpPath, 'utf8'));
        // Create a new gateway for connecting to our peer node.
        const gateway = new Gateway();
        await gateway.connect(ccp, { wallet, identity: 'appUser', discovery: { enabled: true, asLocalhost: true } });

        // Get the network (channel) our contract is deployed to.
        const network = await gateway.getNetwork('mychannel');

        const listener = await network.addBlockListener(
            async (err, blockNum, block) => {
                if (err) {
                    console.error(err);
                    return;
                }
                // Add the block to the processing map by block number
                await ProcessingMap.set(block.header.number, block);

                console.log(`Added block ${blockNum} to ProcessingMap`)
            },
            // set the starting block for the listener
            { filtered: false, startBlock: parseInt(nextBlock, 10) }
        );

        console.log(`Listening for block events, nextblock: ${nextBlock}`);

        // start processing, looking for entries in the ProcessingMap
        processPendingBlocks(ProcessingMap);

    } catch (error) {
        console.error(`Failed to evaluate transaction: ${error}`);
        process.exit(1);
    }
}

// listener function to check for blocks in the ProcessingMap
async function processPendingBlocks(ProcessingMap) {

    setTimeout(async () => {

        // get the next block number from nextblock.txt
        let nextBlockNumber = fs.readFileSync(configPath, 'utf8');
        let processBlock;

        do {

            // get the next block to process from the ProcessingMap
            processBlock = ProcessingMap.get(nextBlockNumber)

            if (processBlock == undefined) {
                break;
            }

            try {
                await blockProcessing.processBlockEvent(channelid, processBlock, use_couchdb, nano)
            } catch (error) {
                console.error(`Failed to process block: ${error}`);
            }

            // if successful, remove the block from the ProcessingMap
            ProcessingMap.remove(nextBlockNumber);

            // increment the next block number to the next block
            fs.writeFileSync(configPath, parseInt(nextBlockNumber, 10) + 1)

            // retrive the next block number to process
            nextBlockNumber = fs.readFileSync(configPath, 'utf8');

        } while (true);

        processPendingBlocks(ProcessingMap);

    }, 250);

}

main();
