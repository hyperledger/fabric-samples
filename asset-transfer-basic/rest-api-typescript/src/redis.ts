/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * This sample includes basic retry logic so it needs somewhere to store
 * transaction details in case the app restarts for any reason, and Redis is
 * just one of the options available
 *
 * Note: This implementation is not designed with multiple instances of the
 * REST app in mind, which is likely to be required in a production environment
 */

import IORedis, { Redis, RedisOptions } from 'ioredis';

import * as config from './config';
import { logger } from './logger';

const redisOptions: RedisOptions = {
  port: config.redisPort,
  host: config.redisHost,
  username: config.redisUsername,
  password: config.redisPassword,
};

export const redis = new IORedis(redisOptions);

export type TransactionDetails = {
  transactionId: string;
  mspId: string;
  transactionState: Buffer;
  transactionArgs: string;
  timestamp: number;
  retries: number;
};

/*
 * Store enough information in order to resubmit a transaction
 */
export const storeTransactionDetails = async (
  redis: Redis,
  transactionId: string,
  mspId: string,
  transactionState: Buffer,
  transactionArgs: string,
  timestamp: number
): Promise<void> => {
  try {
    const key = `txn:${transactionId}`;
    logger.debug(
      {
        key,
        mspId,
        transactionState,
        transactionArgs,
        timestamp,
      },
      'Storing transaction details'
    );
    await redis
      .multi()
      .hset(
        key,
        'mspId',
        mspId,
        'state',
        transactionState,
        'args',
        transactionArgs,
        'timestamp',
        timestamp,
        'retries',
        '0'
      )
      .zadd('index:txn:timestamp', timestamp, transactionId)
      .exec();
  } catch (err) {
    // TODO just log?!
    logger.error(
      { err },
      'Error storing details for transaction ID %s',
      transactionId
    );
  }
};

/*
 * Get the information required to resubmit a transaction
 */
export const getTransactionDetails = async (
  redis: Redis,
  transactionId: string
): Promise<TransactionDetails | undefined> => {
  try {
    const savedTransaction = await (redis as Redis).hgetall(
      `txn:${transactionId}`
    );
    logger.debug(
      { transactionId: transactionId, state: savedTransaction },
      'Got transaction details'
    );

    const transactionDetails = {
      transactionId: transactionId,
      mspId: savedTransaction.mspId,
      transactionState: Buffer.from(savedTransaction.state),
      transactionArgs: savedTransaction.args,
      timestamp: parseInt(savedTransaction.timestamp),
      retries: parseInt(savedTransaction.retries),
    };
    return transactionDetails;
  } catch (err) {
    // TODO just log?!
    logger.error(
      { err },
      'Error getting details for transaction ID %s',
      transactionId
    );
  }
};

/*
 * Get the oldest transaction details
 */
export const getRetryTransactionDetails = async (
  redis: Redis
): Promise<TransactionDetails | undefined> => {
  try {
    const transactionIds = await (redis as Redis).zrange(
      'index:txn:timestamp',
      -1,
      -1
    );

    if (transactionIds.length > 0) {
      const transactionId = transactionIds[0];

      const savedTransaction = await getTransactionDetails(
        redis,
        transactionId
      );
      return savedTransaction;
    }
  } catch (err) {
    // TODO just log?!
    logger.error(
      { err },
      'Error getting details for next transaction to retry'
    );
  }
};

/*
 * Delete transaction details
 */
export const clearTransactionDetails = async (
  redis: Redis,
  transactionId: string
): Promise<void> => {
  const key = `txn:${transactionId}`;
  logger.debug('Removing transaction details. Key: %s', key);
  try {
    await redis
      .multi()
      .del(key)
      .zrem('index:txn:timestamp', transactionId)
      .exec();
  } catch (err) {
    // TODO just log?!
    logger.error(
      { err },
      'Error remove details for transaction ID %s',
      transactionId
    );
  }
};

/*
 * Increment the number of times the transaction has been retried

 * TODO needs to update the timestamp and index as well
 */
export const incrementRetryCount = async (
  redis: Redis,
  transactionId: string
): Promise<void> => {
  const key = `txn:${transactionId}`;
  logger.debug('Incrementing retries fortransaction Key: %s', key);
  try {
    await (redis as Redis).hincrby(`txn:${transactionId}`, 'retries', 1);
  } catch (err) {
    // TODO just log?!
    logger.error(
      err,
      'Error incrementing retries for transaction ID %s',
      transactionId
    );
  }
};
