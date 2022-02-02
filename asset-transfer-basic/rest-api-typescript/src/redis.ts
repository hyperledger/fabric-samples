/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * This sample uses the BullMQ queue system, which is built on top of Redis
 */

import IORedis, { Redis, RedisOptions } from 'ioredis';

import * as config from './config';
import { logger } from './logger';

/**
 * Check whether the maxmemory-policy config is set to noeviction
 *
 * BullMQ requires this setting in redis
 * For details, see: https://docs.bullmq.io/guide/connections
 */
export const isMaxmemoryPolicyNoeviction = async (): Promise<boolean> => {
  let redis: Redis | undefined;

  const redisOptions: RedisOptions = {
    port: config.redisPort,
    host: config.redisHost,
    username: config.redisUsername,
    password: config.redisPassword,
  };

  try {
    redis = new IORedis(redisOptions);

    const maxmemoryPolicyConfig = await (redis as Redis).config(
      'GET',
      'maxmemory-policy'
    );
    logger.debug({ maxmemoryPolicyConfig }, 'Got maxmemory-policy config');

    if (
      maxmemoryPolicyConfig.length == 2 &&
      'maxmemory-policy' === maxmemoryPolicyConfig[0] &&
      'noeviction' === maxmemoryPolicyConfig[1]
    ) {
      return true;
    }
  } finally {
    if (redis != undefined) {
      redis.disconnect();
    }
  }

  return false;
};
