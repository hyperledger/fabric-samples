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
  TimeoutError,
  Transaction,
  Wallet,
} from 'fabric-network';
import * as config from './config';
import { logger } from './logger';
import {
  handleError,
  isContractError,
  isDuplicateTransactionError,
} from './errors';
import * as protos from 'fabric-protos';
import { Job } from 'bullmq';
import { JobData, JobResult, updateJobData } from './jobs';

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
 * Process a submit transaction request from the job queue
 *
 * For this sample transactions are retried if they fail with any error,
 * except for errors from the smart contract, or duplicate transaction
 * errors
 *
 * You might decide to retry transactions which fail with specific errors
 * instead, for example:
 *   MVCC_READ_CONFLICT
 *   PHANTOM_READ_CONFLICT
 *   ENDORSEMENT_POLICY_FAILURE
 *   CHAINCODE_VERSION_CONFLICT
 *   EXPIRED_CHAINCODE
 */
export const processSubmitTransactionJob = async (
  contracts: Map<string, Contract>,
  job: Job<JobData, JobResult>
): Promise<JobResult> => {
  logger.debug({ jobId: job.id, jobName: job.name }, 'Processing job');

  const contract = contracts.get(job.data.mspid);
  if (contract === undefined) {
    logger.error(
      { jobId: job.id, jobName: job.name },
      'Contract not found for MSP ID %s',
      job.data.mspid
    );

    // Retrying will not work, so give up with an unsuccessful result
    return {
      transactionError: undefined,
      transactionPayload: undefined,
    };
  }

  let transaction: Transaction;
  if (job.data.transactionState) {
    const savedState = job.data.transactionState;
    logger.debug(
      {
        jobId: job.id,
        jobName: job.name,
        savedState,
      },
      'Using previously saved transaction state'
    );

    transaction = contract.deserializeTransaction(savedState);
  } else {
    logger.debug(
      {
        jobId: job.id,
        jobName: job.name,
      },
      'Using new transaction'
    );

    transaction = contract.createTransaction(job.data.transactionName);
    await updateJobData(job, transaction);
  }

  try {
    logger.debug(
      {
        jobId: job.id,
        jobName: job.name,
        transactionId: transaction.getTransactionId(),
      },
      'Submitting transaction'
    );
    const args = job.data.transactionArgs;
    const payload = await submitTransaction(transaction, ...args);

    return {
      transactionError: undefined,
      transactionPayload: payload,
    };
  } catch (err) {
    if (
      err instanceof Error &&
      (isContractError(err) || isDuplicateTransactionError(err))
    ) {
      logger.error(
        { jobId: job.id, jobName: job.name, err },
        'Fatal transaction error occurred'
      );

      // Return a job result to stop retrying
      return {
        transactionError: err.toString(),
        transactionPayload: undefined,
      };
    } else {
      logger.warn(
        { jobId: job.id, jobName: job.name, err },
        'Retryable transaction error occurred'
      );

      // The original transaction may eventually get committed in the case of
      // a timeout error, so keep the same transaction ID to protect against
      // unintended duplicate transactions
      if (!(err instanceof TimeoutError)) {
        logger.debug(
          { jobId: job.id, jobName: job.name },
          'Clearing saved transaction state'
        );
        await updateJobData(job, undefined);
      }

      // Rethrow the error to keep retrying
      throw err;
    }
  }
};

/*
 * Evaluate a transaction and handle any errors
 */
export const evatuateTransaction = async (
  contract: Contract,
  transactionName: string,
  ...transactionArgs: string[]
): Promise<Buffer> => {
  const transaction = contract.createTransaction(transactionName);
  const transactionId = transaction.getTransactionId();
  logger.trace({ transaction }, 'Evaluating transaction');

  try {
    const payload = await transaction.evaluate(...transactionArgs);
    logger.trace(
      { transactionId: transactionId, payload: payload.toString() },
      'Evaluate transaction response received'
    );
    return payload;
  } catch (err) {
    throw handleError(transactionId, err);
  }
};

/*
 * Submit a transaction and handle any errors
 */
export const submitTransaction = async (
  transaction: Transaction,
  ...transactionArgs: string[]
): Promise<Buffer> => {
  logger.trace({ transaction }, 'Submitting transaction');
  const txnId = transaction.getTransactionId();

  try {
    const payload = await transaction.submit(...transactionArgs);
    logger.trace(
      { transactionId: txnId, payload: payload.toString() },
      'Submit transaction response received'
    );
    return payload;
  } catch (err) {
    throw handleError(txnId, err);
  }
};

/*
 * Get the validation code of the specified transaction
 */
export const getTransactionValidationCode = async (
  qsccContract: Contract,
  transactionId: string
): Promise<string> => {
  const data = await evatuateTransaction(
    qsccContract,
    'GetTransactionByID',
    config.channelName,
    transactionId
  );

  const processedTransaction = protos.protos.ProcessedTransaction.decode(data);
  const validationCode =
    protos.protos.TxValidationCode[processedTransaction.validationCode];

  logger.debug({ transactionId }, 'Validation code: %s', validationCode);
  return validationCode;
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
