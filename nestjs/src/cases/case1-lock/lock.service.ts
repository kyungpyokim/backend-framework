import { Injectable } from '@nestjs/common';
import { randomUUID } from 'crypto';
import { RedisService } from '../../core/redis/redis.service';

const RELEASE_LOCK_LUA = `
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end
`;

@Injectable()
export class LockService {
  constructor(private readonly redisService: RedisService) {}

  private get redis() {
    return this.redisService.getClient();
  }

  async acquireLock(resource: string, ttlMs = 5000, maxRetries = 20, retryDelayMs = 50): Promise<string | null> {
    const token = randomUUID();
    const lockKey = `lock:${resource}`;

    for (let i = 0; i < maxRetries; i++) {
      const result = await this.redis.set(lockKey, token, 'PX', ttlMs, 'NX');
      if (result === 'OK') {
        return token;
      }
      await new Promise((r) => setTimeout(r, retryDelayMs));
    }
    return null;
  }

  async releaseLock(resource: string, token: string): Promise<boolean> {
    const lockKey = `lock:${resource}`;
    const result = await this.redis.eval(RELEASE_LOCK_LUA, 1, lockKey, token);
    return result === 1;
  }

  async initInventory(itemId: string, stock: number): Promise<void> {
    await this.redis.set(`stock:${itemId}`, stock.toString());
  }

  async getInventory(itemId: string): Promise<number> {
    const val = await this.redis.get(`stock:${itemId}`);
    return val !== null ? parseInt(val, 10) : 0;
  }

  async purchaseWithLock(itemId: string, quantity = 1): Promise<{ success: boolean; message: string; remaining: number }> {
    const token = await this.acquireLock(`inventory:${itemId}`);
    if (!token) {
      throw new Error('Lock acquisition timeout');
    }

    try {
      const currentStock = await this.getInventory(itemId);
      if (currentStock < quantity) {
        return { success: false, message: 'Out of stock', remaining: currentStock };
      }
      // Artificial delay to simulate DB latency
      await new Promise((r) => setTimeout(r, 10));
      const newStock = currentStock - quantity;
      await this.redis.set(`stock:${itemId}`, newStock.toString());
      return { success: true, message: 'Purchase successful', remaining: newStock };
    } finally {
      await this.releaseLock(`inventory:${itemId}`, token);
    }
  }

  async purchaseWithoutLock(itemId: string, quantity = 1): Promise<{ success: boolean; message: string; remaining: number }> {
    const currentStock = await this.getInventory(itemId);
    if (currentStock < quantity) {
      return { success: false, message: 'Out of stock', remaining: currentStock };
    }
    // Race condition window
    await new Promise((r) => setTimeout(r, 10));
    const newStock = currentStock - quantity;
    await this.redis.set(`stock:${itemId}`, newStock.toString());
    return { success: true, message: 'Purchase successful', remaining: newStock };
  }
}
