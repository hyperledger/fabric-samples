/*
 * SPDX-License-Identifier: Apache-2.0
 */

import express, { Request, Response } from 'express';
import { Contract } from 'fabric-network';
import { getReasonPhrase, StatusCodes } from 'http-status-codes';
import { getTransactionValidationCode } from './fabric';
import { logger } from './logger';
import { TransactionNotFoundError } from './errors';

const { INTERNAL_SERVER_ERROR, NOT_FOUND, OK } = StatusCodes;

export const transactionsRouter = express.Router();

transactionsRouter.get(
  '/:transactionId',
  async (req: Request, res: Response) => {
    const mspId = req.user as string;
    const transactionId = req.params.transactionId;
    logger.debug('Read request received for transaction ID %s', transactionId);

    try {
      const qsccContract = req.app.locals[mspId]?.qsccContract as Contract;

      const validationCode = await getTransactionValidationCode(
        qsccContract,
        transactionId
      );

      return res.status(OK).json({
        transactionId,
        validationCode,
      });
    } catch (err) {
      if (err instanceof TransactionNotFoundError) {
        return res.status(NOT_FOUND).json({
          status: getReasonPhrase(NOT_FOUND),
          timestamp: new Date().toISOString(),
        });
      } else {
        logger.error(
          { err },
          'Error processing read request for transaction ID %s',
          transactionId
        );

        return res.status(INTERNAL_SERVER_ERROR).json({
          status: getReasonPhrase(INTERNAL_SERVER_ERROR),
          timestamp: new Date().toISOString(),
        });
      }
    }
  }
);
