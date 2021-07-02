/*
 * SPDX-License-Identifier: Apache-2.0
 */

import IORedis, { RedisOptions } from 'ioredis';

import * as config from './config';

const redisOptions: RedisOptions = {
  port: config.redisPort,
  host: config.redisHost,
  username: config.redisUsername,
  password: config.redisPassword,
};

export const redis = new IORedis(redisOptions);
