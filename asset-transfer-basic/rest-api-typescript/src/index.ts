/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { Contract } from 'fabric-network';
import * as config from './config';
import {
  createGateway,
  createWallet,
  getContracts,
  getNetwork,
} from './fabric';
import {
  initJobQueue,
  initJobQueueScheduler,
  initJobQueueWorker,
} from './jobs';
import { logger } from './logger';
import { createServer } from './server';
import { isMaxmemoryPolicyNoeviction } from './redis';
import { Queue, QueueScheduler, Worker } from 'bullmq';

let jobQueue: Queue | undefined;
let jobQueueWorker: Worker | undefined;
let jobQueueScheduler: QueueScheduler | undefined;

async function main() {
  logger.info('Checking Redis config');
  if (!(await isMaxmemoryPolicyNoeviction())) {
    throw new Error(
      'Invalid redis configuration: redis instance must have the setting maxmemory-policy=noeviction'
    );
  }

  logger.info('Connecting to Fabric network');
  const wallet = await createWallet();

  const gatewayOrg1 = await createGateway(
    config.connectionProfileOrg1,
    config.mspIdOrg1,
    wallet
  );
  const networkOrg1 = await getNetwork(gatewayOrg1);
  const contractsOrg1 = await getContracts(networkOrg1);

  const gatewayOrg2 = await createGateway(
    config.connectionProfileOrg2,
    config.mspIdOrg2,
    wallet
  );
  const networkOrg2 = await getNetwork(gatewayOrg2);
  const contractsOrg2 = await getContracts(networkOrg2);

  const assetContracts = new Map<string, Contract>();
  assetContracts.set(config.mspIdOrg1, contractsOrg1.assetContract);
  assetContracts.set(config.mspIdOrg2, contractsOrg2.assetContract);

  logger.info('Initialising submit job queue');
  jobQueue = initJobQueue();
  jobQueueWorker = initJobQueueWorker(assetContracts);
  if (config.submitJobQueueScheduler === true) {
    logger.info('Initialising submit job queue scheduler');
    jobQueueScheduler = initJobQueueScheduler();
  }

  logger.info('Creating REST server');
  const app = await createServer();
  app.set(config.mspIdOrg1, contractsOrg1);
  app.set(config.mspIdOrg2, contractsOrg2);
  app.set('jobq', jobQueue);

  app.listen(config.port, () => {
    logger.info('REST server started on port: %d', config.port);
  });
}

main().catch(async (err) => {
  logger.error({ err }, 'Unxepected error');

  if (jobQueueScheduler != undefined) {
    logger.debug('Closing job queue scheduler');
    await jobQueueScheduler.close();
  }

  if (jobQueueWorker != undefined) {
    logger.debug('Closing job queue worker');
    await jobQueueWorker.close();
  }

  if (jobQueue != undefined) {
    logger.debug('Closing job queue');
    await jobQueue.close();
  }
});
