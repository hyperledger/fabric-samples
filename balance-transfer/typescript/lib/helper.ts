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

import log4js = require('log4js');
import * as path from 'path';
import * as fs from 'fs';
import * as util from 'util';
import config from '../config';
import Client = require('fabric-client');
import { User, UserOpts, Channel } from 'fabric-client';
// tslint:disable-next-line:no-var-requires
const copService = require('fabric-ca-client');

const logger = log4js.getLogger('Helper');
logger.setLevel('DEBUG');
Client.setLogger(logger);

let ORGS: any;
const clients = {};
const channels = {};
const caClients = {};

function readAllFiles(dir: string) {
    const files = fs.readdirSync(dir);
    const certs: any = [];
    files.forEach((fileName) => {
        const filePath = path.join(dir, fileName);
        const data = fs.readFileSync(filePath);
        certs.push(data);
    });
    return certs;
}

function getKeyStoreForOrg(org: string) {
    return Client.getConfigSetting('keyValueStore') + '_' + org;
}

function setupPeers(channel: any, org: string, client: Client) {
    for (const key in ORGS[org].peers) {
        if (key) {
            const data = fs.readFileSync(
                path.join(__dirname, ORGS[org].peers[key]['tls_cacerts']));
            const peer = client.newPeer(
                ORGS[org].peers[key].requests,
                {
                    'pem': Buffer.from(data).toString(),
                    'ssl-target-name-override': ORGS[org].peers[key]['server-hostname']
                }
            );
            peer.setName(key);

            channel.addPeer(peer);
        }
    }
}

function newOrderer(client: Client) {
    const caRootsPath = ORGS.orderer.tls_cacerts;
    const data = fs.readFileSync(path.join(__dirname, caRootsPath));
    const caroots = Buffer.from(data).toString();
    return client.newOrderer(ORGS.orderer.url, {
        'pem': caroots,
        'ssl-target-name-override': ORGS.orderer['server-hostname']
    });
}

function getOrgName(org: string) {
    return ORGS[org].name;
}

function getMspID(org: string) {
    logger.debug('Msp ID : ' + ORGS[org].mspid);
    return ORGS[org].mspid;
}

function newRemotes(names: string[], forPeers: boolean, userOrg: string) {
    const client = getClientForOrg(userOrg);
    const channel = getChannelForOrg(userOrg);
    const targets: any[] = [];
    // find the peer that match the names
    names.forEach((n) => {
        if (ORGS[userOrg].peers[n]) {
            // found a peer matching the name
            const data = fs.readFileSync(
                path.join(__dirname, ORGS[userOrg].peers[n]['tls_cacerts']));
            const grpcOpts = {
                'pem': Buffer.from(data).toString(),
                'ssl-target-name-override': ORGS[userOrg].peers[n]['server-hostname']
            };

            const peer = client.newPeer(ORGS[userOrg].peers[n].requests, grpcOpts);
            if (forPeers) {
                targets.push(peer);
            } else {
                const eh = channel.newChannelEventHub(peer);
                targets.push(eh);
            }
        }
    });

    if (targets.length === 0) {
        logger.error(util.format('Failed to find peers matching the names %s', names));
    }

    return targets;
}

async function getAdminUser(userOrg: string): Promise<User> {
    const users = Client.getConfigSetting('admins');
    const username = users[0].username;
    const password = users[0].secret;

    const client = getClientForOrg(userOrg);

    const store = await Client.newDefaultKeyValueStore({
        path: getKeyStoreForOrg(getOrgName(userOrg))
    });

    client.setStateStore(store);

    const user = await client.getUserContext(username, true);

    if (user && user.isEnrolled()) {
        logger.info('Successfully loaded member from persistence');
        return user;
    }

    const caClient = caClients[userOrg];

    const enrollment = await caClient.enroll({
        enrollmentID: username,
        enrollmentSecret: password
    });

    logger.info('Successfully enrolled user \'' + username + '\'');
    const userOptions: UserOpts = {
        username,
        mspid: getMspID(userOrg),
        cryptoContent: {
            privateKeyPEM: enrollment.key.toBytes(),
            signedCertPEM: enrollment.certificate
        },
        skipPersistence: false
    };

    const member = await client.createUser(userOptions);
    return member;
}

export function newPeers(names: string[], org: string) {
    return newRemotes(names, true, org);
}

