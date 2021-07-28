/*
 * SPDX-License-Identifier: Apache-2.0
 */

import helmet from 'helmet';
import { StatusCodes, getReasonPhrase } from 'http-status-codes';
import express, { Application, NextFunction, Request, Response } from 'express';
import pinoMiddleware from 'pino-http';

import { logger } from './logger';
import { assetsRouter } from './assets.router';
import { transactionsRouter } from './transactions.router';
import {
  getContracts,
  getGateway,
  getNetwork,
  getChainInfo,
  getContractForOrg,
} from './fabric';
import { redis } from './redis';
import { Contract } from 'fabric-network';
import * as config from './config';
const {
  BAD_REQUEST,
  INTERNAL_SERVER_ERROR,
  NOT_FOUND,
  OK,
  SERVICE_UNAVAILABLE,
} = StatusCodes;

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

  if (process.env.NODE_ENV === 'production') {
    app.use(helmet());
  }
  //
  const gatewayOrg1 = await getGateway(config.identityNameOrg1);
  const gatewayOrg2 = await getGateway(config.identityNameOrg2);
  const networkOrg1 = await getNetwork(gatewayOrg1);
  const networkOrg2 = await getNetwork(gatewayOrg2);

  const contractsOrg1 = await getContracts(networkOrg1);
  const contractsOrg2 = await getContracts(networkOrg2);

  const fabric = {
    [config.identityNameOrg1]: {
      gateway: gatewayOrg1,
      contracts: contractsOrg1,
      network: networkOrg1,
    },
    [config.identityNameOrg2]: {
      gateway: gatewayOrg2,
      contracts: contractsOrg2,
      network: networkOrg2,
    },
  };

  app.set('fabric', fabric);

  app.set('redis', redis);

  // Health routes
  app.get('/ready', (_req, res) =>
    res.status(OK).json({
      status: getReasonPhrase(OK),
      timestamp: new Date().toISOString(),
    })
  );
  app.get('/live', async (_req, res) => {
    _req.user = { org: config.identityNameOrg1 };
    const qsccOrg1: Contract = getContractForOrg(_req).qscc;
    const Org1Liveness = await getChainInfo(qsccOrg1);
    logger.debug('Org1 liveness %s', Org1Liveness);
    _req.user = { org: config.identityNameOrg2 };
    const qsccOrg2: Contract = getContractForOrg(_req).qscc;
    const Org2Liveness = await getChainInfo(qsccOrg2);
    logger.debug('Org2 liveness %s', Org2Liveness);

    if (Org1Liveness && Org2Liveness) {
      res.status(OK).json({
        status: getReasonPhrase(OK),
        timestamp: new Date().toISOString(),
      });
    } else {
      res.status(SERVICE_UNAVAILABLE).json({
        status: getReasonPhrase(SERVICE_UNAVAILABLE),
        timestamp: new Date().toISOString(),
      });
    }
  });

  // TODO delete me
  app.get('/error', (_req, _res) => {
    throw new Error('Example error');
  });

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
  // TBC in addition to pinoMiddleware errors?
  app.use((err: Error, _req: Request, res: Response, _next: NextFunction) => {
    logger.error(err);
    return res.status(INTERNAL_SERVER_ERROR).json({
      status: getReasonPhrase(INTERNAL_SERVER_ERROR),
      timestamp: new Date().toISOString(),
    });
  });

  return app;
};
