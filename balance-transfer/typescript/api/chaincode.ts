/**
 * Copyright 2017 Kapil Sachdeva All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import * as express from 'express';
import log4js = require('log4js');
const logger = log4js.getLogger('SampleWebApp');
import hfc = require('fabric-client');
import * as jwt from 'jsonwebtoken';
import * as helper from '../lib/helper';
import * as channelApi from '../lib/channel';
import * as chainCodeApi from '../lib/chaincode';
import { RequestEx } from '../interfaces';
import { getErrorMessage } from './utils';

export default function chainCodeHandlers(app: express.Application) {

    async function installChainCode(req: RequestEx, res: express.Response) {
        logger.debug('==================== INSTALL CHAINCODE ==================');

        const peers = req.body.peers;
        const chaincodeName = req.body.chaincodeName;
        const chaincodePath = req.body.chaincodePath;
        const chaincodeVersion = req.body.chaincodeVersion;

        logger.debug('peers : ' + peers); // target peers list
        logger.debug('chaincodeName : ' + chaincodeName);
        logger.debug('chaincodePath  : ' + chaincodePath);
        logger.debug('chaincodeVersion  : ' + chaincodeVersion);

        if (!peers || peers.length === 0) {
            res.json(getErrorMessage('\'peers\''));
            return;
        }
        if (!chaincodeName) {
            res.json(getErrorMessage('\'chaincodeName\''));
            return;
        }
        if (!chaincodePath) {
            res.json(getErrorMessage('\'chaincodePath\''));
            return;
        }
        if (!chaincodeVersion) {
            res.json(getErrorMessage('\'chaincodeVersion\''));
            return;
        }

        const message = await chainCodeApi.installChaincode(
            peers, chaincodeName, chaincodePath, chaincodeVersion, req.username, req.orgname);

        res.send(message);
    }

    async function queryChainCode(req: RequestEx, res: express.Response) {
        const peer = req.query.peer;
        const installType = req.query.type;
        // TODO: add Constnats
        if (installType === 'installed') {
            logger.debug(
                '================ GET INSTALLED CHAINCODES ======================');
        } else {
            logger.debug(
                '================ GET INSTANTIATED CHAINCODES ======================');
        }

        const message = await chainCodeApi.getInstalledChaincodes(
            peer, installType, req.username, req.orgname);

        res.send(message);
    }

    const API_ENDPOINT_CHAINCODE_INSTALL = '/chaincodes';
    const API_ENDPOINT_CHAINCODE_QUERY = '/chaincodes';

    app.post(API_ENDPOINT_CHAINCODE_INSTALL, installChainCode);
    app.get(API_ENDPOINT_CHAINCODE_QUERY, queryChainCode);
}
