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
  Network,
  BlockListener,
  BlockEvent,
  TransactionEvent,
} from 'fabric-network';
import { Redis } from 'ioredis';
import * as config from './config';
import { logger } from './logger';
import { storeTransactionDetails, clearTransactionDetails } from './redis';
import {
  AssetExistsError,
  AssetNotFoundError,
  TransactionError,
  TransactionNotFoundError,
} from './errors';

export const getNetwork = async (gateway: Gateway): Promise<Network> => {
  const network = await gateway.getNetwork(config.channelName);
  return network;
};

export const getGateway = async (): Promise<Gateway> => {
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

  return gateway;
};

export const getContracts = async (
  network: Network
): Promise<{ contract: Contract; qscc: Contract }> => {
  const contract = network.getContract(config.chaincodeName);
  const qscc = network.getContract('qscc');
  return { contract, qscc };
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
      logger.debug(
        'Stopped listening for transaction %s events',
        transactionId
      );

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
    txn.setEventHandler(createDeferredEventHandler(redis));

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