export function newEventHubs(names: string[], org: string) {
    return newRemotes(names, false, org);
}

export function setupChaincodeDeploy() {
    process.env.GOPATH = path.join(__dirname, Client.getConfigSetting('CC_SRC_PATH'));
}

export function getOrgs() {
    return ORGS;
}

export function getClientForOrg(org: string): Client {
    return clients[org];
}

export function getChannelForOrg(org: string): Channel {
    return channels[org];
}

export function init() {

    Client.addConfigFile(path.join(__dirname, config.networkConfigFile));
    Client.addConfigFile(path.join(__dirname, '../app_config.json'));

    ORGS = Client.getConfigSetting('network-config');

    // set up the client and channel objects for each org
    for (const key in ORGS) {
        if (key.indexOf('org') === 0) {
            const client = new Client();

            const cryptoSuite = Client.newCryptoSuite();
            // TODO: Fix it up as setCryptoKeyStore is only available for s/w impl
            (cryptoSuite as any).setCryptoKeyStore(
                Client.newCryptoKeyStore({
                    path: getKeyStoreForOrg(ORGS[key].name)
                }));

            client.setCryptoSuite(cryptoSuite);

            const channel = client.newChannel(Client.getConfigSetting('channelName'));
            channel.addOrderer(newOrderer(client));

            clients[key] = client;
            channels[key] = channel;

            setupPeers(channel, key, client);

            const caUrl = ORGS[key].ca;
            caClients[key] = new copService(
                caUrl, null /*defautl TLS opts*/, '' /* default CA */, cryptoSuite);
        }
    }
}

export async function getRegisteredUsers(
    username: string, userOrg: string): Promise<User> {

    const client = getClientForOrg(userOrg);

    const store = await Client.newDefaultKeyValueStore({
        path: getKeyStoreForOrg(getOrgName(userOrg))
    });

    client.setStateStore(store);
    const user = await client.getUserContext(username, true);

    if (user && user.isEnrolled()) {
        logger.info('Successfully loaded member from persistence');
        return user;
    }

    logger.info('Using admin to enroll this user ..');

    // get the Admin and use it to enroll the user
    const adminUser = await getAdminUser(userOrg);

    const caClient = caClients[userOrg];
    const secret = await caClient.register({
        enrollmentID: username,
        affiliation: userOrg + '.department1'
    }, adminUser);

    logger.debug(username + ' registered successfully');

    const message = await caClient.enroll({
        enrollmentID: username,
        enrollmentSecret: secret
    });

    if (message && typeof message === 'string' && message.includes(
        'Error:')) {
        logger.error(username + ' enrollment failed');
    }
    logger.debug(username + ' enrolled successfully');

    const userOptions: UserOpts = {
        username,
        mspid: getMspID(userOrg),
        cryptoContent: {
            privateKeyPEM: message.key.toBytes(),
            signedCertPEM: message.certificate
        },
        skipPersistence: false
    };

    const member = await client.createUser(userOptions);
    return member;
}

export function getLogger(moduleName: string) {
    const moduleLogger = log4js.getLogger(moduleName);
    moduleLogger.setLevel('DEBUG');
    return moduleLogger;
}

export async function getOrgAdmin(userOrg: string): Promise<User> {
    const admin = ORGS[userOrg].admin;
    const keyPath = path.join(__dirname, admin.key);
    const keyPEM = Buffer.from(readAllFiles(keyPath)[0]).toString();
    const certPath = path.join(__dirname, admin.cert);
    const certPEM = readAllFiles(certPath)[0].toString();

    const client = getClientForOrg(userOrg);
    const cryptoSuite = Client.newCryptoSuite();

    if (userOrg) {
        (cryptoSuite as any).setCryptoKeyStore(
            Client.newCryptoKeyStore({ path: getKeyStoreForOrg(getOrgName(userOrg)) }));
        client.setCryptoSuite(cryptoSuite);
    }

    const store = await Client.newDefaultKeyValueStore({
        path: getKeyStoreForOrg(getOrgName(userOrg))
    });

    client.setStateStore(store);

    return client.createUser({
        username: 'peer' + userOrg + 'Admin',
        mspid: getMspID(userOrg),
        cryptoContent: {
            privateKeyPEM: keyPEM,
            signedCertPEM: certPEM
        },
        skipPersistence: false
    });
}
