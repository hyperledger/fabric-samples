/*
 * SPDX-License-Identifier: Apache-2.0
 */

jest.mock('fabric-network');
jest.mock('ioredis', () => require('ioredis-mock/jest'));

import { createServer } from '../server';
import { Application } from 'express';
import request from 'supertest';

// TODO add tests for server errors
// TODO implement 405 Method Not Allowed where appropriate and add tests
describe('Asset Transfer Besic REST API', () => {
  let app: Application;

  beforeEach(async () => {
    app = await createServer();
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
      // NOTE: only the first mocked GetAllAssets with return no assets
      // TODO find a better alternative so that test order does not matter
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
      // NOTE: only the second mocked GetAllAssets with return no assets
      // TODO find a better alternative so that test order does not matter
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
          identifier: 'asset3',
          color: 'red',
          size: 5,
          owner: 'Brad',
          appraisedValue: 400,
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
            param: 'id',
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
          id: 'asset3',
          color: 'red',
          size: 5,
          owner: 'Brad',
          appraisedValue: 400,
        })
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(202);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'Accepted',
        transactionId: 'txn1',
        timestamp: expect.any(String),
      });
    });

    it('POST should respond with 409 conflict json when asset already exists', async () => {
      const response = await request(app)
        .post('/api/assets')
        .send({
          id: 'asset1',
          color: 'blue',
          size: 5,
          owner: 'Tomoko',
          appraisedValue: 300,
        })
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(409);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'Conflict',
        reason: 'ASSET_EXISTS',
        message: 'the asset asset1 already exists',
        timestamp: expect.any(String),
      });
    });
  });

  describe('/api/assets/:id', () => {
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
          id: 'asset3',
          color: 'red',
          size: 5,
          owner: 'Brad',
          appraisedValue: 400,
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

    it('PUT should respond with 404 not found json when there is no asset with the specified ID', async () => {
      const response = await request(app)
        .put('/api/assets/asset3')
        .send({
          id: 'asset3',
          color: 'red',
          size: 5,
          owner: 'Brad',
          appraisedValue: 400,
        })
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

    it('PUT should respond with 400 bad request json when IDs do not match', async () => {
      const response = await request(app)
        .put('/api/assets/asset1')
        .send({
          id: 'asset2',
          color: 'red',
          size: 5,
          owner: 'Brad',
          appraisedValue: 400,
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
          identifier: 'asset1',
          color: 'red',
          size: 5,
          owner: 'Brad',
          appraisedValue: 400,
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
            param: 'id',
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
          id: 'asset1',
          color: 'red',
          size: 5,
          owner: 'Brad',
          appraisedValue: 400,
        })
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(202);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'Accepted',
        transactionId: 'txn1',
        timestamp: expect.any(String),
      });
    });

    it('PATCH should respond with 401 unauthorized json when an invalid API key is specified', async () => {
      const response = await request(app)
        .patch('/api/assets/asset1')
        .send([{ op: 'replace', path: '/owner', value: 'Ashleigh' }])
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

    it('PATCH should respond with 404 not found json when there is no asset with the specified ID', async () => {
      const response = await request(app)
        .patch('/api/assets/asset3')
        .send([{ op: 'replace', path: '/owner', value: 'Ashleigh' }])
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
            msg: "path must be '/owner'",
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
        .send([{ op: 'replace', path: '/owner', value: 'Ashleigh' }])
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(202);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'Accepted',
        transactionId: 'txn1',
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

    it('DELETE should respond with 404 not found json when there is no asset with the specified ID', async () => {
      const response = await request(app)
        .delete('/api/assets/asset3')
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
        transactionId: 'txn1',
        timestamp: expect.any(String),
      });
    });
  });

  describe('/api/transactions/:id', () => {
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
      const response = await request(app)
        .get('/api/transactions/txn1')
        .set('X-Api-Key', 'ORG1MOCKAPIKEY');
      expect(response.statusCode).toEqual(200);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body).toEqual({
        status: 'OK',
        progress: 'DONE',
        validationCode: 'VALID',
        timestamp: expect.any(String),
      });
    });
  });
});
