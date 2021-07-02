/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { Contract } from 'fabric-network';
import { Redis } from 'ioredis';
import * as config from './config';
import { startRetryLoop } from './fabric';
import { logger } from './logger';
import { createServer } from './server';

async function main() {
  const app = await createServer();

  const contract: Contract = app.get('contract');
  const redis: Redis = app.get('redis');
  startRetryLoop(contract, redis);

  app.listen(config.port, () => {
    logger.info('Express server started on port: %d', config.port);
  });
}

// TODO handle errors! E.g. try starting with the wrong cert and private key!
main().catch((err) => {
  logger.error(err, 'Unxepected error');
});
