/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { logger } from './logger';

export class ContractError extends Error {
  transactionId: string;

  constructor(message: string, transactionId: string) {
    super(message);
    Object.setPrototypeOf(this, ContractError.prototype);

    this.name = 'TransactionError';
    this.transactionId = transactionId;
  }
}

export class TransactionNotFoundError extends ContractError {
  transactionId: string;

  constructor(message: string, transactionId: string) {
    super(message, transactionId);
    Object.setPrototypeOf(this, TransactionNotFoundError.prototype);

    this.name = 'TransactionNotFoundError';
    this.transactionId = transactionId;
  }
}

export class AssetExistsError extends ContractError {
  constructor(message: string, transactionId: string) {
    super(message, transactionId);
    Object.setPrototypeOf(this, AssetExistsError.prototype);

    this.name = 'AssetExistsError';
  }
}

export class AssetNotFoundError extends ContractError {
  constructor(message: string, transactionId: string) {
    super(message, transactionId);
    Object.setPrototypeOf(this, AssetNotFoundError.prototype);

    this.name = 'AssetNotFoundError';
  }
}

export class JobNotFoundError extends Error {
  jobId: string;

  constructor(message: string, jobId: string) {
    super(message);
    Object.setPrototypeOf(this, JobNotFoundError.prototype);

    this.name = 'JobNotFoundError';
    this.jobId = jobId;
  }
}

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

/*
 * Checks whether an error was caused by a duplicate transaction.
 *
 * Checking error strings like this is not ideal, unfortunately it appears to
 * be the only option. In this case it would be better to check for the
 * DUPLICATE_TXID TxValidationCode somehow but that does not seem to be
 * possible.
 */
export const isDuplicateTransactionError = (err: unknown): boolean => {
  if (err === undefined || err === null) return false;

  const endorsementError = err as {
    errors: { endorsements: { details: string }[] }[];
  };

  const isDuplicate = endorsementError?.errors?.some((err) =>
    err?.endorsements?.some((endorsement) =>
      endorsement?.details?.startsWith('duplicate transaction found')
    )
  );

  return isDuplicate === true;
};

/*
 * Matches asset already exists error strings from the asset contract
 *
 * The regex needs to match the following error messages:
 *   "the asset %s already exists"
 *   "The asset ${id} already exists"
 *   "Asset %s already exists"
 */
const matchAssetAlreadyExistsMessage = (message: string): string | null => {
  //
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

/*
 * Matches asset does not exist error strings from the asset contract
 *
 * The regex needs to match the following error messages:
 *   "the asset %s does not exist"
 *   "The asset ${id} does not exist"
 *   "Asset %s does not exist"
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

/*
 * Matches transaction does not exist error strings from the contract API
 *
 * The regex needs to match the following error messages:
 *   "Failed to get transaction with id %s, error Entry not found in index"
 *   "Failed to get transaction with id %s, error no such transaction ID [%s] in index"
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

export const isContractError = (err: unknown): boolean => {
  if (
    err instanceof AssetExistsError ||
    err instanceof AssetNotFoundError ||
    err instanceof TransactionNotFoundError
  ) {
    return true;
  }

  return false;
};

/*
 * Handles errors from evaluating and submitting transactions.
 *
 * As with duplicate transaction errors, checking error strings like this is
 * not ideal. Unfortunately the chaincode samples do not use error codes so
 * again it's the only option. The error message text is not even the same for
 * the Go, Java, and Javascript implementations of the chaincode!
 */
export const handleError = (transactionId: string, err: unknown): Error => {
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

    return err;
  }

  return new Error(`Unhandled error: ${err}`);
};
