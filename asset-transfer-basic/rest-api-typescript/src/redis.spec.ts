import IORedis from './__mocks__/IORedis';
import * as config from './config';
import { Redis } from 'ioredis';
import {
  clearTransactionDetails,
  incrementRetryCount,
  storeTransactionDetails,
} from './redis';

jest.mock('ioredis');
jest.mock('./config');

const redisOptions = {
  port: config.redisPort,
  host: config.redisHost,
  username: config.redisUsername,
  password: config.redisPassword,
};
const redis = new IORedis(redisOptions) as unknown as Redis;
describe('Testing increment retries ', () => {
  const transactionId =
    '0ae62c01e4c4b112c3f3954a2f11243da76778e46df9ad2783bcbafc79652b95';
  it('Should  increment retries for valid transction id', async () => {
    await incrementRetryCount(redis, transactionId);
    expect(redis.hincrby).toHaveBeenCalledTimes(1);
  });

  it('Should not increment retries for empty transaction id ', async () => {
    await incrementRetryCount(redis, '');
    expect(redis.hincrby).toHaveBeenCalledTimes(0);
  });
});

describe('Testing storeTransactionDetails ', () => {
  const args = '["test111","red",400,"Jean",101]';
  const timestamp = 1628078044362;
  it('Should  store details for valid transction Id', async () => {
    const transactionId =
      '0ae62c01e4c4b112c3f3954a2f11243da76778e46df9ad2783bcbafc79652b95';
    const state = `{"name":"CreateAsset","nonce":"damqinq8nrI4n4qY8lFVsZw7RwG2ufrv","transactionId":${transactionId}`;
    await storeTransactionDetails(
      redis,
      transactionId,
      Buffer.from(state),
      args,
      timestamp
    );
    expect(redis.hset).toHaveBeenCalledTimes(1);
    expect(redis.zadd).toHaveBeenCalledTimes(1);
  });

  it('Should not  store details for empty transction Id', async () => {
    const transactionId = '';
    const state = `{"name":"CreateAsset","nonce":"damqinq8nrI4n4qY8lFVsZw7RwG2ufrv","transactionId":${transactionId}`;
    await storeTransactionDetails(
      redis,
      transactionId,
      Buffer.from(state),
      args,
      timestamp
    );
    expect(redis.hset).toHaveBeenCalledTimes(0);
    expect(redis.zadd).toHaveBeenCalledTimes(0);
  });
});

describe('Testing clearTransactionDetails ', () => {
  it('Should  clear details ', async () => {
    const transactionId =
      '0ae62c01e4c4b112c3f3954a2f11243da76778e46df9ad2783bcbafc79652b95';
    await clearTransactionDetails(redis, transactionId);
    expect(redis.del).toHaveBeenCalledTimes(1);
    expect(redis.zrem).toHaveBeenCalledTimes(1);
  });
});
