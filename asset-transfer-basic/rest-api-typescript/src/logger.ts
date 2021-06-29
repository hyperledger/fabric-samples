import pino from 'pino';
import * as config from './config';

export const logger = pino({
  level: config.logLevel,
});
