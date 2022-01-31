/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * This file contains all the error handling for Fabric transactions, including
 * whether a transaction should be retried.
 */

import { TimeoutError, TransactionError } from 'fabric-network';
import { logger } from './logger';

/**
 * Base type for errors from the smart contract.
 *
 * These errors will not be retried.
 */
export class ContractError extends Error {
  transactionId: string;

  constructor(message: string, transactionId: string) {
    super(message);
    Object.setPrototypeOf(this, ContractError.prototype);

    this.name = 'TransactionError';
    this.transactionId = transactionId;
  }
}

/**
 * Represents the error which occurs when the transaction being submitted or
 * evaluated is not implemented in a smart contract.
 */
export class TransactionNotFoundError extends ContractError {
  constructor(message: string, transactionId: string) {
    super(message, transactionId);
    Object.setPrototypeOf(this, TransactionNotFoundError.prototype);

    this.name = 'TransactionNotFoundError';
  }
}

/**
 * Represents the error which occurs in the basic asset transfer smart contract
 * implementation when an asset already exists.
 */
export class AssetExistsError extends ContractError {
  constructor(message: string, transactionId: string) {
    super(message, transactionId);
    Object.setPrototypeOf(this, AssetExistsError.prototype);

    this.name = 'AssetExistsError';
  }
}

/**
 * Represents the error which occurs in the basic asset transfer smart contract
 * implementation when an asset does not exist.
 */
export class AssetNotFoundError extends ContractError {
  constructor(message: string, transactionId: string) {
    super(message, transactionId);
    Object.setPrototypeOf(this, AssetNotFoundError.prototype);

    this.name = 'AssetNotFoundError';
  }
}

/**
 * Enumeration of possible retry actions.
 */
export enum RetryAction {
  /**
   * Transactions should be retried using the same transaction ID to protect
   * against duplicate transactions being committed if a timeout error occurs
   */
  WithExistingTransactionId,

  /**
   * Transactions which could not be committed due to other errors require a
   * new transaction ID when retrying
   */
  WithNewTransactionId,

  /**
   * Transactions that failed due to a duplicate transaction error, or errors
   * from the smart contract, should not be retried
   */
  None,
}

/**
 * Get the required transaction retry action for an error.
 *
 * For this sample transactions are considered retriable if they fail with any
 * error, *except* for duplicate transaction errors, or errors from the smart
 * contract.
 *
 * You might decide to retry transactions which fail with specific errors
 * instead, for example:
 *   - MVCC_READ_CONFLICT
 *   - PHANTOM_READ_CONFLICT
 *   - ENDORSEMENT_POLICY_FAILURE
 *   - CHAINCODE_VERSION_CONFLICT
 *   - EXPIRED_CHAINCODE
 */
export const getRetryAction = (err: unknown): RetryAction => {
  if (isDuplicateTransactionError(err) || err instanceof ContractError) {
    return RetryAction.None;
  } else if (err instanceof TimeoutError) {
    return RetryAction.WithExistingTransactionId;
  }

  return RetryAction.WithNewTransactionId;
};

/**
 * Type guard to make catching unknown errors easier
 */
export const isErrorLike = (err: unknown): err is Error => {
  return (
    err != undefined &&
    err != null &&
    typeof (err as Error).name === 'string' &&
    typeof (err as Error).message === 'string' &&
    ((err as Error).stack === undefined ||
      typeof (err as Error).stack === 'string')
  );
};

/**
 * Checks whether an error was caused by a duplicate transaction.
 *
 * This is ...painful.
 */
export const isDuplicateTransactionError = (err: unknown): boolean => {
  logger.debug({ err }, 'Checking for duplicate transaction error');

  if (err === undefined || err === null) return false;

  let isDuplicate;
  if (typeof (err as TransactionError).transactionCode === 'string') {
    // Checking whether a commit failure is caused by a duplicate transaction
    // is straightforward because the transaction code should be available
    isDuplicate =
      (err as TransactionError).transactionCode === 'DUPLICATE_TXID';
  } else {
    // Checking whether an endorsement failure is caused by a duplicate
    // transaction is only possible by processing error strings, which is not ideal.
    const endorsementError = err as {
      errors: { endorsements: { details: string }[] }[];
    };

    isDuplicate = endorsementError?.errors?.some((err) =>
      err?.endorsements?.some((endorsement) =>
        endorsement?.details?.startsWith('duplicate transaction found')
      )
    );
  }

  return isDuplicate === true;
};

