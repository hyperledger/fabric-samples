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

import { Contract } from 'fabric-network';
import { StatusCodes, getReasonPhrase } from 'http-status-codes';
import express, { Request, Response } from 'express';

import { logger } from './logger';

const { INTERNAL_SERVER_ERROR, NOT_FOUND, OK } = StatusCodes;

export const assetsRouter = express.Router();

assetsRouter.options('/:assetId', async (req: Request, res: Response) => {
  try {
    const contract: Contract = req.app.get('contract');

    const assetId = req.params.assetId;
    const data = await contract.evaluateTransaction('AssetExists', assetId);
    const exists = data.toString() === 'true';

    if (exists) {
      res
        .status(OK)
        .set({
          Allow: 'GET,OPTIONS',
        })
        .json({
          status: getReasonPhrase(OK),
          timestamp: new Date().toISOString(),
        });
    } else {
      res.status(NOT_FOUND).json({
        status: getReasonPhrase(NOT_FOUND),
        timestamp: new Date().toISOString(),
      });
    }
  } catch (err) {
    logger.error(err);
    return res.status(INTERNAL_SERVER_ERROR).json({
      status: getReasonPhrase(INTERNAL_SERVER_ERROR),
      timestamp: new Date().toISOString(),
    });
  }
});

assetsRouter.get('/:assetId', async (req: Request, res: Response) => {
  try {
    const contract: Contract = req.app.get('contract');

    const assetId = req.params.assetId;
    const data = await contract.evaluateTransaction('ReadAsset', assetId);
    const asset = JSON.parse(data.toString());

    res.status(OK).json(asset);
  } catch (err) {
    logger.error(err);
    return res.status(INTERNAL_SERVER_ERROR).json({
      status: getReasonPhrase(INTERNAL_SERVER_ERROR),
      timestamp: new Date().toISOString(),
    });
  }
});
