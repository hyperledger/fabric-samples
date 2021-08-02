/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { mock } from 'jest-mock-extended';
import { Contract, Network, Transaction, WalletStore } from 'fabric-network';
import { mocked } from 'ts-jest/utils';
import * as fabricProtos from 'fabric-protos';

const mockAsset1 = {
  ID: 'asset1',
  Color: 'blue',
  Size: 5,
  Owner: 'Tomoko',
  AppraisedValue: 300,
};
const mockAsset1Buffer = Buffer.from(JSON.stringify(mockAsset1));

const mockAsset2 = {
  ID: 'asset2',
  Color: 'red',
  Size: 5,
  Owner: 'Brad',
  AppraisedValue: 400,
};

const mockAllAssetsBuffer = Buffer.from(
  JSON.stringify([mockAsset1, mockAsset2])
);

const mockBlockchainInfoProto = fabricProtos.common.BlockchainInfo.create();
mockBlockchainInfoProto.height = 42;
const mockBlockchainInfoBuffer = Buffer.from(
  fabricProtos.common.BlockchainInfo.encode(mockBlockchainInfoProto).finish()
);

const processedTransactionProto =
  fabricProtos.protos.ProcessedTransaction.create();
processedTransactionProto.validationCode =
  fabricProtos.protos.TxValidationCode.VALID;
const processedTransactionBuffer = Buffer.from(
  fabricProtos.protos.ProcessedTransaction.encode(
    processedTransactionProto
  ).finish()
);

type FabricNetworkModule = jest.Mocked<typeof import('fabric-network')>;

const {
  DefaultEventHandlerStrategies,
  DefaultQueryHandlerStrategies,
  Gateway,
  Wallet,
  Wallets,
}: FabricNetworkModule = jest.createMockFromModule('fabric-network');

const mockWalletStore = mock<WalletStore>();
mocked(Wallets.newInMemoryWallet).mockResolvedValue(
  new Wallet(mockWalletStore)
);

const mockAssetExistsTransaction = mock<Transaction>();
mockAssetExistsTransaction.evaluate
  .calledWith('asset1')
  .mockResolvedValue(Buffer.from('true'));
mockAssetExistsTransaction.evaluate
  .calledWith('asset3')
  .mockResolvedValue(Buffer.from('false'));

const mockReadAssetTransaction = mock<Transaction>();
mockReadAssetTransaction.evaluate
  .calledWith('asset1')
  .mockResolvedValue(mockAsset1Buffer);
mockReadAssetTransaction.evaluate
  .calledWith('asset3')
  .mockRejectedValue(new Error('the asset asset3 does not exist'));

const mockCreateAssetTransaction = mock<Transaction>();
mockCreateAssetTransaction.getTransactionId.mockReturnValue('txn1');
mockCreateAssetTransaction.submit
  .calledWith('asset1')
  .mockRejectedValue(
    new Error(
      'No valid responses from any peers. Errors:\n    peer=peer0.org1.example.com:7051, status=500, message=the asset asset1 already exists\n    peer=peer0.org2.example.com:9051, status=500, message=the asset asset3 already exists'
    )
  );

// NOTE: only the second mocked GetAllAssets with return no assets
// TODO find a better alternative so that test order does not matter
const mockGetAllAssetsTransaction = mock<Transaction>();
mockGetAllAssetsTransaction.evaluate
  .mockResolvedValueOnce(Buffer.from(''))
  .mockResolvedValueOnce(mockAllAssetsBuffer);

const mockUpdateAssetTransaction = mock<Transaction>();
mockUpdateAssetTransaction.getTransactionId.mockReturnValue('txn1');
mockUpdateAssetTransaction.submit
  .calledWith('asset3')
  .mockRejectedValue(
    new Error(
      'No valid responses from any peers. Errors:\n    peer=peer0.org1.example.com:7051, status=500, message=the asset asset3 does not exist\n    peer=peer0.org2.example.com:9051, status=500, message=the asset asset3 does not exist'
    )
  );

