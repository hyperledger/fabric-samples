/*
 * SPDX-License-Identifier: Apache-2.0
 */

import * as env from 'env-var';

export const logLevel = env
  .get('LOG_LEVEL')
  .default('info')
  .asEnum(['fatal', 'error', 'warn', 'info', 'debug', 'trace', 'silent']);

export const port = env
  .get('PORT')
  .default('3000')
  .example('3000')
  .asIntPositive();

export const retryDelay = env
  .get('RETRY_DELAY')
  .default('3000')
  .example('3000')
  .asIntPositive();

export const asLocalHost = env
  .get('AS_LOCAL_HOST')
  .default('true')
  .example('true')
  .asBoolStrict();

export const identityNameOrg1 = 'Org1';
export const identityNameOrg2 = 'Org2';

const mspIdOrg1 = env
  .get('HLF_MSP_ID_ORG1')
  .default('Org1MSP')
  .example('Org1MSP')
  .asString();

const mspIdOrg2 = env
  .get('HLF_MSP_ID_ORG2')
  .default('Org2MSP')
  .example('Org2MSP')
  .asString();

export const channelName = env
  .get('HLF_CHANNEL_NAME')
  .default('mychannel')
  .example('mychannel')
  .asString();

export const chaincodeName = env
  .get('HLF_CHAINCODE_NAME')
  .default('basic')
  .example('basic')
  .asString();

export const commitTimeout = env
  .get('HLF_COMMIT_TIMEOUT')
  .default('3000')
  .example('3000')
  .asIntPositive();

export const endorseTimeout = env
  .get('HLF_ENDORSE_TIMEOUT')
  .default('30')
  .example('30')
  .asIntPositive();

const connectionProfileOrg1 = env
  .get('HLF_CONNECTION_PROFILE_ORG1')
  .required()
  .example(
    '{"name":"test-network-org1","version":"1.0.0","client":{"organization":"Org1" ... }'
  )
  .asJsonObject();

const certificateOrg1 = env
  .get('HLF_CERTIFICATE_ORG1')
  .required()
  .example('"-----BEGIN CERTIFICATE-----\\n...\\n-----END CERTIFICATE-----\\n"')
  .asString();

const privateKeyOrg1 = env
  .get('HLF_PRIVATE_KEY_ORG1')
  .required()
  .example('"-----BEGIN PRIVATE KEY-----\\n...\\n-----END PRIVATE KEY-----\\n"')
  .asString();

const connectionProfileOrg2 = env
  .get('HLF_CONNECTION_PROFILE_ORG2')
  .required()
  .example(
    '{"name":"test-network-org2","version":"1.0.0","client":{"organization":"Org2" ... }'
  )
  .asJsonObject();

const certificateOrg2 = env
  .get('HLF_CERTIFICATE_ORG2')
  .required()
  .example('"-----BEGIN CERTIFICATE-----\\n...\\n-----END CERTIFICATE-----\\n"')
  .asString();

const privateKeyOrg2 = env
  .get('HLF_PRIVATE_KEY_ORG2')
  .required()
  .example('"-----BEGIN PRIVATE KEY-----\\n...\\n-----END PRIVATE KEY-----\\n"')
  .asString();

export const redisHost = env
  .get('REDIS_HOST')
  .default('localhost')
  .example('localhost')
  .asString();

export const redisPort = env
  .get('REDIS_PORT')
  .default('6379')
  .example('6379')
  .asIntPositive();

export const redisUsername = env
  .get('REDIS_USERNAME')
  .example('conga')
  .asString();

export const redisPassword = env.get('REDIS_PASSWORD').asString();

export const org1ApiKey = env
  .get('ORG1_APIKEY')
  .required()
  .example('123')
  .asString();

export const org2ApiKey = env
  .get('ORG2_APIKEY')
  .required()
  .example('456')
  .asString();

export const ORG1_CONFIG = {
  identityName: identityNameOrg1,
  mspId: mspIdOrg1,
  connectionProfile: connectionProfileOrg1,
  certificate: certificateOrg1,
  privateKey: privateKeyOrg1,
};

export const ORG2_CONFIG = {
  identityName: identityNameOrg2,
  mspId: mspIdOrg2,
  connectionProfile: connectionProfileOrg2,
  certificate: certificateOrg2,
  privateKey: privateKeyOrg2,
};
