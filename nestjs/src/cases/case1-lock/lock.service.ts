import { Injectable } from '@nestjs/common';
import { randomUUID } from 'crypto';
import { RedisService } from '../../core/redis/redis.service';

// 락을 안전하게 해제하기 위한 Lua 스크립트.
// 현재 락 키의 값이 획득 시 발급된 토큰과 일치할 때만 원자적으로 삭제하여
// 타 요청에 의해 새로 획득된 락을 오삭제하지 않도록 보장합니다.
const RELEASE_LOCK_LUA = `
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end
`;

/**
 * Redis 기반 분산 락 및 재고 관리 서비스.
 * 다중 인스턴스 환경에서 공유 자원(상품 재고)에 대한 동시성 제어를 담당합니다.
 */
@Injectable()
export class LockService {
  constructor(private readonly redisService: RedisService) {}

  private get redis() {
    return this.redisService.getClient();
  }

  /**
   * Redis SET NX PX를 활용한 분산 락 획득 시도.
   *
   * @param resource 락 대상 자원 식별자
   * @param ttlMs 락 만료 시간 (기본값: 5000ms)
   * @param maxRetries 최대 재시도 횟수 (기본값: 20회)
   * @param retryDelayMs 재시도 대기 간격 (기본값: 50ms)
   * @returns 락 획득 성공 시 고유 토큰 문자열, 실패 시 null
   */
  async acquireLock(resource: string, ttlMs = 5000, maxRetries = 20, retryDelayMs = 50): Promise<string | null> {
    const token = randomUUID();
    const lockKey = `lock:${resource}`;

    for (let i = 0; i < maxRetries; i++) {
      // SET lock:resource token PX ttlMs NX (원자적 락 선점)
      const result = await this.redis.set(lockKey, token, 'PX', ttlMs, 'NX');
      if (result === 'OK') {
        return token;
      }
      await new Promise((r) => setTimeout(r, retryDelayMs));
    }
    return null;
  }

  /**
   * 보유한 고유 토큰을 검증하여 분산 락을 안전하게 해제.
   */
  async releaseLock(resource: string, token: string): Promise<boolean> {
    const lockKey = `lock:${resource}`;
    const result = await this.redis.eval(RELEASE_LOCK_LUA, 1, lockKey, token);
    return result === 1;
  }

  /** 상품 재고 수량 초기화 */
  async initInventory(itemId: string, stock: number): Promise<void> {
    await this.redis.set(`stock:${itemId}`, stock.toString());
  }

  /** 현재 상품 재고 수량 조회 */
  async getInventory(itemId: string): Promise<number> {
    const val = await this.redis.get(`stock:${itemId}`);
    return val !== null ? parseInt(val, 10) : 0;
  }

  /**
   * [동시성 안전] 분산 락을 적용한 재고 차감 구매 로직.
   * 락 획득 -> 재고 조회 -> 검증 -> 차감 -> 락 해제(finally) 흐름을 보장합니다.
   */
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
      // 인위적 DB 처리 지연 시뮬레이션
      await new Promise((r) => setTimeout(r, 10));
      const newStock = currentStock - quantity;
      await this.redis.set(`stock:${itemId}`, newStock.toString());
      return { success: true, message: 'Purchase successful', remaining: newStock };
    } finally {
      await this.releaseLock(`inventory:${itemId}`, token);
    }
  }

  /**
   * [동시성 취약] 분산 락 미적용 구매 로직 (Race Condition 학습 및 시연용).
   * 동시 다발적 요청 인입 시 동일한 현재 재고를 읽어 초과 판매가 발생합니다.
   */
  async purchaseWithoutLock(itemId: string, quantity = 1): Promise<{ success: boolean; message: string; remaining: number }> {
    const currentStock = await this.getInventory(itemId);
    if (currentStock < quantity) {
      return { success: false, message: 'Out of stock', remaining: currentStock };
    }
    // 동시성 결함 발생 창(Race condition window)
    await new Promise((r) => setTimeout(r, 10));
    const newStock = currentStock - quantity;
    await this.redis.set(`stock:${itemId}`, newStock.toString());
    return { success: true, message: 'Purchase successful', remaining: newStock };
  }
}
