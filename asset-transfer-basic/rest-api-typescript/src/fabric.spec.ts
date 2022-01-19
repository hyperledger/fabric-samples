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
  getTransactionValidationCode,
} from './fabric';
import * as config from './config';

import {
  AssetExistsError,
  AssetNotFoundError,
  TransactionNotFoundError,
} from './errors';

import {
  Contract,
  Gateway,
  GatewayOptions,
  Network,
  Transaction,
  Wallet,
} from 'fabric-network';

import * as fabricProtos from 'fabric-protos';

import { MockProxy, mock } from 'jest-mock-extended';
import Long from 'long';

jest.mock('./config');
jest.mock('fabric-network', () => {
  type FabricNetworkModule = jest.Mocked<typeof import('fabric-network')>;
  const originalModule: FabricNetworkModule =
    jest.requireActual('fabric-network');
  const mockModule: FabricNetworkModule =
    jest.createMockFromModule('fabric-network');

  return {
    __esModule: true,
    ...mockModule,
    Wallets: originalModule.Wallets,
  };
});
jest.mock('ioredis', () => require('ioredis-mock/jest'));

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

    it('throws an Error for other errors', async () => {
      mockTransaction.evaluate.mockRejectedValue(new Error('MOCK ERROR'));
      await expect(async () => {
        await evatuateTransaction(mockContract, 'txn', 'arga', 'argb');
      }).rejects.toThrow(Error);
    });
  });

  describe('submitTransaction', () => {
    let mockTransaction: MockProxy<Transaction>;

    beforeEach(() => {
      mockTransaction = mock<Transaction>();
    });

    it('gets the result of submitting a transaction', async () => {
      const mockPayload = Buffer.from('MOCK PAYLOAD');
      mockTransaction.submit.mockResolvedValue(mockPayload);

      const result = await submitTransaction(
        mockTransaction,
        'txn',
        'arga',
        'argb'
      );
      expect(result.toString()).toBe(mockPayload.toString());
    });

    it('throws an AssetExistsError an asset already exists error occurs', async () => {
      mockTransaction.submit.mockRejectedValue(
        new Error('The asset JSCHAINCODE already exists')
      );

      await expect(async () => {
        await submitTransaction(
          mockTransaction,
          'mspid',
          'txn',
          'arga',
          'argb'
        );
      }).rejects.toThrow(AssetExistsError);
    });

    it('throws an AssetNotFoundError if an asset does not exist error occurs', async () => {
      mockTransaction.submit.mockRejectedValue(
        new Error('The asset JSCHAINCODE does not exist')
      );

      await expect(async () => {
        await submitTransaction(
          mockTransaction,
          'mspid',
          'txn',
          'arga',
          'argb'
        );
      }).rejects.toThrow(AssetNotFoundError);
    });

    it('throws a TransactionNotFoundError if a transaction not found error occurs', async () => {
      mockTransaction.submit.mockRejectedValue(
        new Error(
          'Failed to get transaction with id txn, error Entry not found in index'
        )
      );

      await expect(async () => {
        await submitTransaction(
          mockTransaction,
          'mspid',
          'txn',
          'arga',
          'argb'
        );
      }).rejects.toThrow(TransactionNotFoundError);
    });

    it('throws an Error for other errors', async () => {
      mockTransaction.submit.mockRejectedValue(new Error('MOCK ERROR'));

      await expect(async () => {
        await submitTransaction(
          mockTransaction,
          'mspid',
          'txn',
          'arga',
          'argb'
        );
      }).rejects.toThrow(Error);
    });
  });

  describe('getTransactionValidationCode', () => {
    it('gets the validation code from a processed transaction', async () => {
      const processedTransactionProto =
        fabricProtos.protos.ProcessedTransaction.create();
      processedTransactionProto.validationCode =
        fabricProtos.protos.TxValidationCode.VALID;
      const processedTransactionBuffer = Buffer.from(
        fabricProtos.protos.ProcessedTransaction.encode(
          processedTransactionProto
        ).finish()
      );

      const mockTransaction = mock<Transaction>();
      mockTransaction.evaluate.mockResolvedValue(processedTransactionBuffer);
      const mockContract = mock<Contract>();
      mockContract.createTransaction
        .calledWith('GetTransactionByID')
        .mockReturnValue(mockTransaction);
      expect(await getTransactionValidationCode(mockContract, 'txn1')).toBe(
        'VALID'
      );
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
