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
import { RequestEx } from '../interfaces';
import { getErrorMessage } from './utils';

export default function channelHandlers(app: express.Application) {

    async function createNewChannel(req: RequestEx, res: express.Response) {
        logger.info('<<<<<<<<<<<<<<<<< C R E A T E  C H A N N E L >>>>>>>>>>>>>>>>>');
        logger.debug('End point : /channels');

        const channelName = req.body.channelName;
        const channelConfigPath = req.body.channelConfigPath;

        logger.debug('Channel name : ' + channelName);
        // ../artifacts/channel/mychannel.tx
        logger.debug('channelConfigPath : ' + channelConfigPath);

        if (!channelName) {
            res.json(getErrorMessage('\'channelName\''));
            return;
        }
        if (!channelConfigPath) {
            res.json(getErrorMessage('\'channelConfigPath\''));
            return;
        }

        const response = await channelApi.createChannel(
            channelName, channelConfigPath, req.username, req.orgname);

        res.send(response);
    }

    async function joinChannel(req: RequestEx, res: express.Response) {
        logger.info('<<<<<<<<<<<<<<<<< J O I N  C H A N N E L >>>>>>>>>>>>>>>>>');

        const channelName = req.params.channelName;
        const peers = req.body.peers;
        logger.debug('channelName : ' + channelName);
        logger.debug('peers : ' + peers);
        if (!channelName) {
            res.json(getErrorMessage('\'channelName\''));
            return;
        }
        if (!peers || peers.length === 0) {
            res.json(getErrorMessage('\'peers\''));
            return;
        }

        const message = await channelApi.joinChannel(channelName, peers, req.username, req.orgname);
        res.send(message);
    }

    async function instantiateChainCode(req: RequestEx, res: express.Response) {
        logger.debug('==================== INSTANTIATE CHAINCODE ==================');
        const chaincodeName = req.body.chaincodeName;
        const chaincodeVersion = req.body.chaincodeVersion;
        const channelName = req.params.channelName;
        const fcn = req.body.fcn;
        const args = req.body.args;
        logger.debug('channelName  : ' + channelName);
        logger.debug('chaincodeName : ' + chaincodeName);
        logger.debug('chaincodeVersion  : ' + chaincodeVersion);
        logger.debug('fcn  : ' + fcn);
        logger.debug('args  : ' + args);
        if (!chaincodeName) {
            res.json(getErrorMessage('\'chaincodeName\''));
            return;
        }
        if (!chaincodeVersion) {
            res.json(getErrorMessage('\'chaincodeVersion\''));
            return;
        }
        if (!channelName) {
            res.json(getErrorMessage('\'channelName\''));
            return;
        }
        if (!args) {
            res.json(getErrorMessage('\'args\''));
            return;
        }

        const message = await channelApi.instantiateChainCode(
            channelName, chaincodeName, chaincodeVersion, fcn, args, req.username, req.orgname);
        res.send(message);
    }

    async function invokeChainCode(req: RequestEx, res: express.Response) {
        logger.debug('==================== INVOKE ON CHAINCODE ==================');
        const peers = req.body.peers;
        const chaincodeName = req.params.chaincodeName;
        const channelName = req.params.channelName;
        const fcn = req.body.fcn;
        const args = req.body.args;
        logger.debug('channelName  : ' + channelName);
        logger.debug('chaincodeName : ' + chaincodeName);
        logger.debug('fcn  : ' + fcn);
        logger.debug('args  : ' + args);
        if (!chaincodeName) {
            res.json(getErrorMessage('\'chaincodeName\''));
            return;
        }
        if (!channelName) {
            res.json(getErrorMessage('\'channelName\''));
            return;
        }
        if (!fcn) {
            res.json(getErrorMessage('\'fcn\''));
            return;
        }
        if (!args) {
            res.json(getErrorMessage('\'args\''));
            return;
        }

        const message = await channelApi.invokeChaincode(
            peers, channelName, chaincodeName, fcn, args, req.username, req.orgname);

        res.send(message);
    }

    async function queryChainCode(req: RequestEx, res: express.Response) {
        const channelName = req.params.channelName;
        const chaincodeName = req.params.chaincodeName;
        let args = req.query.args;
        const fcn = req.query.fcn;
        const peer = req.query.peer;

        logger.debug('channelName : ' + channelName);
        logger.debug('chaincodeName : ' + chaincodeName);
        logger.debug('fcn : ' + fcn);
        logger.debug('args : ' + args);

        if (!chaincodeName) {
            res.json(getErrorMessage('\'chaincodeName\''));
            return;
        }
        if (!channelName) {
            res.json(getErrorMessage('\'channelName\''));
            return;
        }
        if (!fcn) {
            res.json(getErrorMessage('\'fcn\''));
            return;
        }
        if (!args) {
            res.json(getErrorMessage('\'args\''));
            return;
        }

        args = args.replace(/'/g, '"');
        args = JSON.parse(args);
        logger.debug(args);

        const message = await channelApi.queryChaincode(
            peer, channelName, chaincodeName, args, fcn, req.username, req.orgname);

        res.send(message);
    }

    async function queryByBlockNumber(req: RequestEx, res: express.Response) {
        logger.debug('==================== GET BLOCK BY NUMBER ==================');
        const blockId = req.params.blockId;
        const peer = req.query.peer;
        logger.debug('channelName : ' + req.params.channelName);
        logger.debug('BlockID : ' + blockId);
        logger.debug('Peer : ' + peer);
        if (!blockId) {
            res.json(getErrorMessage('\'blockId\''));
            return;
        }

        const message = await channelApi.getBlockByNumber(peer, blockId, req.username, req.orgname);
        res.send(message);
    }

    async function queryByTransactionId(req: RequestEx, res: express.Response) {
        logger.debug(
            '================ GET TRANSACTION BY TRANSACTION_ID ======================'
        );
        logger.debug('channelName : ' + req.params.channelName);
        const trxnId = req.params.trxnId;
        const peer = req.query.peer;
        if (!trxnId) {
            res.json(getErrorMessage('\'trxnId\''));
            return;
        }

        const message = await channelApi.getTransactionByID(
            peer, trxnId, req.username, req.orgname);

        res.send(message);
    }

    async function queryChannelInfo(req: RequestEx, res: express.Response) {
        logger.debug(
            '================ GET CHANNEL INFORMATION ======================');
        logger.debug('channelName : ' + req.params.channelName);
        const peer = req.query.peer;

        const message = await channelApi.getChainInfo(peer, req.username, req.orgname);

        res.send(message);
    }

    async function queryChannels(req: RequestEx, res: express.Response) {
        logger.debug('================ GET CHANNELS ======================');
        logger.debug('peer: ' + req.query.peer);
        const peer = req.query.peer;
        if (!peer) {
            res.json(getErrorMessage('\'peer\''));
            return;
        }

        const message = await channelApi.getChannels(peer, req.username, req.orgname);
        res.send(message);
    }

    const API_ENDPOINT_CHANNEL_CREATE = '/channels';
    const API_ENDPOINT_CHANNEL_JOIN = '/channels/:channelName/peers';
    const API_ENDPOINT_CHANNEL_INSTANTIATE_CHAINCODE = '/channels/:channelName/chaincodes';
    const API_ENDPOINT_CHANNEL_INVOKE_CHAINCODE =
        '/channels/:channelName/chaincodes/:chaincodeName';
    const API_ENDPOINT_CHANNEL_QUERY_CHAINCODE = '/channels/:channelName/chaincodes/:chaincodeName';
    const API_ENDPOINT_CHANNEL_QUERY_BY_BLOCKNUMBER = '/channels/:channelName/blocks/:blockId';
    const API_ENDPOINT_CHANNEL_QUERY_BY_TRANSACTIONID
        = '/channels/:channelName/transactions/:trxnId';
    const API_ENDPOINT_CHANNEL_INFO = '/channels/:channelName';
    const API_ENDPOINT_CHANNEL_QUERY = '/channels';

    app.post(API_ENDPOINT_CHANNEL_CREATE, createNewChannel);
    app.post(API_ENDPOINT_CHANNEL_JOIN, joinChannel);
    app.post(API_ENDPOINT_CHANNEL_INSTANTIATE_CHAINCODE, instantiateChainCode);
    app.post(API_ENDPOINT_CHANNEL_INVOKE_CHAINCODE, invokeChainCode);
    app.get(API_ENDPOINT_CHANNEL_QUERY_CHAINCODE, queryChainCode);
    app.get(API_ENDPOINT_CHANNEL_QUERY_BY_BLOCKNUMBER, queryByBlockNumber);
    app.get(API_ENDPOINT_CHANNEL_QUERY_BY_TRANSACTIONID, queryByTransactionId);
    app.get(API_ENDPOINT_CHANNEL_INFO, queryChannelInfo);
    app.get(API_ENDPOINT_CHANNEL_QUERY, queryChannels);
}
