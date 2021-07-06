/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import * as fs from 'fs';
import * as path from 'path';

const buildCCPOrg1 = (): Record<string, any> => {
    // load the common connection configuration file
    const ccpPath = path.resolve(__dirname, '..', '..', '..', '..', 'test-network',
        'organizations', 'peerOrganizations', 'org1.example.com', 'connection-org1.json');
    const fileExists = fs.existsSync(ccpPath);
    if (!fileExists) {
        throw new Error(`no such file or directory: ${ccpPath}`);
    }
    const contents = fs.readFileSync(ccpPath, 'utf8');

    // build a JSON object from the file contents
    const ccp = JSON.parse(contents);

    console.log(`Loaded the network configuration located at ${ccpPath}`);
    return ccp;
};

const buildCCPOrg2 = (): Record<string, any> => {
    // load the common connection configuration file
    const ccpPath = path.resolve(__dirname, '..', '..', '..', '..', 'test-network',
        'organizations', 'peerOrganizations', 'org2.example.com', 'connection-org2.json');
    const fileExists = fs.existsSync(ccpPath);
    if (!fileExists) {
        throw new Error(`no such file or directory: ${ccpPath}`);
    }
    const contents = fs.readFileSync(ccpPath, 'utf8');

    // build a JSON object from the file contents
    const ccp = JSON.parse(contents);

    console.log(`Loaded the network configuration located at ${ccpPath}`);
    return ccp;
};

const prettyJSONString = (inputString: string): string => {
    if (inputString) {
         return JSON.stringify(JSON.parse(inputString), null, 2);
    } else {
         return inputString;
    }
};

export {
    buildCCPOrg1,
    buildCCPOrg2,
    prettyJSONString,
};
