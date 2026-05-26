/*
 * SPDX-License-Identifier: Apache-2.0
 */

import * as fs from 'fs';
import * as path from 'path';

import * as grpc from '@grpc/grpc-js';
import yaml from 'js-yaml';

const JSON_EXT = /json/gi;
const YAML_EXT = /ya?ml/gi;

export interface ConnectionProfile {
    display_name: string;
    id: string;
    name: string;
    type: string;
    version: string;
    //
    certificateAuthorities: any;
    client: any;
    oprganizations: any;
    peers: { [key: string]: Peer };
}

export interface Peer {
    grpcOptions: grpcOptions;
    url: string;
    tlsCACerts: any
}

export interface grpcOptions {
    'ssl-Target-Name-Override'?: string;
    hostnameOverride?: string;
    'grpc.ssl_target_name_override'?: string;
    'grpc.default_authority'?: string;
}

export class ConnectionHelper {
    /**
     * Loads the profile at the given filename.
     *
     * File can either by yaml or json, error is thrown is the file does
     * not exist at the location given.
     *
     * @param profilename filename of the gateway connection profile
     * @return Gateway profile as an object
     */
    static loadProfile(profilename: string): ConnectionProfile {
        const ccpPath = path.resolve(profilename);
        if (!fs.existsSync(ccpPath)) {
            throw new Error(`Profile file ${ccpPath} does not exist`);
        }

        const type = path.extname(ccpPath);

        if (JSON_EXT.exec(type)) {
            return JSON.parse(fs.readFileSync(ccpPath, 'utf8'));
        } else if (YAML_EXT.exec(type)) {
            return yaml.load(fs.readFileSync(ccpPath, 'utf8')) as ConnectionProfile;
        } else {
            throw new Error(`Extension of ${ccpPath} not recognised`);
        }
    }


    static async newGrpcConnection(cp: ConnectionProfile, tls: boolean): Promise<grpc.Client> {
        const peerEndpointURL = new URL(cp.peers[Object.keys(cp.peers)[0]].url);
        const peerEndpoint = `${peerEndpointURL.hostname}:${peerEndpointURL.port}`;

        if (tls){
            const tlsRootCert = cp.peers[Object.keys(cp.peers)[0]].tlsCACerts.pem;
            const tlsCredentials = grpc.credentials.createSsl(Buffer.from(tlsRootCert));
    
            return new grpc.Client(peerEndpoint, tlsCredentials);
    
        } else {
            console.log(peerEndpoint);
            return new grpc.Client(peerEndpoint, grpc.ChannelCredentials.createInsecure());
    
        }
    }

}
