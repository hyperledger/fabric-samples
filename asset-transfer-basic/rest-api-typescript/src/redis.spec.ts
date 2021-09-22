/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { isMaxmemoryPolicyNoeviction } from './redis';

const mockRedisConfig = jest.fn();
jest.mock('ioredis', () => {
  return jest.fn().mockImplementation(() => {
    return {
      config: mockRedisConfig,
      disconnect: jest.fn(),
    };
  });
});
jest.mock('./config');

describe('Redis', () => {
  beforeEach(() => {
    mockRedisConfig.mockClear();
  });

  describe('isMaxmemoryPolicyNoeviction', () => {
    it('returns true when the maxmemory-policy is noeviction', async () => {
      mockRedisConfig.mockReturnValue(['maxmemory-policy', 'noeviction']);
      expect(await isMaxmemoryPolicyNoeviction()).toBe(true);
    });

    it('returns false when the maxmemory-policy is not noeviction', async () => {
      mockRedisConfig.mockReturnValue(['maxmemory-policy', 'allkeys-lru']);
      expect(await isMaxmemoryPolicyNoeviction()).toBe(false);
    });
  });
});