const mockTransferAssetTransaction = mock<Transaction>();
mockTransferAssetTransaction.getTransactionId.mockReturnValue('txn1');
mockTransferAssetTransaction.submit
  .calledWith('asset3')
  .mockRejectedValue(
    new Error(
      'No valid responses from any peers. Errors:\n    peer=peer0.org1.example.com:7051, status=500, message=the asset asset3 does not exist\n    peer=peer0.org2.example.com:9051, status=500, message=the asset asset3 does not exist'
    )
  );

const mockDeleteAssetTransaction = mock<Transaction>();
mockDeleteAssetTransaction.getTransactionId.mockReturnValue('txn1');
mockDeleteAssetTransaction.submit
  .calledWith('asset3')
  .mockRejectedValue(
    new Error(
      'No valid responses from any peers. Errors:\n    peer=peer0.org1.example.com:7051, status=500, message=the asset asset3 does not exist\n    peer=peer0.org2.example.com:9051, status=500, message=the asset asset3 does not exist'
    )
  );

const mockBasicContract = mock<Contract>();
mockBasicContract.createTransaction
  .calledWith('AssetExists')
  .mockReturnValue(mockAssetExistsTransaction);
mockBasicContract.createTransaction
  .calledWith('ReadAsset')
  .mockReturnValue(mockReadAssetTransaction);
mockBasicContract.createTransaction
  .calledWith('CreateAsset')
  .mockReturnValue(mockCreateAssetTransaction);
mockBasicContract.createTransaction
  .calledWith('GetAllAssets')
  .mockReturnValue(mockGetAllAssetsTransaction);
mockBasicContract.createTransaction
  .calledWith('UpdateAsset')
  .mockReturnValue(mockUpdateAssetTransaction);
mockBasicContract.createTransaction
  .calledWith('TransferAsset')
  .mockReturnValue(mockTransferAssetTransaction);
mockBasicContract.createTransaction
  .calledWith('DeleteAsset')
  .mockReturnValue(mockDeleteAssetTransaction);

const mockGetTransactionByIDTransaction = mock<Transaction>();
mockGetTransactionByIDTransaction.evaluate
  .calledWith('mychannel', 'txn1')
  .mockResolvedValue(processedTransactionBuffer);
mockGetTransactionByIDTransaction.evaluate
  .calledWith('mychannel', 'txn3')
  .mockRejectedValue(
    new Error(
      'Failed to get transaction with id txn3, error Entry not found in index'
    )
  );

const mockSystemContract = mock<Contract>();
mockSystemContract.evaluateTransaction
  .calledWith('GetChainInfo')
  .mockResolvedValue(mockBlockchainInfoBuffer);
mockSystemContract.createTransaction
  .calledWith('GetTransactionByID')
  .mockReturnValue(mockGetTransactionByIDTransaction);

const mockNetwork = mock<Network>();
mockNetwork.getContract.calledWith('basic').mockReturnValue(mockBasicContract);
mockNetwork.getContract.calledWith('qscc').mockReturnValue(mockSystemContract);

mocked(Gateway.prototype.getNetwork).mockResolvedValue(mockNetwork);

// TODO remove this and use simpler mocks in fabric spec tests
const getMockedNetwork = (getContract = jest.fn()) => {
  return mocked(Gateway.prototype.getNetwork).mockResolvedValue({
    getGateway: jest.fn(),
    getContract,
    getChannel: jest.fn(),
    addCommitListener: jest.fn(),
    removeCommitListener: jest.fn(),
    addBlockListener: jest.fn(),
    removeBlockListener: jest.fn(),
  });
};

export {
  DefaultEventHandlerStrategies,
  DefaultQueryHandlerStrategies,
  Contract,
  Gateway,
  Wallets,
  getMockedNetwork,
};
