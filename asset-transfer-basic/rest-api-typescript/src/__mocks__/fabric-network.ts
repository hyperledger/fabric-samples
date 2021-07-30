/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { mocked } from 'ts-jest/utils';

type FabricNetworkModule = jest.Mocked<typeof import('fabric-network')>;

const {
  DefaultEventHandlerStrategies,
  DefaultQueryHandlerStrategies,
  Gateway,
  Wallet,
  Wallets,
}: FabricNetworkModule = jest.createMockFromModule('fabric-network');

mocked(Wallets.newInMemoryWallet).mockResolvedValue(
  new Wallet({
    get: jest.fn(),
    list: jest.fn(),
    put: jest.fn(),
    remove: jest.fn(),
  })
);

mocked(Gateway.prototype.getNetwork).mockResolvedValue({
  getGateway: jest.fn(),
  getContract: jest.fn(),
  getChannel: jest.fn(),
  addCommitListener: jest.fn(),
  removeCommitListener: jest.fn(),
  addBlockListener: jest.fn(),
  removeBlockListener: jest.fn(),
});
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
  Gateway,
  Wallets,
  getMockedNetwork,
};
