/*
 * SPDX-License-Identifier: Apache-2.0
 */

import helmet from 'helmet';
import { StatusCodes, getReasonPhrase } from 'http-status-codes';
import express, { Application, NextFunction, Request, Response } from 'express';
import pinoMiddleware from 'pino-http';
import { Contract } from 'fabric-network';

import { logger } from './logger';
import { assetsRouter } from './assets.router';
import { healthRouter } from './health.router';
import { transactionsRouter } from './transactions.router';
import {
  getContracts,
  getNetwork,
  createGateway,
  createWallet,
  startRetryLoop,
  blockEventHandler,
} from './fabric';
import { redis } from './redis';
import * as config from './config';
const { BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND } = StatusCodes;

import { authenticateApiKey, fabricAPIKeyStrategy } from './auth';
import passport from 'passport';

export const createServer = async (): Promise<Application> => {
  const app = express();

  app.use(
    pinoMiddleware({
      logger,
      customLogLevel: function customLogLevel(res, err) {
        if (
          res.statusCode >= BAD_REQUEST &&
          res.statusCode < INTERNAL_SERVER_ERROR
        ) {
          return 'warn';
        }

        if (res.statusCode >= INTERNAL_SERVER_ERROR || err) {
          return 'error';
        }

        return 'debug';
      },
    })
  );

  app.use(express.json());
  app.use(express.urlencoded({ extended: true }));

  //define passport startegy
  passport.use(fabricAPIKeyStrategy);

  //initialize passport js
  app.use(passport.initialize());

  if (process.env.NODE_ENV === 'development') {
    // TBC
  }

  if (process.env.NODE_ENV === 'test') {
    // TBC
  }

  if (process.env.NODE_ENV === 'production') {
    app.use(helmet());
  }

  const wallet = await createWallet();

  const gatewayOrg1 = await createGateway(
    config.connectionProfileOrg1,
    config.mspIdOrg1,
    wallet
  );
  const networkOrg1 = await getNetwork(gatewayOrg1);
  const contractsOrg1 = await getContracts(networkOrg1);
  app.set(config.mspIdOrg1, contractsOrg1);

  const gatewayOrg2 = await createGateway(
    config.connectionProfileOrg2,
    config.mspIdOrg2,
    wallet
  );
  const networkOrg2 = await getNetwork(gatewayOrg2);
  const contractsOrg2 = await getContracts(networkOrg2);
  app.set(config.mspIdOrg2, contractsOrg2);

  const assetContracts = new Map<string, Contract>();
  assetContracts.set(config.mspIdOrg1, contractsOrg1.assetContract);
  assetContracts.set(config.mspIdOrg2, contractsOrg2.assetContract);
  startRetryLoop(assetContracts, redis);

  app.set('redis', redis);

  logger.debug('Adding block listener to %s network', config.blockListenerOrg);
  if (config.blockListenerOrg === config.ORG1) {
    await networkOrg1.addBlockListener(blockEventHandler(redis));
  } else {
    await networkOrg2.addBlockListener(blockEventHandler(redis));
  }

  app.use('/', healthRouter);
  app.use('/api/assets', authenticateApiKey, assetsRouter);
  app.use('/api/transactions', authenticateApiKey, transactionsRouter);

  // For everything else
  app.use((_req, res) =>
    res.status(NOT_FOUND).json({
      status: getReasonPhrase(NOT_FOUND),
      timestamp: new Date().toISOString(),
    })
  );

  // Print API errors
  app.use((err: Error, _req: Request, res: Response, _next: NextFunction) => {
    logger.error(err);
    return res.status(INTERNAL_SERVER_ERROR).json({
      status: getReasonPhrase(INTERNAL_SERVER_ERROR),
      timestamp: new Date().toISOString(),
    });
  });

  return app;
};