/**
 * Matches asset already exists error strings from the asset contract
 *
 * The regex needs to match the following error messages:
 *   - "the asset %s already exists"
 *   - "The asset ${id} already exists"
 *   - "Asset %s already exists"
 */
const matchAssetAlreadyExistsMessage = (message: string): string | null => {
  const assetAlreadyExistsRegex = /([tT]he )?[aA]sset \w* already exists/g;
  const assetAlreadyExistsMatch = message.match(assetAlreadyExistsRegex);
  logger.debug(
    { message: message, result: assetAlreadyExistsMatch },
    'Checking for asset already exists message'
  );

  if (assetAlreadyExistsMatch !== null) {
    return assetAlreadyExistsMatch[0];
  }

  return null;
};

/**
 * Matches asset does not exist error strings from the asset contract
 *
 * The regex needs to match the following error messages:
 *   - "the asset %s does not exist"
 *   - "The asset ${id} does not exist"
 *   - "Asset %s does not exist"
 */
const matchAssetDoesNotExistMessage = (message: string): string | null => {
  const assetDoesNotExistRegex = /([tT]he )?[aA]sset \w* does not exist/g;
  const assetDoesNotExistMatch = message.match(assetDoesNotExistRegex);
  logger.debug(
    { message: message, result: assetDoesNotExistMatch },
    'Checking for asset does not exist message'
  );

  if (assetDoesNotExistMatch !== null) {
    return assetDoesNotExistMatch[0];
  }

  return null;
};

/**
 * Matches transaction does not exist error strings from the contract API
 *
 * The regex needs to match the following error messages:
 *   - "Failed to get transaction with id %s, error Entry not found in index"
 *   - "Failed to get transaction with id %s, error no such transaction ID [%s] in index"
 */
const matchTransactionDoesNotExistMessage = (
  message: string
): string | null => {
  const transactionDoesNotExistRegex =
    /Failed to get transaction with id [^,]*, error (?:(?:Entry not found)|(?:no such transaction ID \[[^\]]*\])) in index/g;
  const transactionDoesNotExistMatch = message.match(
    transactionDoesNotExistRegex
  );
  logger.debug(
    { message: message, result: transactionDoesNotExistMatch },
    'Checking for transaction does not exist message'
  );

  if (transactionDoesNotExistMatch !== null) {
    return transactionDoesNotExistMatch[0];
  }

  return null;
};

/**
 * Handles errors from evaluating and submitting transactions.
 *
 * Smart contract errors from the basic asset transfer samples do not use
 * error codes so matching strings is the only option, which is not ideal.
 *
 * Note: the error message text is not the same for the Go, Java, and
 * Javascript implementations of the chaincode!
 */
export const handleError = (
  transactionId: string,
  err: unknown
): Error | unknown => {
  logger.debug({ transactionId: transactionId, err }, 'Processing error');

  if (isErrorLike(err)) {
    const assetAlreadyExistsMatch = matchAssetAlreadyExistsMessage(err.message);
    if (assetAlreadyExistsMatch !== null) {
      return new AssetExistsError(assetAlreadyExistsMatch, transactionId);
    }

    const assetDoesNotExistMatch = matchAssetDoesNotExistMessage(err.message);
    if (assetDoesNotExistMatch !== null) {
      return new AssetNotFoundError(assetDoesNotExistMatch, transactionId);
    }

    const transactionDoesNotExistMatch = matchTransactionDoesNotExistMessage(
      err.message
    );
    if (transactionDoesNotExistMatch !== null) {
      return new TransactionNotFoundError(
        transactionDoesNotExistMatch,
        transactionId
      );
    }
  }

  return err;
};
