/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { Job, Queue } from 'bullmq';
import {
  addSubmitTransactionJob,
  getJobCounts,
  getJobSummary,
  processSubmitTransactionJob,
  JobNotFoundError,
  updateJobData,
} from './jobs';
import { Contract, Transaction } from 'fabric-network';
import { mock, MockProxy } from 'jest-mock-extended';
import { Application } from 'express';

describe('addSubmitTransactionJob', () => {
  let mockJob: MockProxy<Job>;
  let mockQueue: MockProxy<Queue>;

  beforeEach(() => {
    mockJob = mock<Job>();
    mockQueue = mock<Queue>();
    mockQueue.add.mockResolvedValue(mockJob);
  });

  it('returns the new job ID', async () => {
    mockJob.id = 'mockJobId';

    const jobid = await addSubmitTransactionJob(
      mockQueue,
      'mockMspId',
      'txn',
      'arg1',
      'arg2'
    );

    expect(jobid).toBe('mockJobId');
  });

  it('throws an error if there is no job ID', async () => {
    mockJob.id = undefined;

    await expect(async () => {
      await addSubmitTransactionJob(
        mockQueue,
        'mockMspId',
        'txn',
        'arg1',
        'arg2'
      );
    }).rejects.toThrowError('Submit transaction job ID not available');
  });
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

describe('updateJobData', () => {
  let mockJob: MockProxy<Job>;

  beforeEach(() => {
    mockJob = mock<Job>();
    mockJob.data = {
      transactionIds: ['txn1'],
    };
  });

  it('stores the serialized state in the job data if a transaction is specified', async () => {
    const mockSavedState = Buffer.from('MOCK SAVED STATE');
    const mockTransaction = mock<Transaction>();
    mockTransaction.getTransactionId.mockReturnValue('txn2');
    mockTransaction.serialize.mockReturnValue(mockSavedState);

    await updateJobData(mockJob, mockTransaction);

    expect(mockJob.update).toBeCalledTimes(1);
    expect(mockJob.update).toBeCalledWith({
      transactionIds: ['txn1', 'txn2'],
      transactionState: mockSavedState,
    });
  });

  it('removes the serialized state from the job data if a transaction is not specified', async () => {
    await updateJobData(mockJob, undefined);

    expect(mockJob.update).toBeCalledTimes(1);
    expect(mockJob.update).toBeCalledWith({
      transactionIds: ['txn1'],
      transactionState: undefined,
    });
  });
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

  describe('processSubmitTransactionJob', () => {
    const mockContracts = new Map<string, Contract>();
    const mockPayload = Buffer.from('MOCK PAYLOAD');
    const mockSavedState = Buffer.from('MOCK SAVED STATE');
    let mockTransaction: MockProxy<Transaction>;
    let mockContract: MockProxy<Contract>;
    let mockApplication: MockProxy<Application>;
    let mockJob: MockProxy<Job>;

    beforeEach(() => {
      mockTransaction = mock<Transaction>();
      mockTransaction.getTransactionId.mockReturnValue('mockTransactionId');

      mockContract = mock<Contract>();
      mockContract.createTransaction
        .calledWith('txn')
        .mockReturnValue(mockTransaction);
      mockContract.deserializeTransaction
        .calledWith(mockSavedState)
        .mockReturnValue(mockTransaction);
      mockContracts.set('mockMspid', mockContract);

      mockApplication = mock<Application>();
      mockApplication.locals.mockMspid = { assetContract: mockContract };

      mockJob = mock<Job>();
    });

    it('gets job result with no error or payload if no contract is available for the required mspid', async () => {
      mockJob.data = {
        mspid: 'missingMspid',
      };

      const jobResult = await processSubmitTransactionJob(
        mockApplication,
        mockJob
      );

      expect(jobResult).toStrictEqual({
        transactionError: undefined,
        transactionPayload: undefined,
      });
    });

    it('gets a job result containing a payload if the transaction was successful first time', async () => {
      mockJob.data = {
        mspid: 'mockMspid',
        transactionName: 'txn',
        transactionArgs: ['arg1', 'arg2'],
      };
      mockTransaction.submit
        .calledWith('arg1', 'arg2')
        .mockResolvedValue(mockPayload);

      const jobResult = await processSubmitTransactionJob(
        mockApplication,
        mockJob
      );

      expect(jobResult).toStrictEqual({
        transactionError: undefined,
        transactionPayload: Buffer.from('MOCK PAYLOAD'),
      });
    });

    it('gets a job result containing a payload if the transaction was successfully rerun using saved transaction state', async () => {
      mockJob.data = {
        mspid: 'mockMspid',
        transactionName: 'txn',
        transactionArgs: ['arg1', 'arg2'],
        transactionState: mockSavedState,
      };
      mockTransaction.submit
        .calledWith('arg1', 'arg2')
        .mockResolvedValue(mockPayload);

      const jobResult = await processSubmitTransactionJob(
        mockApplication,
        mockJob
      );

      expect(jobResult).toStrictEqual({
        transactionError: undefined,
        transactionPayload: Buffer.from('MOCK PAYLOAD'),
      });
    });

    it('gets a job result containing an error message if the transaction fails but cannot be retried', async () => {
      mockJob.data = {
        mspid: 'mockMspid',
        transactionName: 'txn',
        transactionArgs: ['arg1', 'arg2'],
        transactionState: mockSavedState,
      };
      mockTransaction.submit
        .calledWith('arg1', 'arg2')
        .mockRejectedValue(
          new Error(
            'Failed to get transaction with id txn, error Entry not found in index'
          )
        );

      const jobResult = await processSubmitTransactionJob(
        mockApplication,
        mockJob
      );

      expect(jobResult).toStrictEqual({
        transactionError:
          'TransactionNotFoundError: Failed to get transaction with id txn, error Entry not found in index',
        transactionPayload: undefined,
      });
    });

    it('throws an error if the transaction fails but can be retried', async () => {
      mockJob.data = {
        mspid: 'mockMspid',
        transactionName: 'txn',
        transactionArgs: ['arg1', 'arg2'],
        transactionState: mockSavedState,
      };
      mockTransaction.submit
        .calledWith('arg1', 'arg2')
        .mockRejectedValue(new Error('MOCK ERROR'));

      await expect(async () => {
        await processSubmitTransactionJob(mockApplication, mockJob);
      }).rejects.toThrow('MOCK ERROR');
    });
  });
});
