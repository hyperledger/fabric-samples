/*
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  Contract,
  DefaultEventHandlerStrategies,
  DefaultQueryHandlerStrategies,
  Gateway,
  GatewayOptions,
  Wallets,
  Network,
  BlockListener,
  BlockEvent,
  TransactionEvent,
} from 'fabric-network';
import { Request } from 'express';
import { Redis } from 'ioredis';
import * as config from './config';
import { logger } from './logger';
import {
  storeTransactionDetails,
  clearTransactionDetails,
  incrementRetryCount,
} from './redis';
import {
  AssetExistsError,
  AssetNotFoundError,
  TransactionError,
  TransactionNotFoundError,
} from './errors';
import fabproto6 from 'fabric-protos';

export const getNetwork = async (gateway: Gateway): Promise<Network> => {
  const network = await gateway.getNetwork(config.channelName);
  return network;
};

interface FabricConfigType {
  identityName: string;
  mspId: string;
  connectionProfile: { [key: string]: any };
  certificate: string;
  privateKey: string;
}

const ORG1_CONFIG = {
  identityName: config.identityNameOrg1,
  mspId: config.mspIdOrg1,
  connectionProfile: config.connectionProfileOrg1,
  certificate: config.certificateOrg1,
  privateKey: config.privateKeyOrg1,
};

const ORG2_CONFIG = {
  identityName: config.identityNameOrg2,
  mspId: config.mspIdOrg2,
  connectionProfile: config.connectionProfileOrg2,
  certificate: config.certificateOrg2,
  privateKey: config.privateKeyOrg2,
};

const FabricDataMapper: { [key: string]: FabricConfigType } = {
  [config.identityNameOrg1]: ORG1_CONFIG,
  [config.identityNameOrg2]: ORG2_CONFIG,
};

export const getGateway = async (org: string): Promise<Gateway> => {
  const fabricConfig = FabricDataMapper[org];
  logger.debug('Configuring fabric gateway for %s', org);
  const wallet = await Wallets.newInMemoryWallet();

  const x509Identity = {
    credentials: {
      certificate: fabricConfig.certificate,
      privateKey: fabricConfig.privateKey,
    },
    mspId: fabricConfig.mspId,
    type: 'X.509',
  };
  await wallet.put(fabricConfig.identityName, x509Identity);

  const gateway = new Gateway();

  const connectOptions: GatewayOptions = {
    wallet,
    identity: fabricConfig.identityName,
    discovery: { enabled: true, asLocalhost: config.asLocalHost },
    eventHandlerOptions: {
      commitTimeout: config.commitTimeout,
      endorseTimeout: config.endorseTimeout,
      strategy: DefaultEventHandlerStrategies.PREFER_MSPID_SCOPE_ANYFORTX,
    },
    queryHandlerOptions: {
      timeout: 3,
      strategy: DefaultQueryHandlerStrategies.PREFER_MSPID_SCOPE_ROUND_ROBIN,
    },
  };

  await gateway.connect(fabricConfig.connectionProfile, connectOptions);
  return gateway;
};

export const getContracts = async (
  network: Network
): Promise<{ contract: Contract; qscc: Contract }> => {
  const contract = network.getContract(config.chaincodeName);
  const qscc = network.getContract('qscc');
  return { contract, qscc };
};

export const startRetryLoop = (contract: Contract, redis: Redis): void => {
  setInterval(
    async (redis) => {
      try {
        const pendingTransactionCount = await (redis as Redis).zcard(
          'index:txn:timestamp'
        );
        logger.debug(
          'Transactions awaiting retry: %d',
          pendingTransactionCount
        );

        // TODO pick a random transaction instead to reduce chances of
        // clashing with other instances? Currently no zrandmember
        // command though...
        //   https://github.com/luin/ioredis/issues/1374
        const transactionIds = await (redis as Redis).zrange(
          'index:txn:timestamp',
          -1,
          -1
        );

        if (transactionIds.length > 0) {
          const transactionId = transactionIds[0];
          const savedTransaction = await (redis as Redis).hgetall(
            `txn:${transactionId}`
          );

          await retryTransaction(
            contract,
            redis,
            transactionId,
            savedTransaction
          );
        }
      } catch (err) {
        // TODO just log?
        logger.error(err, 'error getting saved transaction state');
      }
    },
    config.retryDelay,
    redis
  );
};

export const evatuateTransaction = async (
  contract: Contract,
  transactionName: string,
  ...transactionArgs: string[]
): Promise<Buffer> => {
  const txn = contract.createTransaction(transactionName);
  const txnId = txn.getTransactionId();

  try {
    return await txn.evaluate(...transactionArgs);
  } catch (err) {
    throw handleError(txnId, err);
  }
};

export const submitTransaction = async (
  contract: Contract,
  redis: Redis,
  transactionName: string,
  ...transactionArgs: string[]
): Promise<string> => {
  const txn = contract.createTransaction(transactionName);
  const txnId = txn.getTransactionId();
  const txnState = txn.serialize();
  const txnArgs = JSON.stringify(transactionArgs);
  const timestamp = Date.now();

  try {
    // Store the transaction details and set the event handler in case there
    // are problems later with commiting the transaction
    await storeTransactionDetails(redis, txnId, txnState, txnArgs, timestamp);
    txn.setEventHandler(DefaultEventHandlerStrategies.NONE);
    await txn.submit(...transactionArgs);
  } catch (err) {
    // If the transaction failed to endorse, there is no point attempting
    // to retry it later so clear the transaction details
    // TODO will this always catch endorsement errors or can they
    // arrive later?
    await clearTransactionDetails(redis, txnId);
    throw handleError(txnId, err);
  }

  return txnId;
};

// Unfortunately the chaincode samples do not use error codes, and the error
// message text is not the same for each implementation
const handleError = (transactionId: string, err: Error): Error => {
  // This regex needs to match the following error messages:
  //   "the asset %s already exists"
  //   "The asset ${id} already exists"
  //   "Asset %s already exists"
  const assetAlreadyExistsRegex = /([tT]he )?[aA]sset \w* already exists/g;
  const assetAlreadyExistsMatch = err.message.match(assetAlreadyExistsRegex);
  logger.debug(
    { message: err.message, result: assetAlreadyExistsMatch },
    'Checking for asset already exists message'
  );
  if (assetAlreadyExistsMatch) {
    return new AssetExistsError(assetAlreadyExistsMatch[0], transactionId);
  }

  // This regex needs to match the following error messages:
  //   "the asset %s does not exist"
  //   "The asset ${id} does not exist"
  //   "Asset %s does not exist"
  const assetDoesNotExistRegex = /([tT]he )?[aA]sset \w* does not exist/g;
  const assetDoesNotExistMatch = err.message.match(assetDoesNotExistRegex);
  logger.debug(
    { message: err.message, result: assetDoesNotExistMatch },
    'Checking for asset does not exist message'
  );
  if (assetDoesNotExistMatch) {
    return new AssetNotFoundError(assetDoesNotExistMatch[0], transactionId);
  }

  // This regex needs to match the following error messages:
  //   "Failed to get transaction with id %s, error Entry not found in index"
  const transactionDoesNotExistRegex =
    /Failed to get transaction with id [^,]*, error Entry not found in index/g;
  const transactionDoesNotExistMatch = err.message.match(
    transactionDoesNotExistRegex
  );
  logger.debug(
    { message: err.message, result: transactionDoesNotExistMatch },
    'Checking for transaction does not exist message'
  );
  if (transactionDoesNotExistMatch) {
    return new TransactionNotFoundError(
      transactionDoesNotExistMatch[0],
      transactionId
    );
  }

  logger.error(
    { transactionId: transactionId, error: err },
    'Unhandled transaction error'
  );
  return new TransactionError('Transaction error', transactionId);
};

export const retryTransaction = async (
  contract: Contract,
  redis: Redis,
  transactionId: string,
  savedTransaction: Record<string, string>
): Promise<void> => {
  logger.debug('Retrying transaction %s', transactionId);

  try {
    const transaction = contract.deserializeTransaction(
      Buffer.from(savedTransaction.state)
    );
    const args: string[] = JSON.parse(savedTransaction.args);

    await transaction.submit(...args);
    await clearTransactionDetails(redis, transactionId);
  } catch (err) {
    if (isDuplicateTransaction(err)) {
      logger.warn('Transaction %s has already been committed', transactionId);
      await clearTransactionDetails(redis, transactionId);
    } else {
      // TODO check for retry limit and update timestamp
      logger.warn(
        err,
        'Retry %d failed for transaction %s',
        savedTransaction.retries,
        transactionId
      );
      if (parseInt(savedTransaction.retries) < config.maxRetryCount) {
        await incrementRetryCount(redis, transactionId);
      } else {
        await clearTransactionDetails(redis, transactionId);
      }
    }
  }
};

const isDuplicateTransaction = (error: {
  errors: { endorsements: { details: string }[] }[];
}) => {
  // TODO this is horrible! Isn't it possible to check for TxValidationCode DUPLICATE_TXID somehow?
  try {
    const isDuplicateTxn = error?.errors?.some((err) =>
      err?.endorsements?.some((endorsement) =>
        endorsement?.details?.startsWith('duplicate transaction found')
      )
    );

    return isDuplicateTxn;
  } catch (err) {
    logger.warn(err, 'Error checking for duplicate transaction');
  }

  return false;
};

export const blockEventHandler = (redis: Redis): BlockListener => {
  const blockListner = async (event: BlockEvent) => {
    logger.debug('Block event received ');
    const transEvents: Array<TransactionEvent> = event.getTransactionEvents();

    for (const transEvent of transEvents) {
      if (transEvent && transEvent.isValid) {
        logger.debug(
          'Remove transation with txnId %s',
          transEvent.transactionId
        );
        await clearTransactionDetails(redis, transEvent.transactionId);
      }
    }
  };

  return blockListner;
};

export const getChainInfo = async (qscc: Contract): Promise<boolean> => {
  try {
    const data = await qscc.evaluateTransaction(
      'GetChainInfo',
      config.channelName
    );
    const info = fabproto6.common.BlockchainInfo.decode(data);
    const blockHeight = info.height.toString();
    logger.info('Current block height: %s', blockHeight);
    return true;
  } catch (e) {
    logger.error(e, 'Unable to get blockchain info');
    return false;
  }
};

export const getContractForOrg = (
  req: Request
): { contract: Contract; qscc: Contract } => {
  const user: { org: string } = req.user as { org: string };
  return req.app.get('fabric')[user.org as string].contracts;
};
