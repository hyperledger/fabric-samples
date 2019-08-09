/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 * 
 */

'use strict';

const fs = require('fs');
const path = require('path');

const couchdbutil = require('./couchdbutil.js');

const configPath = path.resolve(__dirname, 'nextblock.txt');

exports.processBlockEvent = async function (channelname, block, use_couchdb, nano) {

    return new Promise((async (resolve, reject) => {

        // reject the block if the block number is not defined
        if (block.header.number == undefined) {
            reject(new Error('Undefined block number'));
        }

        const blockNumber = block.header.number

        console.log(`------------------------------------------------`);
        console.log(`Block Number: ${blockNumber}`);

        // reject if the data is not set
        if (block.data.data == undefined) {
            reject(new Error('Data block is not defined'));
        }

        const dataArray = block.data.data;

        for (var dataItem in dataArray) {

            // reject if a timestamp is not set
            if (dataArray[dataItem].payload.header.channel_header.timestamp == undefined) {
                reject(new Error('Block timestamp is not defined'));
            }

            const timestamp = dataArray[dataItem].payload.header.channel_header.timestamp;

            // reject if no actions are set
            if (dataArray[dataItem].payload.data.actions == undefined) {
                break;
            }

            const actions = dataArray[dataItem].payload.data.actions;

            // iterate through all actions
            for (var actionItem in actions) {

                // reject if a chaincode id is not defined
                if (actions[actionItem].payload.chaincode_proposal_payload.input.chaincode_spec.chaincode_id.name == undefined) {
                    reject(new Error('Chaincode name is not defined'));
                }

                const chaincodeID = actions[actionItem].payload.chaincode_proposal_payload.input.chaincode_spec.chaincode_id.name

                // reject if there is no readwrite set
                if (actions[actionItem].payload.action.proposal_response_payload.extension.results.ns_rwset == undefined) {
                    reject(new Error('No readwrite set is defined'));
                }

                const rwSet = actions[actionItem].payload.action.proposal_response_payload.extension.results.ns_rwset

                for (var record in rwSet) {

                    // ignore lscc events
                    if (rwSet[record].namespace != 'lscc') {
                        // create object to store properties
                        const writeObject = new Object();
                        writeObject.blocknumber = blockNumber;
                        writeObject.chaincodeid = chaincodeID;
                        writeObject.timestamp = timestamp;
                        writeObject.values = rwSet[record].rwset.writes;

                        console.log(`Block Timestamp: ${writeObject.timestamp}`);
                        console.log(`ChaincodeID: ${writeObject.chaincodeid}`);
                        console.log(writeObject.values);

                        const logfilePath = path.resolve(__dirname, 'nextblock.txt');

                        // send the object to a log file
                        fs.appendFileSync(channelname + '_' + chaincodeID + '.log', JSON.stringify(writeObject) + "\n");

                        // if couchdb is configured, then write to couchdb
                        if (use_couchdb) {
                            try {
                                await writeValuesToCouchDBP(nano, channelname, writeObject);
                            } catch (error) {

                            }
                        }
                    }
                };
            };
        };

        // update the nextblock.txt file to retrieve the next block
        fs.writeFileSync(configPath, parseInt(blockNumber, 10) + 1)

        resolve(true);

    }));
}

async function writeValuesToCouchDBP(nano, channelname, writeObject) {

    return new Promise((async (resolve, reject) => {

        try {

            // define the database for saving block events by key - this emulates world state
            const dbname = channelname + '_' + writeObject.chaincodeid;
            // define the database for saving all block events - this emulates history
            const historydbname = channelname + '_' + writeObject.chaincodeid + '_history';
            // set values to the array of values received
            const values = writeObject.values;

            try {
                for (var sequence in values) {
                    let keyvalue =
                        values[
                        sequence
                        ];

                    if (
                        keyvalue.is_delete ==
                        true
                    ) {
                        await couchdbutil.deleteRecord(
                            nano,
                            dbname,
                            keyvalue.key
                        );
                    } else {
                        if (
                            isJSON(
                                keyvalue.value
                            )
                        ) {
                            //  insert or update value by key - this emulates world state behavior
                            await couchdbutil.writeToCouchDB(
                                nano,
                                dbname,
                                keyvalue.key,
                                JSON.parse(
                                    keyvalue.value
                                )
                            );
                        }
                    }

                    // add additional fields for history
                    keyvalue.timestamp =
                        writeObject.timestamp;
                    keyvalue.blocknumber = parseInt(
                        writeObject.blocknumber,
                        10
                    );
                    keyvalue.sequence = parseInt(
                        sequence,
                        10
                    );

                    await couchdbutil.writeToCouchDB(
                        nano,
                        historydbname,
                        null,
                        keyvalue
                    );
                }
            } catch (error) {
                console.log(error);
                reject(error);
            }

        } catch (error) {
            console.error(`Failed to write to couchdb: ${error}`);
            reject(error);
        }

        resolve(true);

    }));

}

function isJSON(value) {
    try {
        JSON.parse(value);
    } catch (e) {
        return false;
    }
    return true;
}