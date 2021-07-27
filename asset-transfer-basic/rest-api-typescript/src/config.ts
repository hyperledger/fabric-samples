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

export const identityName = 'restServerIdentity';

export const mspId = env
  .get('HLF_MSP_ID')
  .default('Org1MSP')
  .example('Org1MSP')
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

export const connectionProfile = env
  .get('HLF_CONNECTION_PROFILE')
  .required()
  .example(
    '{"name":"test-network-org1","version":"1.0.0","client":{"organization":"Org1" ... }'
  )
  .asJsonObject();

export const certificate = env
  .get('HLF_CERTIFICATE')
  .required()
  .example('"-----BEGIN CERTIFICATE-----\\n...\\n-----END CERTIFICATE-----\\n"')
  .asString();

export const privateKey = env
  .get('HLF_PRIVATE_KEY')
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
