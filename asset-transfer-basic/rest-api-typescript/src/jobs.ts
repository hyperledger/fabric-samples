/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * This sample uses BullMQ jobs to process submit transactions, which includes
 * retry support for failing jobs
 *
 * Important: BullMQ requires the following setting in redis
 *   maxmemory-policy=noeviction
 * For details, see: https://docs.bullmq.io/guide/connections
 */

import { ConnectionOptions, Job, Queue, QueueScheduler, Worker } from 'bullmq';
import { Contract, Transaction } from 'fabric-network';
import * as config from './config';
import { JobNotFoundError } from './errors';
import { processSubmitTransactionJob } from './fabric';
import { logger } from './logger';

export type JobData = {
  mspid: string;
  transactionName: string;
  transactionArgs: string[];
  transactionState?: Buffer;
  transactionIds: string[];
};

export type JobResult = {
  transactionPayload?: Buffer;
  transactionError?: string;
};

// TODO include attempts made?
export type JobSummary = {
  jobId: string;
  transactionIds: string[];
  transactionPayload?: string;
  transactionError?: string;
};

const connection: ConnectionOptions = {
  port: config.redisPort,
  host: config.redisHost,
  username: config.redisUsername,
  password: config.redisPassword,
};

export const initJobQueue = (): Queue => {
  const submitQueue = new Queue(config.JOB_QUEUE_NAME, {
    connection,
    defaultJobOptions: {
      attempts: config.submitJobAttempts,
      backoff: {
        type: config.submitJobBackoffType,
        delay: config.submitJobBackoffDelay,
      },
      removeOnComplete: config.maxCompletedSubmitJobs,
      removeOnFail: config.maxFailedSubmitJobs,
    },
  });

  return submitQueue;
};

export const initJobQueueWorker = (
  contracts: Map<string, Contract>
): Worker => {
  const worker = new Worker<JobData, JobResult>(
    config.JOB_QUEUE_NAME,
    async (job): Promise<JobResult> => {
      return await processSubmitTransactionJob(contracts, job);
    },
    { connection, concurrency: config.submitJobConcurrency }
  );

  worker.on('failed', (job) => {
    logger.error({ job }, 'Job failed'); // WHY?!
  });

  // Important: need to handle this error otherwise worker may stop
  // processing jobs
  worker.on('error', (err) => {
    logger.error({ err }, 'Worker error');
  });

  if (logger.isLevelEnabled('debug')) {
    worker.on('completed', (job) => {
      logger.debug({ job }, 'Job completed');
    });
  }

  return worker;
};

export const initJobQueueScheduler = (): QueueScheduler => {
  const queueScheduler = new QueueScheduler(config.JOB_QUEUE_NAME, {
    connection,
  });

  queueScheduler.on('failed', (jobId, failedReason) => {
    // TODO when does this happen, and how should it be handled?
    logger.error({ jobId, failedReason }, 'Queue sceduler failure');
  });

  return queueScheduler;
};

export const addSubmitTransactionJob = async (
  submitQueue: Queue<JobData, JobResult>,
  mspid: string,
  transactionName: string,
  ...transactionArgs: string[]
): Promise<string> => {
  const jobName = `submit ${transactionName} transaction`;
  const job = await submitQueue.add(jobName, {
    mspid,
    transactionName,
    transactionArgs: transactionArgs,
    transactionIds: [],
  });

  if (job?.id === undefined) {
    throw new Error('Submit transaction job ID not available');
  }

  return job.id;
};

/*
 * Gets a summary for the jobs endpoint
 */
export const getJobSummary = async (
  queue: Queue,
  jobId: string
): Promise<JobSummary> => {
  const job: Job<JobData, JobResult> | undefined = await queue.getJob(jobId);
  logger.debug({ job }, 'Got job');

  if (!(job && job.id != undefined)) {
    throw new JobNotFoundError(`Job ${jobId} not found`, jobId);
  }

  let transactionIds: string[];
  if (job.data && job.data.transactionIds) {
    transactionIds = job.data.transactionIds;
  } else {
    transactionIds = [];
  }

  let transactionError;
  let transactionPayload;
  const returnValue = job.returnvalue;
  if (returnValue) {
    if (returnValue.transactionError) {
      transactionError = returnValue.transactionError;
    }

    if (
      returnValue.transactionPayload &&
      returnValue.transactionPayload.length > 0
    ) {
      transactionPayload = returnValue.transactionPayload.toString();
    } else {
      transactionPayload = '';
    }
  }

  const jobSummary: JobSummary = {
    jobId: job.id,
    transactionIds,
    transactionError,
    transactionPayload,
  };

  return jobSummary;
};

export const updateJobData = async (
  job: Job<JobData, JobResult>,
  transaction: Transaction | undefined
): Promise<void> => {
  const newData = { ...job.data };

  if (transaction != undefined) {
    const transationIds = ([] as string[]).concat(
      newData.transactionIds,
      transaction.getTransactionId()
    );
    newData.transactionIds = transationIds;

    newData.transactionState = transaction.serialize();
  } else {
    newData.transactionState = undefined;
  }

  await job.update(newData);
};

/*
 * Get the current job counts
 *
 * This function is used for the liveness REST endpoint
 */
export const getJobCounts = async (
  queue: Queue
): Promise<{ [index: string]: number }> => {
  const jobCounts = await queue.getJobCounts(
    'active',
    'completed',
    'delayed',
    'failed',
    'waiting'
  );
  logger.debug({ jobCounts }, 'Current job counts');

  return jobCounts;
};
