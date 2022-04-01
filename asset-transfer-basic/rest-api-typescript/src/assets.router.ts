/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * This sample is intended to work with the basic asset transfer
 * chaincode which imposes some constraints on what is possible here.
 *
 * For example,
 *  - There is no validation for Asset IDs
 *  - There are no error codes from the chaincode
 *
 * To avoid timeouts, long running tasks should be decoupled from HTTP request
 * processing
 *
 * Submit transactions can potentially be very long running, especially if the
 * transaction fails and needs to be retried one or more times
 *
 * To allow requests to respond quickly enough, this sample queues submit
 * requests for processing asynchronously and immediately returns 202 Accepted
 */

import express, { Request, Response } from 'express';
import { body, validationResult } from 'express-validator';
import { Contract } from 'fabric-network';
import { getReasonPhrase, StatusCodes } from 'http-status-codes';
import { Queue } from 'bullmq';
import { AssetNotFoundError } from './errors';
import { evatuateTransaction } from './fabric';
import { addSubmitTransactionJob } from './jobs';
import { logger } from './logger';

const { ACCEPTED, BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND, OK } =
  StatusCodes;

export const assetsRouter = express.Router();

assetsRouter.get('/', async (req: Request, res: Response) => {
  logger.debug('Get all assets request received');
  try {
    const mspId = req.user as string;
    const contract = req.app.locals[mspId]?.assetContract as Contract;

    const data = await evatuateTransaction(contract, 'GetAllAssets');
    let assets = [];
    if (data.length > 0) {
      assets = JSON.parse(data.toString());
    }

    return res.status(OK).json(assets);
  } catch (err) {
    logger.error({ err }, 'Error processing get all assets request');
    return res.status(INTERNAL_SERVER_ERROR).json({
      status: getReasonPhrase(INTERNAL_SERVER_ERROR),
      timestamp: new Date().toISOString(),
    });
  }
});

