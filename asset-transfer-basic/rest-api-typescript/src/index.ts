/*
 * SPDX-License-Identifier: Apache-2.0
 */

import pino from 'pino';

import app from './server';

// TODO check any required env vars

const logger = pino({
  level: process.env.LOG_LEVEL || 'info',
});

// Start the server
const port = Number(process.env.PORT || 3000);
app.listen(port, () => {
  logger.info('Express server started on port: %d', port);
});
