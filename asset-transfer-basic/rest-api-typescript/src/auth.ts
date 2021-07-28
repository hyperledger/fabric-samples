/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { logger } from './logger';
import passport from 'passport';
import { NextFunction, Request, Response } from 'express';
import { HeaderAPIKeyStrategy } from 'passport-headerapikey';
import { StatusCodes, getReasonPhrase } from 'http-status-codes';
import * as config from './config';

const { UNAUTHORIZED } = StatusCodes;

export const fabricAPIKeyStrategy: HeaderAPIKeyStrategy =
  new HeaderAPIKeyStrategy(
    { header: 'X-API-Key', prefix: '' },
    false,
    function (apikey, done) {
      logger.debug({ apikey }, 'Checking X-API-Key');
      const user: { org: string } = {
        org: '',
      };
      if (apikey === config.org1ApiKey) {
        user.org = config.identityNameOrg1;
        logger.debug('Organisation set to Org1');
        done(null, user);
      } else if (apikey === config.org2ApiKey) {
        user.org = config.identityNameOrg2;
        logger.info('Organisation set to Org2');
        done(null, user);
      } else {
        logger.debug({ apikey }, 'No valid X-API-Key');
        return done(null, false);
      }
    }
  );

export const authenticateApiKey = (
  req: Request,
  res: Response,
  next: NextFunction
): void => {
  passport.authenticate(
    'headerapikey',
    { session: false },
    (err, user, _info) => {
      logger.debug({ user }, 'USERUSERUSER');
      if (err) return next(err);
      if (!user)
        return res.status(UNAUTHORIZED).json({
          status: getReasonPhrase(UNAUTHORIZED),
          reason: 'NO_VALID_APIKEY',
          timestamp: new Date().toISOString(),
        });

      req.logIn(user, { session: false }, async (err) => {
        if (err) {
          return next(err);
        }
        return next();
      });
    }
  )(req, res, next);
};