assetsRouter.post(
  '/',
  body().isObject().withMessage('body must contain an asset object'),
  body('ID', 'must be a string').notEmpty(),
  body('Color', 'must be a string').notEmpty(),
  body('Size', 'must be a number').isNumeric(),
  body('Owner', 'must be a string').notEmpty(),
  body('AppraisedValue', 'must be a number').isNumeric(),
  async (req: Request, res: Response) => {
    logger.debug(req.body, 'Create asset request received');

    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(BAD_REQUEST).json({
        status: getReasonPhrase(BAD_REQUEST),
        reason: 'VALIDATION_ERROR',
        message: 'Invalid request body',
        timestamp: new Date().toISOString(),
        errors: errors.array(),
      });
    }

    const mspId = req.user as string;
    const assetId = req.body.ID;

    try {
      const submitQueue = req.app.locals.jobq as Queue;
      const jobId = await addSubmitTransactionJob(
        submitQueue,
        mspId,
        'CreateAsset',
        assetId,
        req.body.Color,
        req.body.Size,
        req.body.Owner,
        req.body.AppraisedValue
      );

      return res.status(ACCEPTED).json({
        status: getReasonPhrase(ACCEPTED),
        jobId: jobId,
        timestamp: new Date().toISOString(),
      });
    } catch (err) {
      logger.error(
        { err },
        'Error processing create asset request for asset ID %s',
        assetId
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
    const mspId = req.user as string;
    const contract = req.app.locals[mspId]?.assetContract as Contract;

    const data = await evatuateTransaction(contract, 'AssetExists', assetId);
    const exists = data.toString() === 'true';

    if (exists) {
      return res
        .status(OK)
        .set({
          Allow: 'DELETE,GET,OPTIONS,PATCH,PUT',
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
      { err },
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
    const mspId = req.user as string;
    const contract = req.app.locals[mspId]?.assetContract as Contract;

    const data = await evatuateTransaction(contract, 'ReadAsset', assetId);
    const asset = JSON.parse(data.toString());

    return res.status(OK).json(asset);
  } catch (err) {
    logger.error(
      { err },
      'Error processing read asset request for asset ID %s',
      assetId
    );

    if (err instanceof AssetNotFoundError) {
      return res.status(NOT_FOUND).json({
        status: getReasonPhrase(NOT_FOUND),
        timestamp: new Date().toISOString(),
      });
    }

    return res.status(INTERNAL_SERVER_ERROR).json({
      status: getReasonPhrase(INTERNAL_SERVER_ERROR),
      timestamp: new Date().toISOString(),
    });
  }
});

assetsRouter.put(
  '/:assetId',
  body().isObject().withMessage('body must contain an asset object'),
  body('ID', 'must be a string').notEmpty(),
  body('Color', 'must be a string').notEmpty(),
  body('Size', 'must be a number').isNumeric(),
  body('Owner', 'must be a string').notEmpty(),
  body('AppraisedValue', 'must be a number').isNumeric(),
  async (req: Request, res: Response) => {
    logger.debug(req.body, 'Update asset request received');

    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(BAD_REQUEST).json({
        status: getReasonPhrase(BAD_REQUEST),
        reason: 'VALIDATION_ERROR',
        message: 'Invalid request body',
        timestamp: new Date().toISOString(),
        errors: errors.array(),
      });
    }

    if (req.params.assetId != req.body.ID) {
      return res.status(BAD_REQUEST).json({
        status: getReasonPhrase(BAD_REQUEST),
        reason: 'ASSET_ID_MISMATCH',
        message: 'Asset IDs must match',
        timestamp: new Date().toISOString(),
      });
    }

    const mspId = req.user as string;
    const assetId = req.params.assetId;

    try {
      const submitQueue = req.app.locals.jobq as Queue;
      const jobId = await addSubmitTransactionJob(
        submitQueue,
        mspId,
        'UpdateAsset',
        assetId,
        req.body.color,
        req.body.size,
        req.body.owner,
        req.body.appraisedValue
      );

      return res.status(ACCEPTED).json({
        status: getReasonPhrase(ACCEPTED),
        jobId: jobId,
        timestamp: new Date().toISOString(),
      });
    } catch (err) {
      logger.error(
        { err },
        'Error processing update asset request for asset ID %s',
        assetId
      );

      return res.status(INTERNAL_SERVER_ERROR).json({
        status: getReasonPhrase(INTERNAL_SERVER_ERROR),
        timestamp: new Date().toISOString(),
      });
    }
  }
);

assetsRouter.patch(
  '/:assetId',
  body()
    .isArray({
      min: 1,
      max: 1,
    })
    .withMessage('body must contain an array with a single patch operation'),
  body('*.op', "operation must be 'replace'").equals('replace'),
  body('*.path', "path must be '/Owner'").equals('/Owner'),
  body('*.value', 'must be a string').isString(),
  async (req: Request, res: Response) => {
    logger.debug(req.body, 'Transfer asset request received');

    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(BAD_REQUEST).json({
        status: getReasonPhrase(BAD_REQUEST),
        reason: 'VALIDATION_ERROR',
        message: 'Invalid request body',
        timestamp: new Date().toISOString(),
        errors: errors.array(),
      });
    }

    const mspId = req.user as string;
    const assetId = req.params.assetId;
    const newOwner = req.body[0].value;

    try {
      const submitQueue = req.app.locals.jobq as Queue;
      const jobId = await addSubmitTransactionJob(
        submitQueue,
        mspId,
        'TransferAsset',
        assetId,
        newOwner
      );

      return res.status(ACCEPTED).json({
        status: getReasonPhrase(ACCEPTED),
        jobId: jobId,
        timestamp: new Date().toISOString(),
      });
    } catch (err) {
      logger.error(
        { err },
        'Error processing update asset request for asset ID %s',
        req.params.assetId
      );

      return res.status(INTERNAL_SERVER_ERROR).json({
        status: getReasonPhrase(INTERNAL_SERVER_ERROR),
        timestamp: new Date().toISOString(),
      });
    }
  }
);

assetsRouter.delete('/:assetId', async (req: Request, res: Response) => {
  logger.debug(req.body, 'Delete asset request received');

  const mspId = req.user as string;
  const assetId = req.params.assetId;

  try {
    const submitQueue = req.app.locals.jobq as Queue;
    const jobId = await addSubmitTransactionJob(
      submitQueue,
      mspId,
      'DeleteAsset',
      assetId
    );

    return res.status(ACCEPTED).json({
      status: getReasonPhrase(ACCEPTED),
      jobId: jobId,
      timestamp: new Date().toISOString(),
    });
  } catch (err) {
    logger.error(
      { err },
      'Error processing delete asset request for asset ID %s',
      assetId
    );

    return res.status(INTERNAL_SERVER_ERROR).json({
      status: getReasonPhrase(INTERNAL_SERVER_ERROR),
      timestamp: new Date().toISOString(),
    });
  }
});
