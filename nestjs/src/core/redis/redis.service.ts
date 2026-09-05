import { Injectable, OnModuleDestroy } from '@nestjs/common';
import Redis from 'ioredis';
import { config } from '../../config/configuration';

@Injectable()
export class RedisService implements OnModuleDestroy {
  private client: Redis;

  constructor() {
    this.client = new Redis(config.redisUrl, {
      maxRetriesPerRequest: 1,
      lazyConnect: true,
      retryStrategy: (times) => Math.min(times * 100, 2000),
    });
    this.client.connect().catch((err) => {
      console.warn(`[${config.nodeId}] Redis connection skipped or failed: ${err.message}`);
    });
  }

  getClient(): Redis {
    return this.client;
  }

  async onModuleDestroy() {
    try {
      await this.client.quit();
    } catch {
      this.client.disconnect();
    }
  }
}
