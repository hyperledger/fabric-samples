/*
 * SPDX-License-Identifier: Apache-2.0
 */

import * as config from './config';
import IORedis, { Redis } from 'ioredis';
import {
  clearTransactionDetails,
  incrementRetryCount,
  storeTransactionDetails,
  getTransactionDetails,
  getRetryTransactionDetails,
} from './redis';

jest.mock('ioredis', () => require('ioredis-mock/jest'));
jest.mock('./config');

describe('Redis', () => {
  let redis: Redis;

  const mockTransactionId =
    '0ae62c01e4c4b112c3f3954a2f11243da76778e46df9ad2783bcbafc79652b95';
  const mockKey = `txn:${mockTransactionId}`;
  const mockMspId = 'Org1MSP';
  const mockState = Buffer.from(
    `{"name":"CreateAsset","nonce":"damqinq8nrI4n4qY8lFVsZw7RwG2ufrv","transactionId":${mockTransactionId}`
  );
  const mockArgs = '["test111","red",400,"Jean",101]';
  const mockTimestamp = 1628078044362;

  const addMockTransationDetails = async (redis: Redis) => {
    await redis
      .multi()
      .hset(
        mockKey,
        'mspId',
        mockMspId,
        'state',
        mockState,
        'args',
        mockArgs,
        'timestamp',
        mockTimestamp,
        'retries',
        '0'
      )
      .zadd('index:txn:timestamp', mockTimestamp, mockTransactionId)
      .exec();
  };

  beforeEach(async () => {
    const redisOptions = {
      port: config.redisPort,
      host: config.redisHost,
      username: config.redisUsername,
      password: config.redisPassword,
    };

    redis = new IORedis(redisOptions) as unknown as Redis;
  });
  describe('storeTransactionDetails', () => {
    it('stores transaction details as a hash', async () => {
      await storeTransactionDetails(
        redis,
        mockTransactionId,
        mockMspId,
        mockState,
        mockArgs,
        mockTimestamp
      );

      const storedTransaction = await redis.hgetall(mockKey);
      const expectedTransaction = {
        mspId: mockMspId,
        state: mockState,
        args: mockArgs,
        retries: '0',
        timestamp: mockTimestamp.toString(),
      };
      expect(storedTransaction).toStrictEqual(expectedTransaction);
    });

    it('adds the transaction ID to the sorted set timestamp index', async () => {
      await storeTransactionDetails(
        redis,
        mockTransactionId,
        mockMspId,
        mockState,
        mockArgs,
        mockTimestamp
      );

      const index = await redis.zrange('index:txn:timestamp', 0, -1);
      expect(index).toStrictEqual([mockTransactionId]);
    });

    // TODO this seems to work for spying/mocking...
    //   jest.spyOn(redis, 'multi').mock...
    // but haven't worked out how to spy on the hset, zadd, exec in that chain
    // Ask Mark?
    it.todo('handles an error from redis');
  });

  describe('getTransactionDetails', () => {
    it('gets the transaction details from a hash', async () => {
      await addMockTransationDetails(redis);

      const details = await getTransactionDetails(redis, mockTransactionId);

      expect(details).toStrictEqual({
        transactionId: mockTransactionId,
        mspId: mockMspId,
        transactionState: mockState,
        transactionArgs: mockArgs,
        retries: 0,
        timestamp: mockTimestamp,
      });
    });

    it.todo('handles an error from redis');
  });

  describe('getRetryTransactionDetails', () => {
    it('gets the oldest transaction details from a hash', async () => {
      await addMockTransationDetails(redis);

      const details = await getRetryTransactionDetails(redis);

      expect(details).toStrictEqual({
        transactionId: mockTransactionId,
        mspId: mockMspId,
        transactionState: mockState,
        transactionArgs: mockArgs,
        retries: 0,
        timestamp: mockTimestamp,
      });
    });

    it('gets undefined if there are no transactions to retry', async () => {
      const details = await getRetryTransactionDetails(redis);

      expect(details).toBeUndefined();
    });

    it.todo('handles an error from redis');
  });

  describe('clearTransactionDetails', () => {
    it('removes the transaction details hash', async () => {
      await addMockTransationDetails(redis);

      await clearTransactionDetails(redis, mockTransactionId);

      const storedTransaction = await redis.hgetall(mockKey);
      expect(storedTransaction).not.toHaveProperty('state');
    });

    it('removes the transaction ID from the sorted set timestamp index', async () => {
      await addMockTransationDetails(redis);

      await clearTransactionDetails(redis, mockTransactionId);

      const index = await redis.zrange('index:txn:timestamp', 0, -1);
      expect(index).toStrictEqual([]);
    });
  });

  describe('incrementRetryCount', () => {
    it('increments the retries value in the transction details hash', async () => {
      await addMockTransationDetails(redis);

      await incrementRetryCount(redis, mockTransactionId);

      const retries = await redis.hget(mockKey, 'retries');
      expect(retries).toBe('1');
    });

    it.todo(
      'updates the position of the transaction ID in the sorted set timestamp index'
    );

    it.todo('handles an error from redis');
  });
});
