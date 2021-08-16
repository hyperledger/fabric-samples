/*
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  createGateway,
  createWallet,
  getContracts,
  getNetwork,
  retryTransaction,
} from './fabric';
import * as config from './config';

import IORedis from './__mocks__/IORedis';
import { Redis } from 'ioredis';
import {
  Contract,
  Gateway,
  GatewayOptions,
  Network,
  Transaction,
  Wallet,
} from 'fabric-network';

import { mock } from 'jest-mock-extended';

jest.mock('./config');
jest.mock('ioredis');

const redisOptions = {
  port: config.redisPort,
  host: config.redisHost,
  username: config.redisUsername,
  password: config.redisPassword,
};

const redis = new IORedis(redisOptions) as unknown as Redis;

describe('Fabric', () => {
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
        const mockTransaction = mock<Transaction>();
        mockTransaction.submit.mockRejectedValue('MOCKERROR');
        const mockContract = mock<Contract>();
        mockContract.deserializeTransaction.mockReturnValue(mockTransaction);

        savedTransaction.retries = '3';
        await retryTransaction(
          mockContract,
          redis,
          transactionId,
          savedTransaction
        );
        expect(redis.hincrby).toHaveBeenCalledTimes(1);
      });
    });

    describe('Transaction successful, check redis delete key func call  ', () => {
      it('call redis increment', async () => {
        const mockTransaction = mock<Transaction>();
        mockTransaction.submit.mockResolvedValue(Buffer.from('{}'));
        const mockContract = mock<Contract>();
        mockContract.deserializeTransaction.mockReturnValue(mockTransaction);

        savedTransaction.retries = '3';
        await retryTransaction(
          mockContract,
          redis,
          transactionId,
          savedTransaction
        );

        expect(redis.del).toHaveBeenCalledTimes(1);
      });
    });
  });
});
