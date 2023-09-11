/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { Job, Queue } from 'bullmq';
import { Application } from 'express';
import { Contract, Transaction } from 'fabric-network';
import * as fabricProtos from 'fabric-protos';
import { mock, MockProxy } from 'jest-mock-extended';
import { jest } from '@jest/globals';
import request from 'supertest';
import * as config from '../config';
import { createServer } from '../server';

jest.mock('../config');
jest.mock('bullmq');

const mockAsset1 = {
  ID: 'asset1',
  Color: 'blue',
  Size: 5,
  Owner: 'Tomoko',
  AppraisedValue: 300,
};
const mockAsset1Buffer = Buffer.from(JSON.stringify(mockAsset1));

const mockAsset2 = {
  ID: 'asset2',
  Color: 'red',
  Size: 5,
  Owner: 'Brad',
  AppraisedValue: 400,
};

const mockAllAssetsBuffer = Buffer.from(
  JSON.stringify([mockAsset1, mockAsset2])
);

// TODO add tests for server errors
describe('Asset Transfer Besic REST API', () => {
  let app: Application;
  let mockJobQueue: MockProxy<Queue>;

  beforeEach(async () => {
    app = await createServer();

    const mockJob = mock<Job>();
    mockJob.id = '1';
    mockJobQueue = mock<Queue>();
    mockJobQueue.add.mockResolvedValue(mockJob);
    app.locals.jobq = mockJobQueue;
  });

  describe('/ready', () => {
    it('GET should respond with 200 OK json', async () => {
      const response = await request(app).get('/ready');
      expect(response.statusCode).toEqual(200);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'OK',
        timestamp: expect.any(String),
      });
    });
  });

  describe('/live', () => {
    it('GET should respond with 200 OK json', async () => {
      const mockBlockchainInfoProto =
        fabricProtos.common.BlockchainInfo.create();
      mockBlockchainInfoProto.height = 42;
      const mockBlockchainInfoBuffer = Buffer.from(
        fabricProtos.common.BlockchainInfo.encode(
          mockBlockchainInfoProto
        ).finish()
      );

      const mockOrg1QsccContract = mock<Contract>();
      mockOrg1QsccContract.evaluateTransaction
        .calledWith('GetChainInfo')
        .mockResolvedValue(mockBlockchainInfoBuffer);
      app.locals[config.mspIdOrg1] = {
        qsccContract: mockOrg1QsccContract,
      };

      const mockOrg2QsccContract = mock<Contract>();
      mockOrg2QsccContract.evaluateTransaction
        .calledWith('GetChainInfo')
        .mockResolvedValue(mockBlockchainInfoBuffer);
      app.locals[config.mspIdOrg2] = {
        qsccContract: mockOrg2QsccContract,
      };

      const response = await request(app).get('/live');
      expect(response.statusCode).toEqual(200);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'OK',
        timestamp: expect.any(String),
      });
    });
  });

  describe('/api/assets', () => {
    let mockGetAllAssetsTransaction: MockProxy<Transaction>;

    beforeEach(() => {
      mockGetAllAssetsTransaction = mock<Transaction>();
      const mockBasicContract = mock<Contract>();
      mockBasicContract.createTransaction
        .calledWith('GetAllAssets')
        .mockReturnValue(mockGetAllAssetsTransaction);
      app.locals[config.mspIdOrg1] = {
        assetContract: mockBasicContract,
      };
    });

    it('GET should respond with 401 unauthorized json when an invalid API key is specified', async () => {
      const response = await request(app)
        .get('/api/assets')
        .set('X-Api-Key', 'NOTTHERIGHTAPIKEY');
      expect(response.statusCode).toEqual(401);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        reason: 'NO_VALID_APIKEY',
        status: 'Unauthorized',
        timestamp: expect.any(String),
      });
    });

    it('GET should respond with an empty json array when there are no assets', async () => {
      mockGetAllAssetsTransaction.evaluate.mockResolvedValue(Buffer.from(''));

      const response = await request(app)
        .get('/api/assets')
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(200);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual([]);
    });

    it('GET should respond with json array of assets', async () => {
      mockGetAllAssetsTransaction.evaluate.mockResolvedValue(
        mockAllAssetsBuffer
      );

      const response = await request(app)
        .get('/api/assets')
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(200);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual([
        {
          ID: 'asset1',
          Color: 'blue',
          Size: 5,
          Owner: 'Tomoko',
          AppraisedValue: 300,
        },
        {
          ID: 'asset2',
          Color: 'red',
          Size: 5,
          Owner: 'Brad',
          AppraisedValue: 400,
        },
      ]);
    });

    it('POST should respond with 401 unauthorized json when an invalid API key is specified', async () => {
      const response = await request(app)
        .post('/api/assets')
        .send({
          ID: 'asset6',
          Color: 'white',
          Size: 15,
          Owner: 'Michel',
          AppraisedValue: 800,
        })
        .set('X-Api-Key', 'NOTTHERIGHTAPIKEY');
      expect(response.statusCode).toEqual(401);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        reason: 'NO_VALID_APIKEY',
        status: 'Unauthorized',
        timestamp: expect.any(String),
      });
    });

    it('POST should respond with 400 bad request json for invalid asset json', async () => {
      const response = await request(app)
        .post('/api/assets')
        .send({
          wrongidfield: 'asset3',
          Color: 'red',
          Size: 5,
          Owner: 'Brad',
          AppraisedValue: 400,
        })
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(400);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'Bad Request',
        reason: 'VALIDATION_ERROR',
        errors: [
          {
            location: 'body',
            msg: 'must be a string',
            param: 'ID',
          },
        ],
        message: 'Invalid request body',
        timestamp: expect.any(String),
      });
    });

    it('POST should respond with 202 accepted json', async () => {
      const response = await request(app)
        .post('/api/assets')
        .send({
          ID: 'asset3',
          Color: 'red',
          Size: 5,
          Owner: 'Brad',
          AppraisedValue: 400,
        })
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(202);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'Accepted',
        jobId: '1',
        timestamp: expect.any(String),
      });
    });
  });

  describe('/api/assets/:id', () => {
    let mockAssetExistsTransaction: MockProxy<Transaction>;
    let mockReadAssetTransaction: MockProxy<Transaction>;

    beforeEach(() => {
      const mockBasicContract = mock<Contract>();

      mockAssetExistsTransaction = mock<Transaction>();
      mockBasicContract.createTransaction
        .calledWith('AssetExists')
        .mockReturnValue(mockAssetExistsTransaction);

      mockReadAssetTransaction = mock<Transaction>();
      mockBasicContract.createTransaction
        .calledWith('ReadAsset')
        .mockReturnValue(mockReadAssetTransaction);

      app.locals[config.mspIdOrg1] = {
        assetContract: mockBasicContract,
      };
    });

    it('OPTIONS should respond with 401 unauthorized json when an invalid API key is specified', async () => {
      const response = await request(app)
        .options('/api/assets/asset1')
        .set('X-Api-Key', 'NOTTHERIGHTAPIKEY');
      expect(response.statusCode).toEqual(401);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        reason: 'NO_VALID_APIKEY',
        status: 'Unauthorized',
        timestamp: expect.any(String),
      });
    });

    it('OPTIONS should respond with 404 not found json without the allow header when there is no asset with the specified ID', async () => {
      mockAssetExistsTransaction.evaluate
        .calledWith('asset3')
        .mockResolvedValue(Buffer.from('false'));

      const response = await request(app)
        .options('/api/assets/asset3')
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(404);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.header).not.toHaveProperty('allow');
      expect(response.body).toEqual({
        status: 'Not Found',
        timestamp: expect.any(String),
      });
    });

    it('OPTIONS should respond with 200 OK json with the allow header', async () => {
      mockAssetExistsTransaction.evaluate
        .calledWith('asset1')
        .mockResolvedValue(Buffer.from('true'));

      const response = await request(app)
        .options('/api/assets/asset1')
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(200);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.header).toHaveProperty(
        'allow',
        'DELETE,GET,OPTIONS,PATCH,PUT'
      );
      expect(response.body).toEqual({
        status: 'OK',
        timestamp: expect.any(String),
      });
    });

    it('GET should respond with 401 unauthorized json when an invalid API key is specified', async () => {
      const response = await request(app)
        .get('/api/assets/asset1')
        .set('X-Api-Key', 'NOTTHERIGHTAPIKEY');
      expect(response.statusCode).toEqual(401);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        reason: 'NO_VALID_APIKEY',
        status: 'Unauthorized',
        timestamp: expect.any(String),
      });
    });

    it('GET should respond with 404 not found json when there is no asset with the specified ID', async () => {
      mockReadAssetTransaction.evaluate
        .calledWith('asset3')
        .mockRejectedValue(new Error('the asset asset3 does not exist'));

      const response = await request(app)
        .get('/api/assets/asset3')
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(404);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'Not Found',
        timestamp: expect.any(String),
      });
    });

    it('GET should respond with the asset json when the asset exists', async () => {
      mockReadAssetTransaction.evaluate
        .calledWith('asset1')
        .mockResolvedValue(mockAsset1Buffer);

      const response = await request(app)
        .get('/api/assets/asset1')
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(200);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        ID: 'asset1',
        Color: 'blue',
        Size: 5,
        Owner: 'Tomoko',
        AppraisedValue: 300,
      });
    });

    it('PUT should respond with 401 unauthorized json when an invalid API key is specified', async () => {
      const response = await request(app)
        .put('/api/assets/asset1')
        .send({
          ID: 'asset3',
          Color: 'red',
          Size: 5,
          Owner: 'Brad',
          AppraisedValue: 400,
        })
        .set('X-Api-Key', 'NOTTHERIGHTAPIKEY');
      expect(response.statusCode).toEqual(401);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        reason: 'NO_VALID_APIKEY',
        status: 'Unauthorized',
        timestamp: expect.any(String),
      });
    });

    it('PUT should respond with 400 bad request json when IDs do not match', async () => {
      const response = await request(app)
        .put('/api/assets/asset1')
        .send({
          ID: 'asset2',
          Color: 'red',
          Size: 5,
          Owner: 'Brad',
          AppraisedValue: 400,
        })
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(400);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'Bad Request',
        reason: 'ASSET_ID_MISMATCH',
        message: 'Asset IDs must match',
        timestamp: expect.any(String),
      });
    });

    it('PUT should respond with 400 bad request json for invalid asset json', async () => {
      const response = await request(app)
        .put('/api/assets/asset1')
        .send({
          wrongID: 'asset1',
          Color: 'red',
          Size: 5,
          Owner: 'Brad',
          AppraisedValue: 400,
        })
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(400);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'Bad Request',
        reason: 'VALIDATION_ERROR',
        errors: [
          {
            location: 'body',
            msg: 'must be a string',
            param: 'ID',
          },
        ],
        message: 'Invalid request body',
        timestamp: expect.any(String),
      });
    });

    it('PUT should respond with 202 accepted json', async () => {
      const response = await request(app)
        .put('/api/assets/asset1')
        .send({
          ID: 'asset1',
          Color: 'red',
          Size: 5,
          Owner: 'Brad',
          AppraisedValue: 400,
        })
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(202);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'Accepted',
        jobId: '1',
        timestamp: expect.any(String),
      });
    });

    it('PATCH should respond with 401 unauthorized json when an invalid API key is specified', async () => {
      const response = await request(app)
        .patch('/api/assets/asset1')
        .send([{ op: 'replace', path: '/Owner', value: 'Ashleigh' }])
        .set('X-Api-Key', 'NOTTHERIGHTAPIKEY');
      expect(response.statusCode).toEqual(401);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        reason: 'NO_VALID_APIKEY',
        status: 'Unauthorized',
        timestamp: expect.any(String),
      });
    });

    it('PATCH should respond with 400 bad request json for invalid patch op/path', async () => {
      const response = await request(app)
        .patch('/api/assets/asset1')
        .send([{ op: 'replace', path: '/color', value: 'orange' }])
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(400);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'Bad Request',
        reason: 'VALIDATION_ERROR',
        errors: [
          {
            location: 'body',
            msg: "path must be '/Owner'",
            param: '[0].path',
            value: '/color',
          },
        ],
        message: 'Invalid request body',
        timestamp: expect.any(String),
      });
    });

    it('PATCH should respond with 202 accepted json', async () => {
      const response = await request(app)
        .patch('/api/assets/asset1')
        .send([{ op: 'replace', path: '/Owner', value: 'Ashleigh' }])
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(202);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'Accepted',
        jobId: '1',
        timestamp: expect.any(String),
      });
    });

    it('DELETE should respond with 401 unauthorized json when an invalid API key is specified', async () => {
      const response = await request(app)
        .delete('/api/assets/asset1')
        .set('X-Api-Key', 'NOTTHERIGHTAPIKEY');
      expect(response.statusCode).toEqual(401);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        reason: 'NO_VALID_APIKEY',
        status: 'Unauthorized',
        timestamp: expect.any(String),
      });
    });

    it('DELETE should respond with 202 accepted json', async () => {
      const response = await request(app)
        .delete('/api/assets/asset1')
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(202);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'Accepted',
        jobId: '1',
        timestamp: expect.any(String),
      });
    });
  });

  describe('/api/jobs/:id', () => {
    it('GET should respond with 401 unauthorized json when an invalid API key is specified', async () => {
      const response = await request(app)
        .get('/api/jobs/1')
        .set('X-Api-Key', 'NOTTHERIGHTAPIKEY');
      expect(response.statusCode).toEqual(401);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        reason: 'NO_VALID_APIKEY',
        status: 'Unauthorized',
        timestamp: expect.any(String),
      });
    });

    it('GET should respond with 404 not found json when there is no job with the specified ID', async () => {
      jest.mocked(Job.fromId).mockResolvedValue(undefined);

      const response = await request(app)
        .get('/api/jobs/3')
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(404);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'Not Found',
        timestamp: expect.any(String),
      });
    });

    it('GET should respond with json details for the specified job ID', async () => {
      const mockJob = mock<Job>();
      mockJob.id = '2';
      mockJob.data = {
        transactionIds: ['txn1', 'txn2'],
      };
      mockJob.returnvalue = {
        transactionError: 'Mock error',
        transactionPayload: Buffer.from('Mock payload'),
      };
      mockJobQueue.getJob.calledWith('2').mockResolvedValue(mockJob);

      const response = await request(app)
        .get('/api/jobs/2')
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(200);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        jobId: '2',
        transactionIds: ['txn1', 'txn2'],
        transactionError: 'Mock error',
        transactionPayload: 'Mock payload',
      });
    });
  });

  describe('/api/transactions/:id', () => {
    let mockGetTransactionByIDTransaction: MockProxy<Transaction>;

    beforeEach(() => {
      mockGetTransactionByIDTransaction = mock<Transaction>();
      const mockQsccContract = mock<Contract>();
      mockQsccContract.createTransaction
        .calledWith('GetTransactionByID')
        .mockReturnValue(mockGetTransactionByIDTransaction);
      app.locals[config.mspIdOrg1] = {
        qsccContract: mockQsccContract,
      };
    });

    it('GET should respond with 401 unauthorized json when an invalid API key is specified', async () => {
      const response = await request(app)
        .get('/api/transactions/txn1')
        .set('X-Api-Key', 'NOTTHERIGHTAPIKEY');
      expect(response.statusCode).toEqual(401);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        reason: 'NO_VALID_APIKEY',
        status: 'Unauthorized',
        timestamp: expect.any(String),
      });
    });

    it('GET should respond with 404 not found json when there is no transaction with the specified ID', async () => {
      mockGetTransactionByIDTransaction.evaluate
        .calledWith('mychannel', 'txn3')
        .mockRejectedValue(
          new Error(
            'Failed to get transaction with id txn3, error Entry not found in index'
          )
        );

      const response = await request(app)
        .get('/api/transactions/txn3')
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(404);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'Not Found',
        timestamp: expect.any(String),
      });
    });

    it('GET should respond with json details for the specified transaction ID', async () => {
      const processedTransactionProto =
        fabricProtos.protos.ProcessedTransaction.create();
      processedTransactionProto.validationCode =
        fabricProtos.protos.TxValidationCode.VALID;
      const processedTransactionBuffer = Buffer.from(
        fabricProtos.protos.ProcessedTransaction.encode(
          processedTransactionProto
        ).finish()
      );
      mockGetTransactionByIDTransaction.evaluate
        .calledWith('mychannel', 'txn2')
        .mockResolvedValue(processedTransactionBuffer);

      const response = await request(app)
        .get('/api/transactions/txn2')
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(200);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        transactionId: 'txn2',
        validationCode: 'VALID',
      });
    });
  });
});
