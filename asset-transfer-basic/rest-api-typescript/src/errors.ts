/*
 * SPDX-License-Identifier: Apache-2.0
 */

export class TransactionError extends Error {
  transactionId: string;

  constructor(message: string, transactionId: string) {
    super(message);
    Object.setPrototypeOf(this, TransactionError.prototype);

    this.name = 'TransactionError';
    this.transactionId = transactionId;
  }
}

export class AssetExistsError extends TransactionError {
  constructor(message: string, transactionId: string) {
    super(message, transactionId);
    Object.setPrototypeOf(this, AssetExistsError.prototype);

    this.name = 'AssetExistsError';
  }
}

export class AssetNotFoundError extends TransactionError {
  constructor(message: string, transactionId: string) {
    super(message, transactionId);
    Object.setPrototypeOf(this, AssetNotFoundError.prototype);

    this.name = 'AssetNotFoundError';
  }
}
