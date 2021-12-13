/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { Queue } from 'bullmq';
import express, { Request, Response } from 'express';
import { getReasonPhrase, StatusCodes } from 'http-status-codes';
import { getJobSummary, JobNotFoundError } from './jobs';
import { logger } from './logger';

const { INTERNAL_SERVER_ERROR, NOT_FOUND, OK } = StatusCodes;

export const jobsRouter = express.Router();

jobsRouter.get('/:jobId', async (req: Request, res: Response) => {
  const jobId = req.params.jobId;
  logger.debug('Read request received for job ID %s', jobId);

  try {
    const submitQueue = req.app.locals.jobq as Queue;

    const jobSummary = await getJobSummary(submitQueue, jobId);

    return res.status(OK).json(jobSummary);
  } catch (err) {
    logger.error({ err }, 'Error processing read request for job ID %s', jobId);

    if (err instanceof JobNotFoundError) {
      return res.status(NOT_FOUND).json({
        status: getReasonPhrase(NOT_FOUND),
        timestamp: new Date().toISOString(),
      });
    }

    return res.status(INTERNAL_SERVER_ERROR).json({
      status: getReasonPhrase(INTERNAL_SERVER_ERROR),
      timestamp: new Date().toISOString(),
    });
  }
});
