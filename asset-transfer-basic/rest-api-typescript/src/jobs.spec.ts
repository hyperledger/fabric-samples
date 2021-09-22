/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { Job, Queue } from 'bullmq';
import { getJobCounts, getJobSummary } from './jobs';
import { mock, MockProxy } from 'jest-mock-extended';
import { JobNotFoundError } from './errors';

describe('initJobQueue', () => {
  it.todo('write tests');
});

describe('initJobQueueWorker', () => {
  it.todo('write tests');
});

describe('initJobQueueScheduler', () => {
  it.todo('write tests');
});

describe('addSubmitTransactionJob', () => {
  it.todo('write tests');
});

describe('getJobSummary', () => {
  let mockQueue: MockProxy<Queue>;
  let mockJob: MockProxy<Job>;

  beforeEach(() => {
    mockQueue = mock<Queue>();
    mockJob = mock<Job>();
  });

  it('throws a JobNotFoundError if the Job is undefined', async () => {
    mockQueue.getJob.calledWith('1').mockResolvedValue(undefined);

    await expect(async () => {
      await getJobSummary(mockQueue, '1');
    }).rejects.toThrow(JobNotFoundError);
  });

  it('gets a job summary with transaction payload data', async () => {
    mockQueue.getJob.calledWith('1').mockResolvedValue(mockJob);
    mockJob.id = '1';
    mockJob.data = {
      transactionIds: ['txn1'],
    };
    mockJob.returnvalue = {
      transactionPayload: Buffer.from('MOCK PAYLOAD'),
    };

    expect(await getJobSummary(mockQueue, '1')).toStrictEqual({
      jobId: '1',
      transactionIds: ['txn1'],
      transactionError: undefined,
      transactionPayload: 'MOCK PAYLOAD',
    });
  });

  it('gets a job summary with empty transaction payload data', async () => {
    mockQueue.getJob.calledWith('1').mockResolvedValue(mockJob);
    mockJob.id = '1';
    mockJob.data = {
      transactionIds: ['txn1'],
    };
    mockJob.returnvalue = {
      transactionPayload: Buffer.from(''),
    };

    expect(await getJobSummary(mockQueue, '1')).toStrictEqual({
      jobId: '1',
      transactionIds: ['txn1'],
      transactionError: undefined,
      transactionPayload: '',
    });
  });

  it('gets a job summary with a transaction error', async () => {
    mockQueue.getJob.calledWith('1').mockResolvedValue(mockJob);
    mockJob.id = '1';
    mockJob.data = {
      transactionIds: ['txn1'],
    };
    mockJob.returnvalue = {
      transactionError: 'MOCK ERROR',
    };

    expect(await getJobSummary(mockQueue, '1')).toStrictEqual({
      jobId: '1',
      transactionIds: ['txn1'],
      transactionError: 'MOCK ERROR',
      transactionPayload: '',
    });
  });

  it('gets a job summary when there is no return value', async () => {
    mockQueue.getJob.calledWith('1').mockResolvedValue(mockJob);
    mockJob.id = '1';
    mockJob.returnvalue = undefined;
    mockJob.data = {
      transactionIds: ['txn1'],
    };

    expect(await getJobSummary(mockQueue, '1')).toStrictEqual({
      jobId: '1',
      transactionIds: ['txn1'],
      transactionError: undefined,
      transactionPayload: undefined,
    });
  });

  it('gets a job summary when there is no job data', async () => {
    mockQueue.getJob.calledWith('1').mockResolvedValue(mockJob);
    mockJob.id = '1';
    mockJob.data = undefined;
    mockJob.returnvalue = {
      transactionPayload: Buffer.from('MOCK PAYLOAD'),
    };

    expect(await getJobSummary(mockQueue, '1')).toStrictEqual({
      jobId: '1',
      transactionIds: [],
      transactionError: undefined,
      transactionPayload: 'MOCK PAYLOAD',
    });
  });
});

describe('updateSubmitTransactionJobStateData', () => {
  it.todo('write tests');
});

describe('getJobCounts', () => {
  it('gets job counts from the specified queue', async () => {
    const mockQueue = mock<Queue>();
    mockQueue.getJobCounts
      .calledWith('active', 'completed', 'delayed', 'failed', 'waiting')
      .mockResolvedValue({
        active: 1,
        completed: 2,
        delayed: 3,
        failed: 4,
        waiting: 5,
      });

    expect(await getJobCounts(mockQueue)).toStrictEqual({
      active: 1,
      completed: 2,
      delayed: 3,
      failed: 4,
      waiting: 5,
    });
  });
});
