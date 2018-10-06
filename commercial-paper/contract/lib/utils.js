/*
SPDX-License-Identifier: Apache-2.0
*/

'use strict';

/**
 * Utility class for data, object mapulation, e.g. serialization
 */

class Utils {

    /**
     * Convert object to buffer containing JSON data serialization
     * Typically used before putState() ledger API
     * @param {Object} object object to serialize
     * @return {buffer} buffer with the data to store
     */
    static serialize(object){
        return Buffer.from(JSON.stringify(object));
    }

    /**
     * Deserialize object, i.e. Covert serialized data to JSON object
     * Typically used after getState() ledger API
     * @param {Object} data object to deserialize
     * @return {json} json with the data to store
     */
    static deserialize(data){
        return JSON.parse(data);
    }
}

module.exports = Utils;
