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

export const asLocalHost = env
  .get('AS_LOCAL_HOST')
  .default('true')
  .example('true')
  .asBoolStrict();

export const identityName = 'restServerIdentity';

export const mspId = env
  .get('MSP_ID')
  .default('Org1MSP')
  .example('Org1MSP')
  .asString();

export const channelName = env
  .get('CHANNEL_NAME')
  .default('mychannel')
  .example('mychannel')
  .asString();

export const chaincodeName = env
  .get('CHAINCODE_NAME')
  .default('basic')
  .example('basic')
  .asString();

export const connectionProfile = env
  .get('CONNECTION_PROFILE')
  .required()
  .example(
    '{"name":"test-network-org1","version":"1.0.0","client":{"organization":"Org1" ... }'
  )
  .asJsonObject();

export const certificate = env
  .get('CERTIFICATE')
  .required()
  .example('"-----BEGIN CERTIFICATE-----\\n...\\n-----END CERTIFICATE-----\\n"')
  .asString();

export const privateKey = env
  .get('PRIVATE_KEY')
  .required()
  .example('"-----BEGIN PRIVATE KEY-----\\n...\\n-----END PRIVATE KEY-----\\n"')
  .asString();
