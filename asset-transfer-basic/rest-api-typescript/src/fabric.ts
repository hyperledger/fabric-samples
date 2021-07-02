/*
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  DefaultQueryHandlerStrategies,
  Gateway,
  GatewayOptions,
  Contract,
  Wallets,
} from 'fabric-network';

import * as config from './config';

export const getContract = async (): Promise<Contract> => {
  const wallet = await Wallets.newInMemoryWallet();

  const x509Identity = {
    credentials: {
      certificate: config.certificate,
      privateKey: config.privateKey,
    },
    mspId: config.mspId,
    type: 'X.509',
  };
  await wallet.put(config.identityName, x509Identity);

  const gateway = new Gateway();

  const connectOptions: GatewayOptions = {
    wallet,
    identity: config.identityName,
    discovery: { enabled: true, asLocalhost: config.asLocalHost },
    queryHandlerOptions: {
      timeout: 3,
      strategy: DefaultQueryHandlerStrategies.PREFER_MSPID_SCOPE_ROUND_ROBIN,
    },
  };

  await gateway.connect(config.connectionProfile, connectOptions);

  const network = await gateway.getNetwork(config.channelName);
  const contract = network.getContract(config.chaincodeName);

  return contract;
};
