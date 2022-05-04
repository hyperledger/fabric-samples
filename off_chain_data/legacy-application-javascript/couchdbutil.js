/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 * 
 */

'use strict';

exports.createDatabaseIfNotExists = function (nano, dbname) {

    return new Promise((async (resolve, reject) => {
        await nano.db.get(dbname, async function (err, body) {
            if (err) {
                if (err.statusCode == 404) {
                    await nano.db.create(dbname, function (err, body) {
                        if (!err) {
                            resolve(true);
                        } else {
                            reject(err);
                        }
                    });
                } else {
                    reject(err);
                }
            } else {
                resolve(true);
            }
        });
    }));
}

exports.writeToCouchDB = async function (nano, dbname, key, value) {

    return new Promise((async (resolve, reject) => {

        try {
            await this.createDatabaseIfNotExists(nano, dbname);
        } catch (error) {
            console.log("Error creating the database-"+error)
        }

        const db = nano.use(dbname);

        // If a key is not specified, then this is an insert
        if (key == null) {
            db.insert(value, async function (err, body, header) {
                if (err) {
                    reject(err);
                }
            }
            );
        } else {

            // If a key is specified, then attempt to retrieve the record by key
            db.get(key, async function (err, body) {
                // parse the value
                const updateValue = value;
                // if the record was found, then update the revision to allow the update
                if (err == null) {
                    updateValue._rev = body._rev
                }
                // update or insert the value
                db.insert(updateValue, key, async function (err, body, header) {
                    if (err) {
                        reject(err);
                    }
                });
            });
        }

        resolve(true);

    }));
}


exports.deleteRecord = async function (nano, dbname, key) {

    return new Promise((async (resolve, reject) => {

        try {
            await this.createDatabaseIfNotExists(nano, dbname);
        } catch (error) {
            console.log("Error creating the database-"+error)
        }

        const db = nano.use(dbname);

        // If a key is specified, then attempt to retrieve the record by key
        db.get(key, async function (err, body) {

            // if the record was found, then update the revision to allow the update
            if (err == null) {

                let revision = body._rev

                // update or insert the value
                db.destroy(key, revision, async function (err, body, header) {
                    if (err) {
                        reject(err);
                    }
                });

            }
        });

        resolve(true);

    }));
}
