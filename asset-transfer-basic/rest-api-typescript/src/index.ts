/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { logger } from './logger';
import { createServer } from './server';
import * as config from './config';

async function main() {
  const app = await createServer();

  app.listen(config.port, () => {
    logger.info('Express server started on port: %d', config.port);
  });
}

main();
