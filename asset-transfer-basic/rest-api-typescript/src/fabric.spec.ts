/*
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  createGateway,
  createWallet,
  getContracts,
  getNetwork,
  evatuateTransaction,
  submitTransaction,
  getBlockHeight,
  startRetryLoop,
  blockEventHandler,
} from './fabric';
import * as config from './config';

import {
  AssetExistsError,
  AssetNotFoundError,
  TransactionError,
  TransactionNotFoundError,
} from './errors';

import {
  BlockEvent,
  Contract,
  Gateway,
  GatewayOptions,
  Network,
  Transaction,
  TransactionEvent,
  Wallet,
} from 'fabric-network';

import * as fabricProtos from 'fabric-protos';

import { MockProxy, mock } from 'jest-mock-extended';
import IORedis, { Redis } from 'ioredis';
import Long from 'long';

jest.mock('./config');
jest.mock('ioredis', () => require('ioredis-mock/jest'));

describe('Fabric', () => {
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

  describe('createWallet', () => {
    it('creates a wallet containing identities for both orgs', async () => {
      const wallet = await createWallet();

      expect(await wallet.list()).toStrictEqual(['Org1MSP', 'Org2MSP']);
    });
  });

  describe('createGateway', () => {
    it('creates a Gateway and connects using the provided arguments', async () => {
      const connectionProfile = config.connectionProfileOrg1;
      const identity = config.mspIdOrg1;
      const mockWallet = mock<Wallet>();

      const gateway = await createGateway(
        connectionProfile,
        identity,
        mockWallet
      );

      expect(gateway.connect).toBeCalledWith(
        connectionProfile,
        expect.objectContaining<GatewayOptions>({
          wallet: mockWallet,
          identity,
          discovery: expect.any(Object),
          eventHandlerOptions: expect.any(Object),
          queryHandlerOptions: expect.any(Object),
        })
      );
    });
  });

  describe('getNetwork', () => {
    it('gets a Network instance for the required channel from the Gateway', async () => {
      const mockGateway = mock<Gateway>();

      await getNetwork(mockGateway);

      expect(mockGateway.getNetwork).toHaveBeenCalledWith(config.channelName);
    });
  });

  describe('getContracts', () => {
    it('gets the asset and qscc contracts from the network', async () => {
      const mockBasicContract = mock<Contract>();
      const mockSystemContract = mock<Contract>();
      const mockNetwork = mock<Network>();
      mockNetwork.getContract
        .calledWith(config.chaincodeName)
        .mockReturnValue(mockBasicContract);
      mockNetwork.getContract
        .calledWith('qscc')
        .mockReturnValue(mockSystemContract);

      const contracts = await getContracts(mockNetwork);

      expect(contracts).toStrictEqual({
        assetContract: mockBasicContract,
        qsccContract: mockSystemContract,
      });
    });
  });

  describe('startRetryLoop', () => {
    let redis: Redis;
    let mockTransaction: MockProxy<Transaction>;
    let mockContract: MockProxy<Contract>;
    let mockContracts: Map<string, Contract>;

    const flushPromises = () => {
      jest.useRealTimers();
      return new Promise((resolve) => setImmediate(resolve));
    };

    beforeEach(() => {
      const redisOptions = {
        port: config.redisPort,
        host: config.redisHost,
        username: config.redisUsername,
        password: config.redisPassword,
      };

      redis = new IORedis(redisOptions) as unknown as Redis;

      mockTransaction = mock<Transaction>();
      mockTransaction.submit
        .mockResolvedValue(Buffer.from('MOCK PAYLOAD'))
        .mockName('submit');
      mockContract = mock<Contract>();
      mockContract.deserializeTransaction.mockReturnValue(mockTransaction);
      mockContracts = new Map<string, Contract>();
      mockContracts.set(mockMspId, mockContract);

      jest.useFakeTimers();
    });

    afterEach(() => {
      jest.useRealTimers();
    });

    it('starts a retry loop which does nothing if there are no saved transaction details', async () => {
      const getContractSpy = jest.spyOn(mockContracts, 'get');

      startRetryLoop(mockContracts, redis);
      jest.runOnlyPendingTimers();
      await flushPromises();

      expect(getContractSpy).not.toBeCalled();
    });

    it('starts a retry loop which clears the saved details after succesfully retrying a transaction', async () => {
      addMockTransationDetails(redis);

      startRetryLoop(mockContracts, redis);
      jest.runOnlyPendingTimers();
      await flushPromises();

      expect(mockContract.deserializeTransaction).toBeCalledWith(mockState);
      expect(mockTransaction.submit).toBeCalledWith(
        'test111',
        'red',
        400,
        'Jean',
        101
      );

      const index = await redis.zrange('index:txn:timestamp', 0, -1);
      expect(index).toStrictEqual([]);
    });

    it('starts a retry loop which increments the retry count when a transaction fails', async () => {
      addMockTransationDetails(redis);
      mockTransaction.submit.mockRejectedValue(new Error('MOCK ERROR'));

      startRetryLoop(mockContracts, redis);
      jest.runOnlyPendingTimers();
      await flushPromises();

      expect(mockContract.deserializeTransaction).toBeCalledWith(mockState);
      expect(mockTransaction.submit).toBeCalledWith(
        'test111',
        'red',
        400,
        'Jean',
        101
      );

      const index = await redis.zrange('index:txn:timestamp', 0, -1);
      expect(index).toStrictEqual([
        '0ae62c01e4c4b112c3f3954a2f11243da76778e46df9ad2783bcbafc79652b95',
      ]);

      const savedTransaction = await (redis as Redis).hgetall(mockKey);
      expect(savedTransaction.retries).toBe('1');
    });

    it('starts a retry loop which clears the saved details when a transaction fails as a duplicate', async () => {
      addMockTransationDetails(redis);
      const mockDuplicateTransactionError = new Error('MOCK ERROR');
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (mockDuplicateTransactionError as any).errors = [
        {
          endorsements: [
            {
              details: 'duplicate transaction found',
            },
          ],
        },
      ];
      mockTransaction.submit.mockRejectedValue(mockDuplicateTransactionError);

      startRetryLoop(mockContracts, redis);
      jest.runOnlyPendingTimers();
      await flushPromises();

      expect(mockContract.deserializeTransaction).toBeCalledWith(mockState);
      expect(mockTransaction.submit).toBeCalledWith(
        'test111',
        'red',
        400,
        'Jean',
        101
      );

      const index = await redis.zrange('index:txn:timestamp', 0, -1);
      expect(index).toStrictEqual([]);
    });

    it('starts a retry loop which clears the saved details when a transaction fails the final attempt', async () => {
      addMockTransationDetails(redis);
      await (redis as Redis).hincrby(mockKey, 'retries', 5);
      mockTransaction.submit.mockRejectedValue(new Error('MOCK ERROR'));

      startRetryLoop(mockContracts, redis);
      jest.runOnlyPendingTimers();
      await flushPromises();

      expect(mockContract.deserializeTransaction).toBeCalledWith(mockState);
      expect(mockTransaction.submit).toBeCalledWith(
        'test111',
        'red',
        400,
        'Jean',
        101
      );

      const index = await redis.zrange('index:txn:timestamp', 0, -1);
      expect(index).toStrictEqual([]);
    });

    it('starts a retry loop which clears the saved details when no contract exist for the org', async () => {
      addMockTransationDetails(redis);
      mockContracts = new Map<string, Contract>();
      startRetryLoop(mockContracts, redis);
      jest.runOnlyPendingTimers();
      await flushPromises();

      const index = await redis.zrange('index:txn:timestamp', 0, -1);
      expect(index).toStrictEqual([]);
    });
  });

  describe('evatuateTransaction', () => {
    const mockPayload = Buffer.from('MOCK PAYLOAD');
    let mockTransaction: MockProxy<Transaction>;
    let mockContract: MockProxy<Contract>;

    beforeEach(() => {
      mockTransaction = mock<Transaction>();
      mockTransaction.evaluate.mockResolvedValue(mockPayload);
      mockContract = mock<Contract>();
      mockContract.createTransaction
        .calledWith('txn')
        .mockReturnValue(mockTransaction);
    });

    it('gets the result of evaluating a transaction', async () => {
      const result = await evatuateTransaction(
        mockContract,
        'txn',
        'arga',
        'argb'
      );
      expect(result.toString()).toBe(mockPayload.toString());
    });

    it('throws an AssetExistsError an asset already exists error occurs', async () => {
      mockTransaction.evaluate.mockRejectedValue(
        new Error('The asset JSCHAINCODE already exists')
      );

      await expect(async () => {
        await evatuateTransaction(mockContract, 'txn', 'arga', 'argb');
      }).rejects.toThrow(AssetExistsError);
    });

    it('throws an AssetNotFoundError if an asset does not exist error occurs', async () => {
      mockTransaction.evaluate.mockRejectedValue(
        new Error('The asset JSCHAINCODE does not exist')
      );

      await expect(async () => {
        await evatuateTransaction(mockContract, 'txn', 'arga', 'argb');
      }).rejects.toThrow(AssetNotFoundError);
    });

    it('throws a TransactionNotFoundError if a transaction not found error occurs', async () => {
      mockTransaction.evaluate.mockRejectedValue(
        new Error(
          'Failed to get transaction with id txn, error Entry not found in index'
        )
      );

      await expect(async () => {
        await evatuateTransaction(mockContract, 'txn', 'arga', 'argb');
      }).rejects.toThrow(TransactionNotFoundError);
    });

    it('throws a TransactionError for other errors', async () => {
      mockTransaction.evaluate.mockRejectedValue(new Error('MOCK ERROR'));

      await expect(async () => {
        await evatuateTransaction(mockContract, 'txn', 'arga', 'argb');
      }).rejects.toThrow(TransactionError);
    });
  });

  describe('submitTransaction', () => {
    let redis: Redis;
    const mockPayload = Buffer.from('MOCK PAYLOAD');
    let mockTransaction: MockProxy<Transaction>;
    let mockContract: MockProxy<Contract>;

    beforeEach(async () => {
      const redisOptions = {
        port: config.redisPort,
        host: config.redisHost,
        username: config.redisUsername,
        password: config.redisPassword,
      };

      redis = new IORedis(redisOptions) as unknown as Redis;

      mockTransaction = mock<Transaction>();
      mockTransaction.submit.mockResolvedValue(mockPayload);
      mockTransaction.getTransactionId.mockReturnValue('MOCK TXN ID');
      mockTransaction.serialize.mockReturnValue(Buffer.from('MOCK TXN STATE'));
      mockContract = mock<Contract>();
      mockContract.createTransaction
        .calledWith('txn')
        .mockReturnValue(mockTransaction);
    });

    it('gets the transaction ID of the submitted transaction', async () => {
      const result = await submitTransaction(
        mockContract,
        redis,
        'mspid',
        'txn',
        'arga',
        'argb'
      );
      expect(result).toBe('MOCK TXN ID');
    });

    it.each([
      'the asset GOCHAINCODE already exists',
      'Asset JAVACHAINCODE already exists',
      'The asset JSCHAINCODE already exists',
    ])(
      'throws an AssetExistsError an asset already exists error occurs: %s',
      async (msg) => {
        mockTransaction.submit.mockRejectedValue(new Error(msg));

        await expect(async () => {
          await submitTransaction(
            mockContract,
            redis,
            'mspid',
            'txn',
            'arga',
            'argb'
          );
        }).rejects.toThrow(AssetExistsError);
      }
    );

    it.each([
      'the asset GOCHAINCODE does not exist',
      'Asset JAVACHAINCODE does not exist',
      'The asset JSCHAINCODE does not exist',
    ])(
      'throws an AssetNotFoundError if an asset does not exist error occurs: %s',
      async (msg) => {
        mockTransaction.submit.mockRejectedValue(new Error(msg));

        await expect(async () => {
          await submitTransaction(
            mockContract,
            redis,
            'mspid',
            'txn',
            'arga',
            'argb'
          );
        }).rejects.toThrow(AssetNotFoundError);
      }
    );

    it('throws a TransactionNotFoundError if a transaction not found error occurs', async () => {
      mockTransaction.submit.mockRejectedValue(
        new Error(
          'Failed to get transaction with id txn, error Entry not found in index'
        )
      );

      await expect(async () => {
        await submitTransaction(
          mockContract,
          redis,
          'mspid',
          'txn',
          'arga',
          'argb'
        );
      }).rejects.toThrow(TransactionNotFoundError);
    });

    it('throws a TransactionError for other errors', async () => {
      mockTransaction.submit.mockRejectedValue(new Error('MOCK ERROR'));

      await expect(async () => {
        await submitTransaction(
          mockContract,
          redis,
          'mspid',
          'txn',
          'arga',
          'argb'
        );
      }).rejects.toThrow(TransactionError);
    });
  });

  describe('blockEventHandler', () => {
    let redis: Redis;
    let mockIsValidGetter: jest.Mock<boolean, []>;
    let mockTransactionIdGetter: jest.Mock<string, []>;
    let mockTransactionEvent: MockProxy<TransactionEvent>;
    let mockBlockEvent: MockProxy<BlockEvent>;

    beforeEach(async () => {
      const redisOptions = {
        port: config.redisPort,
        host: config.redisHost,
        username: config.redisUsername,
        password: config.redisPassword,
      };

      redis = new IORedis(redisOptions) as unknown as Redis;
      addMockTransationDetails(redis);

      const baseMock = {};
      mockTransactionEvent = mock<TransactionEvent>(baseMock);
      mockIsValidGetter = jest.fn<boolean, []>();
      Object.defineProperty(baseMock, 'isValid', { get: mockIsValidGetter });
      mockTransactionIdGetter = jest.fn<string, []>();
      Object.defineProperty(baseMock, 'transactionId', {
        get: mockTransactionIdGetter,
      });

      mockBlockEvent = mock<BlockEvent>();
      mockBlockEvent.getTransactionEvents.mockReturnValue([
        mockTransactionEvent,
      ]);
    });

    it('clears saved details for valid transactions', async () => {
      const blockListener = blockEventHandler(redis);
      mockIsValidGetter.mockReturnValue(true);
      mockTransactionIdGetter.mockReturnValue(mockTransactionId);

      await blockListener(mockBlockEvent);

      const index = await redis.zrange('index:txn:timestamp', 0, -1);
      expect(index).toStrictEqual([]);
    });

    it('does not clear saved details for invalid transactions', async () => {
      const blockListener = blockEventHandler(redis);
      mockIsValidGetter.mockReturnValue(false);

      await blockListener(mockBlockEvent);

      const index = await redis.zrange('index:txn:timestamp', 0, -1);
      expect(index).toStrictEqual([
        '0ae62c01e4c4b112c3f3954a2f11243da76778e46df9ad2783bcbafc79652b95',
      ]);
    });
  });

  describe('getBlockHeight', () => {
    it('gets the current block height', async () => {
      const mockBlockchainInfoProto =
        fabricProtos.common.BlockchainInfo.create();
      mockBlockchainInfoProto.height = 42;
      const mockBlockchainInfoBuffer = Buffer.from(
        fabricProtos.common.BlockchainInfo.encode(
          mockBlockchainInfoProto
        ).finish()
      );
      const mockContract = mock<Contract>();
      mockContract.evaluateTransaction
        .calledWith('GetChainInfo', 'mychannel')
        .mockResolvedValue(mockBlockchainInfoBuffer);

      const result = (await getBlockHeight(mockContract)) as Long;
      expect(result.toInt()).toStrictEqual(42);
    });
  });
});
