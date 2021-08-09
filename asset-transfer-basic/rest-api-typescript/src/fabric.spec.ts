import { retryTransaction, getGateway } from './fabric';
import { getMockedNetwork } from './__mocks__/fabric-network';
import * as config from './config';

import IORedis from './__mocks__/IORedis';
import { Redis } from 'ioredis';
import { Contract } from 'fabric-network';

jest.mock('./config');
jest.mock('ioredis');
const redisOptions = {
  port: config.redisPort,
  host: config.redisHost,
  username: config.redisUsername,
  password: config.redisPassword,
};

const redis = new IORedis(redisOptions) as unknown as Redis;

describe('Testing retryTransaction', () => {
  const transactionId =
    '0ae62c01e4c4b112c3f3954a2f11243da76778e46df9ad2783bcbafc79652b95';
  const state = `{"name":"CreateAsset","nonce":"damqinq8nrI4n4qY8lFVsZw7RwG2ufrv","transactionId":${transactionId}`;
  const args = '["test111","red",400,"Jean",101]';
  const timestamp = 1628078044362;
  const savedTransaction = {
    timestamp: timestamp.toString(),
    state: state,
    retries: '',
    args: args,
  };

  describe('Check retry increment  ', () => {
    const transactionId =
      '0ae62c01e4c4b112c3f3954a2f11243da76778e46df9ad2783bcbafc79652b95';
    const state = `{"name":"CreateAsset","nonce":"damqinq8nrI4n4qY8lFVsZw7RwG2ufrv","transactionId":${transactionId}`;
    const args = '["test111","red",400,"Jean",101]';
    const timestamp = 1628078044362;
    const savedTransaction = {
      timestamp: timestamp.toString(),
      state: state,
      retries: '',
      args: args,
    };

    it('Transaction failure, check redis increment func call', async () => {
      jest.doMock('fabric-network');
      const transaction = {
        submit: jest.fn().mockRejectedValue({}),
      };
      const mockedContact = {
        deserializeTransaction: jest.fn().mockReturnValue(transaction),
      };
      const rejectableGetContract = jest
        .fn()
        .mockImplementation(() => mockedContact);

      const network = getMockedNetwork(rejectableGetContract)('');
      const contract: Contract = (await network).getContract('');
      savedTransaction.retries = '3';
      await retryTransaction(contract, redis, transactionId, savedTransaction);
      expect(redis.hincrby).toHaveBeenCalledTimes(1);
    });
  });

  describe('Transaction successful, check redis delete key func call  ', () => {
    it('call redis increment', async () => {
      jest.doMock('fabric-network');
      const transaction = {
        submit: jest.fn().mockResolvedValue({}),
      };
      const mockedContact = {
        deserializeTransaction: jest.fn().mockReturnValue(transaction),
      };
      const resolvableGetContract = jest
        .fn()
        .mockImplementation(() => mockedContact);

      const network = getMockedNetwork(resolvableGetContract)('');
      const contract: Contract = (await network).getContract('');
      savedTransaction.retries = '3';
      await retryTransaction(contract, redis, transactionId, savedTransaction);
      expect(redis.del).toHaveBeenCalledTimes(1);
    });
  });
});

describe('Test getGateway', () => {
  it('should throw error for invalid org name', async () => {
    expect(async () => await getGateway('')).rejects.toThrow(
      'Invalid org name for gateway'
    );
  });
});
