/*
 * SPDX-License-Identifier: Apache-2.0
 */

import helmet from 'helmet';
import { StatusCodes, getReasonPhrase } from 'http-status-codes';
import express, { Application, NextFunction, Request, Response } from 'express';
import pinoMiddleware from 'pino-http';
import { Gateway, GatewayOptions, Contract, Wallets } from 'fabric-network';

import * as config from './config';
import { logger } from './logger';
import { assetsRouter } from './assets.router';

const { BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND, OK } = StatusCodes;

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

  if (process.env.NODE_ENV === 'development') {
    // TBC
  }

  if (process.env.NODE_ENV === 'production') {
    app.use(helmet());
  }

  const contract = await getContract();
  app.set('contract', contract);

  // Health routes
  app.get('/ready', (_req, res) =>
    res.status(OK).json({
      status: getReasonPhrase(OK),
      timestamp: new Date().toISOString(),
    })
  );
  app.get('/live', (_req, res) =>
    res.status(OK).json({
      status: getReasonPhrase(OK),
      timestamp: new Date().toISOString(),
    })
  );

  // TODO delete me
  app.get('/error', (_req, _res) => {
    throw new Error('Example error');
  });

  app.use('/api/assets', assetsRouter);

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

// TODO should this go in a fabric.ts file?

const getContract = async (): Promise<Contract> => {
  const wallet = await Wallets.newInMemoryWallet();

  const x509Identity = {
    credentials: {
      certificate: config.certificate,
      privateKey: config.privateKey,
    },
    mspId: config.mspId,
    type: 'X.509',
  };
  await wallet.put(config.identityName, x509Identity);

  const gateway = new Gateway();

  const gatewayOpts: GatewayOptions = {
    wallet,
    identity: config.identityName,
    discovery: { enabled: true, asLocalhost: config.asLocalHost },
  };

  await gateway.connect(config.connectionProfile, gatewayOpts);

  const network = await gateway.getNetwork(config.channelName);
  const contract = network.getContract(config.chaincodeName);

  return contract;
};
