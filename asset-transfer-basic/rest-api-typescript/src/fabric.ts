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
  Wallet,
} from 'fabric-network';
import { Redis } from 'ioredis';
import * as config from './config';
import { logger } from './logger';
import {
  storeTransactionDetails,
  getRetryTransactionDetails,
  clearTransactionDetails,
  incrementRetryCount,
  TransactionDetails,
} from './redis';
import { handleError, isDuplicateTransactionError } from './errors';
import protos from 'fabric-protos';

/*
 * Creates an in memory wallet to hold credentials for an Org1 and Org2 user
 *
 * In this sample there is a single user for each MSP ID to demonstrate how
 * a client app might submit transactions for different users
 *
 * Alternatively a REST server could use its own identity for all transactions,
 * or it could use credentials supplied in the REST requests
 */
export const createWallet = async (): Promise<Wallet> => {
  const wallet = await Wallets.newInMemoryWallet();

  const org1Identity = {
    credentials: {
      certificate: config.certificateOrg1,
      privateKey: config.privateKeyOrg1,
    },
    mspId: config.mspIdOrg1,
    type: 'X.509',
  };

  await wallet.put(config.mspIdOrg1, org1Identity);

  const org2Identity = {
    credentials: {
      certificate: config.certificateOrg2,
      privateKey: config.privateKeyOrg2,
    },
    mspId: config.mspIdOrg2,
    type: 'X.509',
  };

  await wallet.put(config.mspIdOrg2, org2Identity);

  return wallet;
};

/*
 * Create a Gateway connection
 *
 * Gateway instances can and should be reused rather than connecting to submit every transaction
 */
export const createGateway = async (
  connectionProfile: Record<string, unknown>,
  identity: string,
  wallet: Wallet
): Promise<Gateway> => {
  logger.debug({ connectionProfile, identity }, 'Configuring gateway');

  const gateway = new Gateway();

  const options: GatewayOptions = {
    wallet,
    identity,
    discovery: { enabled: true, asLocalhost: config.asLocalhost },
    eventHandlerOptions: {
      commitTimeout: config.commitTimeout,
      endorseTimeout: config.endorseTimeout,
      strategy: DefaultEventHandlerStrategies.PREFER_MSPID_SCOPE_ANYFORTX,
    },
    queryHandlerOptions: {
      timeout: config.queryTimeout,
      strategy: DefaultQueryHandlerStrategies.PREFER_MSPID_SCOPE_ROUND_ROBIN,
    },
  };

  await gateway.connect(connectionProfile, options);

  return gateway;
};

/*
 * Get the network which the asset transfer sample chaincode is running on
 *
 * In addion to getting the contract, the network will also be used to
 * start a block event listener
 */
export const getNetwork = async (gateway: Gateway): Promise<Network> => {
  const network = await gateway.getNetwork(config.channelName);
  return network;
};

/*
 * Get the asset transfer sample contract and the qscc system contract
 *
 * The system contract is used for the liveness REST endpoint
 */
export const getContracts = async (
  network: Network
): Promise<{ assetContract: Contract; qsccContract: Contract }> => {
  const assetContract = network.getContract(config.chaincodeName);
  const qsccContract = network.getContract('qscc');
  return { assetContract, qsccContract };
};

/*
 * Starts a timer to retry transactions at regular intervals
 *
 * Note: there is check for whether the transaction has successfully completed
 * since it could succeed between any check and the retry, so the additional
 * transaction to get the status is unlikely to be worthwhile
 */
export const startRetryLoop = (
  contracts: Map<string, Contract>,
  redis: Redis
): void => {
  const retryInterval = setInterval(
    async (contracts, redis) => {
      if (logger.isLevelEnabled('debug')) {
        try {
          const pendingTransactionCount = await (redis as Redis).zcard(
            'index:txn:timestamp'
          );
          logger.debug(
            '%d transactions awaiting retry',
            pendingTransactionCount
          );
        } catch (err) {
          logger.warn({ err }, 'Error getting pending transaction count');
        }
      }

      const savedTransaction = await getRetryTransactionDetails(redis);

      if (savedTransaction) {
        const contract = contracts.get(savedTransaction.mspId);

        if (contract) {
          await retryTransaction(contract, redis, savedTransaction);
        } else {
          clearTransactionDetails(redis, savedTransaction.transactionId);
          logger.error(
            'No contract found for %s to retry transaction %s',
            savedTransaction.mspId,
            savedTransaction.transactionId
          );
        }
      }
    },
    config.retryDelay,
    contracts,
    redis
  );

  retryInterval.unref();
};

