/*
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  AssetExistsError,
  AssetNotFoundError,
  TransactionNotFoundError,
  handleError,
  isDuplicateTransactionError,
  isErrorLike,
} from './errors';

describe('Errors', () => {
  describe('isErrorLike', () => {
    it('returns false for null', () => {
      expect(isErrorLike(null)).toBe(false);
    });

    it('returns false for undefined', () => {
      expect(isErrorLike(undefined)).toBe(false);
    });

    it('returns false for empty object', () => {
      expect(isErrorLike({})).toBe(false);
    });

    it('returns false for string', () => {
      expect(isErrorLike('true')).toBe(false);
    });

    it('returns false for non-error object', () => {
      expect(isErrorLike({ size: 42 })).toBe(false);
    });

    it('returns false for invalid error object', () => {
      expect(isErrorLike({ name: 'MockError', message: 42 })).toBe(false);
    });

    it('returns false for error like object with invalid stack', () => {
      expect(
        isErrorLike({ name: 'MockError', message: 'Fail', stack: false })
      ).toBe(false);
    });

    it('returns true for error like object', () => {
      expect(isErrorLike({ name: 'MockError', message: 'Fail' })).toBe(true);
    });

    it('returns true for new Error', () => {
      expect(isErrorLike(new Error('Error'))).toBe(true);
    });
  });

  describe('isDuplicateTransactionError', () => {
    it('returns true for an error when all endorsement details are duplicate transaction found', () => {
      const mockDuplicateTransactionError = {
        errors: [
          {
            endorsements: [
              {
                details: 'duplicate transaction found',
              },
              {
                details: 'duplicate transaction found',
              },
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

    it('returns true for an error when at least one endorsement details are duplicate transaction found', () => {
      const mockDuplicateTransactionError = {
        errors: [
          {
            endorsements: [
              {
                details: 'duplicate transaction found',
              },
              {
                details: 'mock endorsement details',
              },
              {
                details: 'mock endorsement details',
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

    it('returns false for an error without endorsement details', () => {
      const mockDuplicateTransactionError = {
        errors: [
          {
            rejections: [
              {
                details: 'duplicate transaction found',
              },
            ],
          },
        ],
      };

      expect(isDuplicateTransactionError(mockDuplicateTransactionError)).toBe(
        false
      );
    });

    it('returns false for a basic Error object without endorsement details', () => {
      expect(
        isDuplicateTransactionError(new Error('duplicate transaction found'))
      ).toBe(false);
    });

    it('returns false for an undefined error', () => {
      expect(isDuplicateTransactionError(undefined)).toBe(false);
    });

    it('returns false for a null error', () => {
      expect(isDuplicateTransactionError(null)).toBe(false);
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

    it.each([
      'Failed to get transaction with id txn, error Entry not found in index',
      'Failed to get transaction with id txn, error no such transaction ID [txn] in index',
    ])(
      'returns a TransactionNotFoundError for errors with a transaction not found message: %s',
      (msg) => {
        expect(handleError('txn1', new Error(msg))).toStrictEqual(
          new TransactionNotFoundError(msg, 'txn1')
        );
      }
    );

    it('returns the original error for errors with other messages', () => {
      expect(handleError('txn1', new Error('MOCK ERROR'))).toStrictEqual(
        new Error('MOCK ERROR')
      );
    });

    it('returns a new Error object for errors of other types', () => {
      expect(handleError('txn1', 42)).toStrictEqual(
        new Error('Unhandled error: 42')
      );
    });
  });
});
