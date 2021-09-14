/*
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  AssetExistsError,
  AssetNotFoundError,
  TransactionError,
  TransactionNotFoundError,
  handleError,
  isDuplicateTransactionError,
} from './errors';

describe('Errors', () => {
  describe('isDuplicateTransactionError', () => {
    it('returns true for an error with duplicate transaction endorsement details', () => {
      const mockDuplicateTransactionError = {
        errors: [
          {
            endorsements: [
              {
                details: 'duplicate transaction found',
              },
            ],
          },
        ],
      };

      expect(isDuplicateTransactionError(mockDuplicateTransactionError)).toBe(
        true
      );
    });

    it('returns false for an error without duplicate transaction endorsement details', () => {
      const mockDuplicateTransactionError = {
        errors: [
          {
            endorsements: [
              {
                details: 'mock endorsement details',
              },
            ],
          },
        ],
      };

      expect(isDuplicateTransactionError(mockDuplicateTransactionError)).toBe(
        false
      );
    });
  });

  describe('handleError', () => {
    it.each([
      'the asset GOCHAINCODE already exists',
      'Asset JAVACHAINCODE already exists',
      'The asset JSCHAINCODE already exists',
    ])(
      'returns an AssetExistsError for errors with an asset already exists message: %s',
      (msg) => {
        expect(handleError('txn1', new Error(msg))).toStrictEqual(
          new AssetExistsError(msg, 'txn1')
        );
      }
    );

    it.each([
      'the asset GOCHAINCODE does not exist',
      'Asset JAVACHAINCODE does not exist',
      'The asset JSCHAINCODE does not exist',
    ])(
      'returns an AssetNotFoundError for errors with an asset does not exist message: %s',
      (msg) => {
        expect(handleError('txn1', new Error(msg))).toStrictEqual(
          new AssetNotFoundError(msg, 'txn1')
        );
      }
    );

    it('returns a TransactionNotFoundError for errors with a transaction not found message', () => {
      expect(
        handleError(
          'txn1',
          new Error(
            'Failed to get transaction with id txn, error Entry not found in index'
          )
        )
      ).toStrictEqual(
        new TransactionNotFoundError(
          'Failed to get transaction with id txn, error Entry not found in index',
          'txn1'
        )
      );
    });

    it('returns a TransactionError for errors with other messages', () => {
      expect(handleError('txn1', new Error('MOCK ERROR'))).toStrictEqual(
        new TransactionError('Transaction error', 'txn1')
      );
    });
  });
});
