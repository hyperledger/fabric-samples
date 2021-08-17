/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { Network } from 'fabric-network';
import { Redis } from 'ioredis';
import * as config from './config';
import { blockEventHandler } from './fabric';
import { logger } from './logger';
import { createServer } from './server';

async function main() {
  const app = await createServer();

  // TODO block listener currently only handles a single org!!!
  // TODO should it be initialised here?
  const redis = app.get('redis') as Redis;
  const network = app.get('networkOrg1') as Network;
  await network.addBlockListener(blockEventHandler(redis));

  app.listen(config.port, () => {
    logger.info('Express server started on port: %d', config.port);
  });
}

// TODO handle errors! E.g. try starting with the wrong cert and private key!
main().catch((err) => {
  logger.error(err, 'Unxepected error');
});
