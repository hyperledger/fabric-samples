/*
 * SPDX-License-Identifier: Apache-2.0
 */

import * as env from 'env-var';

/*
 * Log level for the REST server
 */
export const logLevel = env
  .get('LOG_LEVEL')
  .default('info')
  .asEnum(['fatal', 'error', 'warn', 'info', 'debug', 'trace', 'silent']);

/*
 * The port to start the REST server on
 */
export const port = env
  .get('PORT')
  .default('3000')
  .example('3000')
  .asPortNumber();

/*
 * The delay between each retry attempt in milliseconds
 */
export const retryDelay = env
  .get('RETRY_DELAY')
  .default('3000')
  .example('3000')
  .asIntPositive();

/*
 * The maximum number of times to retry a failing transaction
 */
export const maxRetryCount = env
  .get('MAX_RETRY_COUNT')
  .default('5')
  .example('5')
  .asIntPositive();

/*
 * Whether to convert discovered host addresses to be 'localhost'
 * This should be set to 'true' when running a docker composed fabric network on the
 * local system, e.g. using the test network; otherwise should it should be 'false'
 */
export const asLocalhost = env
  .get('AS_LOCAL_HOST')
  .default('true')
  .example('true')
  .asBoolStrict();

/*
 * The Org1 MSP ID
 */
export const mspIdOrg1 = env
  .get('HLF_MSP_ID_ORG1')
  .default('Org1MSP')
  .example('Org1MSP')
  .asString();

/*
 * The Org2 MSP ID
 */
export const mspIdOrg2 = env
  .get('HLF_MSP_ID_ORG2')
  .default('Org2MSP')
  .example('Org2MSP')
  .asString();

/*
 * Name of the channel which the basic asset sample chaincode has been installed on
 */
export const channelName = env
  .get('HLF_CHANNEL_NAME')
  .default('mychannel')
  .example('mychannel')
  .asString();

/*
 * Name used to install the basic asset sample
 */
export const chaincodeName = env
  .get('HLF_CHAINCODE_NAME')
  .default('basic')
  .example('basic')
  .asString();

/*
 * The transaction submit timeout in seconds for commit notification to complete
 */
export const commitTimeout = env
  .get('HLF_COMMIT_TIMEOUT')
  .default('3000')
  .example('3000')
  .asIntPositive();

/*
 * The transaction submit timeout in seconds for the endorsement to complete
 */
export const endorseTimeout = env
  .get('HLF_ENDORSE_TIMEOUT')
  .default('30')
  .example('30')
  .asIntPositive();

/*
 * The transaction query timeout in seconds
 */
export const queryTimeout = env
  .get('HLF_QUERY_TIMEOUT')
  .default('3')
  .example('3')
  .asIntPositive();

/*
 * The Org1 connection profile JSON
 */
export const connectionProfileOrg1 = env
  .get('HLF_CONNECTION_PROFILE_ORG1')
  .required()
  .example(
    '{"name":"test-network-org1","version":"1.0.0","client":{"organization":"Org1" ... }'
  )
  .asJsonObject() as Record<string, unknown>;

/*
 * Certificate for the Org1 identity
 */
export const certificateOrg1 = env
  .get('HLF_CERTIFICATE_ORG1')
  .required()
  .example('"-----BEGIN CERTIFICATE-----\\n...\\n-----END CERTIFICATE-----\\n"')
  .asString();

/*
 * Private key for the Org1 identity
 */
export const privateKeyOrg1 = env
  .get('HLF_PRIVATE_KEY_ORG1')
  .required()
  .example('"-----BEGIN PRIVATE KEY-----\\n...\\n-----END PRIVATE KEY-----\\n"')
  .asString();

/*
 * The Org2 connection profile JSON
 */
export const connectionProfileOrg2 = env
  .get('HLF_CONNECTION_PROFILE_ORG2')
  .required()
  .example(
    '{"name":"test-network-org2","version":"1.0.0","client":{"organization":"Org2" ... }'
  )
  .asJsonObject() as Record<string, unknown>;

/*
 * Certificate for the Org2 identity
 */
export const certificateOrg2 = env
  .get('HLF_CERTIFICATE_ORG2')
  .required()
  .example('"-----BEGIN CERTIFICATE-----\\n...\\n-----END CERTIFICATE-----\\n"')
  .asString();

/*
 * Private key for the Org2 identity
 */
export const privateKeyOrg2 = env
  .get('HLF_PRIVATE_KEY_ORG2')
  .required()
  .example('"-----BEGIN PRIVATE KEY-----\\n...\\n-----END PRIVATE KEY-----\\n"')
  .asString();

/*
 * The host the Redis server is running on
 */
export const redisHost = env
  .get('REDIS_HOST')
  .default('localhost')
  .example('localhost')
  .asString();

/*
 * The port the Redis server is running on
 */
export const redisPort = env
  .get('REDIS_PORT')
  .default('6379')
  .example('6379')
  .asPortNumber();

/*
 * Username for the Redis server
 */
export const redisUsername = env
  .get('REDIS_USERNAME')
  .example('conga')
  .asString();

/*
 * Password for the Redis server
 */
export const redisPassword = env.get('REDIS_PASSWORD').asString();

/*
 * API key for Org1
 * Specify this API key with the X-Api-Key header to use the Org1 connection profile and credentials
 */
export const org1ApiKey = env
  .get('ORG1_APIKEY')
  .required()
  .example('123')
  .asString();

/*
 * API key for Org2
 * Specify this API key with the X-Api-Key header to use the Org2 connection profile and credentials
 */
export const org2ApiKey = env
  .get('ORG2_APIKEY')
  .required()
  .example('456')
  .asString();
