/*
 * SPDX-License-Identifier: Apache-2.0
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

export const storeTransactionDetails = async (
  redis: Redis,
  transactionId: string,
  transactionState: Buffer,
  transactionArgs: string,
  timestamp: number
): Promise<void> => {
  const key = `txn:${transactionId}`;
  logger.debug(
    'Storing transaction details. Key: %s State: %s Args: %s Timestamp: %d',
    key,
    transactionState,
    transactionArgs,
    timestamp
  );
  await redis
    .multi()
    .hset(
      key,
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
};

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
    logger.error(
      err,
      'Error remove saved transaction state for transaction ID %s',
      transactionId
    );
  }
};

// TODO add getTransaction etc. helpers?

export const incrementRetryCount = async (
  redis: Redis,
  transactionId: string
): Promise<void> => {
  const key = `txn:${transactionId}`;
  logger.debug('Incrementing retries fortransaction Key: %s', key);
  try {
    await (redis as Redis).hincrby(`txn:${transactionId}`, 'retries', 1);
  } catch (err) {
    logger.error(
      err,
      'Error incrementing retries for transaction ID %s',
      transactionId
    );
  }
};
