/*
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  CommitListener,
  Contract,
  DefaultEventHandlerStrategies,
  DefaultQueryHandlerStrategies,
  Gateway,
  GatewayOptions,
  TxEventHandler,
  TxEventHandlerFactory,
  Wallets,
} from 'fabric-network';
import { Redis } from 'ioredis';
import * as config from './config';
import { logger } from './logger';

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

  await gateway.connect(config.connectionProfile, connectOptions);

  const network = await gateway.getNetwork(config.channelName);
  const contract = network.getContract(config.chaincodeName);

  return contract;
};

export const createDeferredEventHandler = (
  redis: Redis
): TxEventHandlerFactory => {
  return (transactionId, network): TxEventHandler => {
    // TODO would like to store the transaction details here
    // but doesn't seem possible to use await or handle errors
    // in the TxEventHandlerFactory :(

    const mspId = network.getGateway().getIdentity().mspId;
    const peers = network.getChannel().getEndorsers(mspId);

    const options = Object.assign(
      {
        commitTimeout: 30,
      },
      network.getGateway().getOptions().eventHandlerOptions
    );

    const removeCommitListener = async () => {
      network.removeCommitListener(listener);
      logger.debug('Stopped listening for transaction %s events', transactionId);

      const txnExists = await redis.exists(transactionId);
      if (txnExists) {
        logger.warn(
          'Transaction %s was not successfully committed',
          transactionId
        );
      }
    };

    const listener: CommitListener = async (error, event) => {
      if (error) {
        logger.error(error, 'Commit error for transaction %s', transactionId);
      }

      if (event && event.isValid) {
        logger.debug('Transaction %s successfully committed', transactionId);

        await clearTransactionDetails(redis, transactionId);
        await removeCommitListener();
      }
    };

    const deferredEventHandler: TxEventHandler = {
      startListening: async () => {
        logger.debug('Setting timeout for %d ms', options.commitTimeout * 1000);
        setTimeout(async () => {
          logger.debug(
            'Timeout listening for transaction %s events',
            transactionId
          );
          await removeCommitListener();
        }, options.commitTimeout * 1000);

        await network.addCommitListener(listener, peers, transactionId);
        logger.debug('Listening for transaction %s events', transactionId);
      },
      waitForEvents: async () => {
        // No-op
      },
      cancelListening: async () => {
        // TODO this is what the doc says, but is it true?!
        logger.warn(
          'Submission of transaction %s to the orderer failed',
          transactionId
        );
        await removeCommitListener();
      },
    };

    return deferredEventHandler;
  };
};

export const startRetryLoop = (contract: Contract, redis: Redis): void => {
  setInterval(
    async (redis) => {
      try {
        const pendingTransactionCount = await (redis as Redis).zcard(
          'index:txn:timestamp'
        );
        logger.debug('Transactions awaiting retry: %d', pendingTransactionCount);

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

const retryTransaction = async (
  contract: Contract,
  redis: Redis,
  transactionId: string,
  savedTransaction: Record<string, string>
) => {
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
      logger.debug('Transaction %s has already been committed', transactionId);
      await clearTransactionDetails(redis, transactionId);
    } else {
      // TODO check for retry limit and update timestamp
      logger.warn(
        err,
        'Retry %d failed for transaction %s',
        savedTransaction.retries,
        transactionId
      );
      await (redis as Redis).hincrby(`txn:${transactionId}`, 'retries', 1);
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

// TODO move these to redis.ts?

export const storeTransactionDetails = async (
  redis: Redis,
  transactionId: string,
  transactionState: Buffer,
  transactionArgs: string,
  timestamp: number
): Promise<void> => {
  const key = `txn:${transactionId}`;
  logger.debug(
    'Storing transaction details. Key: %s State: %s Args: %s Timestamp: %d',
    key,
    transactionState,
    transactionArgs,
    timestamp
  );
  await redis
    .multi()
    .hset(
      key,
      'state',
      transactionState,
      'args',
      transactionArgs,
      'timestamp',
      timestamp,
      'retries',
      '0'
    )
    .zadd('index:txn:timestamp', timestamp, transactionId)
    .exec();
};

export const clearTransactionDetails = async (
  redis: Redis,
  transactionId: string
): Promise<void> => {
  const key = `txn:${transactionId}`;
  logger.debug('Removing transaction details. Key: %s', key);
  try {
    await redis
      .multi()
      .del(key)
      .zrem('index:txn:timestamp', transactionId)
      .exec();
  } catch (err) {
    logger.error(err, 'Error remove saved transaction state for transaction ID %s', transactionId);
  }
};
