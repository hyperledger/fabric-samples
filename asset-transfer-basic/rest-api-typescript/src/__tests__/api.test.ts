/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { createServer } from '../server';
import { Application } from 'express';
import request from 'supertest';

jest.mock('../config');
jest.mock('fabric-network');
jest.mock('ioredis');

describe('Asset Transfer Besic REST API', () => {
  let app: Application;

  beforeEach(async () => {
    app = await createServer();
  });

  describe('GET /ready', () => {
    it('should respond with success json', async () => {
      const response = await request(app).get('/ready');
      expect(response.statusCode).toEqual(200);
      expect(response.header).toHaveProperty(
        'content-type',
        'application/json; charset=utf-8'
      );
      expect(response.body.status).toEqual('OK');
    });
  });
});
