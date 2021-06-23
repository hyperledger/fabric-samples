/*
 * SPDX-License-Identifier: Apache-2.0
 */

import helmet from 'helmet';
import { StatusCodes, getReasonPhrase } from 'http-status-codes';
import express, { NextFunction, Request, Response } from 'express';
import pino from 'pino';
import pinoMiddleware from 'pino-http';

const logger = pino({
  level: process.env.LOG_LEVEL || 'info',
});

const app = express();
const { BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND, OK } = StatusCodes;

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

// TODO add asset APIs
// app.use("/api/assets", assetsRouter);

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

export default app;
