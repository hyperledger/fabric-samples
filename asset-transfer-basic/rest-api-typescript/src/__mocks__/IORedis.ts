import { RedisOptions } from 'ioredis';

class IORedis {
  redisOptions: RedisOptions;
  constructor(options: RedisOptions) {
    this.redisOptions = options;
  }

  hincrby = jest.fn().mockReturnThis();
  multi = jest.fn().mockReturnThis();
  del = jest.fn().mockReturnThis();

  zrem = jest.fn().mockReturnThis();

  exec = jest.fn().mockReturnThis();

  hset = jest.fn().mockReturnThis();
  zadd = jest.fn().mockReturnThis();
}

export default IORedis;