/*
 * Evaluate a transaction and handle any errors
 */
export const evatuateTransaction = async (
  contract: Contract,
  transactionName: string,
  ...transactionArgs: string[]
): Promise<Buffer> => {
  const txn = contract.createTransaction(transactionName);
  const txnId = txn.getTransactionId();

  try {
    const payload = await txn.evaluate(...transactionArgs);
    logger.debug(
      { transactionId: txnId, payload: payload.toString() },
      'Evaluate transaction response received'
    );
    return payload;
  } catch (err) {
    throw handleError(txnId, err);
  }
};

/*
 * Submit a transaction and handle any errors
 *
 * Transaction details are saved before being submitted so that they can be
 * retried if any errors occur
 */
export const submitTransaction = async (
  contract: Contract,
  redis: Redis,
  mspId: string,
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
    await storeTransactionDetails(
      redis,
      txnId,
      mspId,
      txnState,
      txnArgs,
      timestamp
    );
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

/*
 * Retry a transaction
 *
 * The saved transaction details include a retry count which is used to ensure
 * failing transactions are not retried indefinitely
 */
const retryTransaction = async (
  contract: Contract,
  redis: Redis,
  savedTransaction: TransactionDetails
): Promise<void> => {
  logger.debug('Retrying transaction %s', savedTransaction.transactionId);

  try {
    const transaction = contract.deserializeTransaction(
      savedTransaction.transactionState
    );
    const args: string[] = JSON.parse(savedTransaction.transactionArgs);

    const payload = await transaction.submit(...args);
    logger.debug(
      {
        transactionId: savedTransaction.transactionId,
        payload: payload.toString(),
      },
      'Retry transaction response received'
    );

    await clearTransactionDetails(redis, savedTransaction.transactionId);
  } catch (err) {
    if (isDuplicateTransactionError(err)) {
      logger.warn(
        'Transaction %s has already been committed',
        savedTransaction.transactionId
      );
      await clearTransactionDetails(redis, savedTransaction.transactionId);
    } else {
      logger.warn(
        err,
        'Retry %d failed for transaction %s',
        savedTransaction.retries,
        savedTransaction.transactionId
      );

      if (savedTransaction.retries < config.maxRetryCount) {
        await incrementRetryCount(redis, savedTransaction.transactionId);
      } else {
        await clearTransactionDetails(redis, savedTransaction.transactionId);
      }
    }
  }
};

/*
 * Block event listener to handle successful transactions
 *
 * Transaction details are saved before being submitted so that they can be
 * retried, and this listener deletes those transaction details for any
 * successful transactions
 *
 * Transactions can be submitted using one of two identities however one one
 * of those identities is used to listen for block events
 */
export const blockEventHandler = (redis: Redis): BlockListener => {
  const blockListener = async (event: BlockEvent) => {
    logger.debug(
      { blockNumber: event.blockNumber.toString() },
      'Block event received'
    );
    const transactionEvents: Array<TransactionEvent> =
      event.getTransactionEvents();

    for (const event of transactionEvents) {
      if (event && event.isValid) {
        logger.debug('Remove transation with txnId %s', event.transactionId);
        await clearTransactionDetails(redis, event.transactionId);
      }
    }
  };

  return blockListener;
};

/*
 * Get the current block height
 *
 * This example of using a system contract is used for the liveness REST
 * endpoint
 */
export const getBlockHeight = async (
  qscc: Contract
): Promise<number | Long.Long> => {
  const data = await qscc.evaluateTransaction(
    'GetChainInfo',
    config.channelName
  );
  const info = protos.common.BlockchainInfo.decode(data);
  const blockHeight = info.height;
  logger.debug('Current block height: %d', blockHeight);
  return blockHeight;
};
