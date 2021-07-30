import { retryTransaction } from '../fabric';

import { getMockedNetwork } from '../__mocks__/fabric-network';
import { Redis } from 'ioredis';
import * as redis from '../redis';
// import { Gateway, Gateway, Gateway } from 'fabric-network';

/**
 * retryTransaction
 */
jest.mock('../config');


describe('Testing retryTransaction', () => {
  let contract: any = null;
  const transaction = {
    submit: jest.fn().mockRejectedValue({}),
  };
  const mockedContact = {
    deserializeTransaction: jest.fn().mockReturnValue(transaction),
  };
  beforeAll(async () => {
    const rejectableGetContract = jest.fn().mockImplementation(
      () =>
        mockedContact
    );

    const network = getMockedNetwork(rejectableGetContract)('');
    contract = (await network).getContract('');

  });

  describe('Check retry condition  ', () => {
    const transactionId =
      '0ae62c01e4c4b112c3f3954a2f11243da76778e46df9ad2783bcbafc79652b95';
    const key = `txn:${transactionId}`;
    const state = `{"name":"CreateAsset","nonce":"damqinq8nrI4n4qY8lFVsZw7RwG2ufrv","transactionId":${transactionId}`;
    const args = '["test111","red",400,"Jean",101]';
    const timestamp = 1628078044362;
    const savedTransaction = {
      timestamp: timestamp.toString(),
      state: state,
      retries: '',
      args: args,
    };

    let data: Record<string, any> = {};
    beforeEach(() => {
      data = {};
      const clearTransactionDetails = jest.spyOn(
        redis,
        'clearTransactionDetails'
      );
      clearTransactionDetails.mockImplementation(
        async (redis: Redis, transactionId: string) => {
          const key = `txn:${transactionId}`;
          delete data[key];
        }
      );

      const incrementRetryCount = jest.spyOn(redis, 'incrementRetryCount');
      incrementRetryCount.mockImplementation(
        async (redis: Redis, transactionId: string) => {
          const key = `txn:${transactionId}`;
          data[key].retries = (parseInt(data[key].retries) + 1).toString();
        }
      );
    });
    it('Transaction should exist if  retry count is less then max rety count', async () => {
      savedTransaction.retries = '3';
      data = { [key]: savedTransaction };
      await retryTransaction(
        contract,
        redis.redis,
        transactionId,
        savedTransaction
      );
      expect(data[key]).toMatchObject({
        timestamp: timestamp.toString(),
        state: state,
        retries: '4',
        args: args,
      });
    });
    it('Clear transaction once retry reaches max retry count ', async () => {
      savedTransaction.retries = '5';
      data = { [key]: savedTransaction };
      await retryTransaction(
        contract,
        redis.redis,
        transactionId,
        savedTransaction
      );
      expect(data[key]).toBe(undefined);
    });
  });
});
