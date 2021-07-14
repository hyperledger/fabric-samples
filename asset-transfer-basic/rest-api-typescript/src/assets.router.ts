/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Note: this sample is intended to work with the basic asset transfer
 * chaincode which imposes some constraints on what is possible here.
 *
 * For example,
 *  - There is no validation for Asset IDs
 *  - There are no error codes from the chaincode
 *
 */

import express, { Request, Response } from 'express';
import { body, validationResult } from 'express-validator';
import { Contract } from 'fabric-network';
import { getReasonPhrase, StatusCodes } from 'http-status-codes';
import { Redis } from 'ioredis';
import {
  clearTransactionDetails,
  createDeferredEventHandler,
  storeTransactionDetails,
} from './fabric';
import { logger } from './logger';

const { ACCEPTED, BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND, OK } =
  StatusCodes;

export const assetsRouter = express.Router();

assetsRouter.post(
  '/',
  body('id', 'must be a string').notEmpty(),
  body('color', 'must be a string').notEmpty(),
  body('size', 'must be a number').isNumeric(),
  body('owner', 'must be a string').notEmpty(),
  body('appraisedValue', 'must be a number').isNumeric(),
  async (req: Request, res: Response) => {
    logger.debug(req.body, 'Create asset request received');

    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(BAD_REQUEST).json({
        status: getReasonPhrase(BAD_REQUEST),
        timestamp: new Date().toISOString(),
        errors: errors.array(),
      });
    }

    const contract: Contract = req.app.get('contract');
    const redis: Redis = req.app.get('redis');
    const txn = contract.createTransaction('CreateAsset');
    const txnId = txn.getTransactionId();
    const txnState = txn.serialize();
    const txnArgs = JSON.stringify([
      req.body.id,
      req.body.color,
      req.body.size,
      req.body.owner,
      req.body.appraisedValue,
    ]);

    try {
      const timestamp = Date.now();

      // Store the transaction details and set the event handler in case there
      // are problems later with commiting the transaction
      await storeTransactionDetails(redis, txnId, txnState, txnArgs, timestamp);
      txn.setEventHandler(createDeferredEventHandler(redis));

      await txn.submit(
        req.body.id,
        req.body.color,
        req.body.size,
        req.body.owner,
        req.body.appraisedValue
      );

      return res.status(ACCEPTED).json({
        status: getReasonPhrase(ACCEPTED),
        timestamp: new Date().toISOString(),
      });
    } catch (err) {
      // TODO will this always catch endorsement errors or can those
      // arrive later?

      // There's no point retrying a transaction if there were business
      // logic errors so clear the transaction details
      //
      // Note: it would be nice to pick out business logic errors returned
      // from chaincode, e.g. asset already exists, and return those as a
      // 400 error with message instead. Unfortunately the asset transfer
      // sample or Fabric Node SDK do not provide any well defined error
      // codes that can be checked.
      await clearTransactionDetails(redis, txnId);

      logger.error(
        err,
        'Error processing create asset request for asset ID %s with transaction ID %s',
        req.body.id,
        txnId
      );
      return res.status(INTERNAL_SERVER_ERROR).json({
        status: getReasonPhrase(INTERNAL_SERVER_ERROR),
        timestamp: new Date().toISOString(),
      });
    }
  }
);

assetsRouter.options('/:assetId', async (req: Request, res: Response) => {
  const assetId = req.params.assetId;
  logger.debug('Asset options request received for asset ID %s', assetId);

  try {
    const contract: Contract = req.app.get('contract');

    const data = await contract.evaluateTransaction('AssetExists', assetId);
    const exists = data.toString() === 'true';

    if (exists) {
      return res
        .status(OK)
        .set({
          Allow: 'GET,OPTIONS',
        })
        .json({
          status: getReasonPhrase(OK),
          timestamp: new Date().toISOString(),
        });
    } else {
      return res.status(NOT_FOUND).json({
        status: getReasonPhrase(NOT_FOUND),
        timestamp: new Date().toISOString(),
      });
    }
  } catch (err) {
    logger.error(
      err,
      'Error processing asset options request for asset ID %s',
      assetId
    );
    return res.status(INTERNAL_SERVER_ERROR).json({
      status: getReasonPhrase(INTERNAL_SERVER_ERROR),
      timestamp: new Date().toISOString(),
    });
  }
});

assetsRouter.get('/:assetId', async (req: Request, res: Response) => {
  const assetId = req.params.assetId;
  logger.debug('Read asset request received for asset ID %s', assetId);

  try {
    const contract: Contract = req.app.get('contract');

    const data = await contract.evaluateTransaction('ReadAsset', assetId);
    const asset = JSON.parse(data.toString());

    return res.status(OK).json(asset);
  } catch (err) {
    logger.error(
      err,
      'Error processing read asset request for asset ID %s',
      assetId
    );
    return res.status(INTERNAL_SERVER_ERROR).json({
      status: getReasonPhrase(INTERNAL_SERVER_ERROR),
      timestamp: new Date().toISOString(),
    });
  }
});
