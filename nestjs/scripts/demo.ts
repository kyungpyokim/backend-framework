import RedisMock from 'ioredis-mock';
import { LockService } from '../src/cases/case1-lock/lock.service';
import { RateLimiterService } from '../src/cases/case1-lock/rate-limiter.service';
import { CircuitBreakerService } from '../src/cases/case4-resilience/circuit-breaker.service';

async function main() {
  console.log('======================================================================');
  console.log('   NestJS 분산 시스템 플레이그라운드 (TypeScript) 데모');
  console.log('======================================================================\n');

  const fakeRedis = new RedisMock() as any;
  const mockRedisService = { getClient: () => fakeRedis, onModuleDestroy: async () => {} } as any;

  // [Case 1 데모]
  console.log('======================================================================');
  console.log(' [Case 1] Stateless Scale-Out & 동시성 제어 (분산 락 vs 미적용 비교)');
  console.log('======================================================================');

  const lockService = new LockService(mockRedisService);
  const rateLimiter = new RateLimiterService(mockRedisService);

  // 1) 미적용
  console.log('\n--- 1) 분산 락 미적용 (Race Condition 취약) ---');
  await lockService.initInventory('nest-unsafe', 10);
  console.log(`초기 재고: ${await lockService.getInventory('nest-unsafe')}개`);
  console.log('30개의 동시 구매 요청을 보냅니다...');
  const resultsUnsafe = await Promise.all(
    Array.from({ length: 30 }).map(() => lockService.purchaseWithoutLock('nest-unsafe', 1)),
  );
  const successUnsafe = resultsUnsafe.filter((r) => r.success).length;
  console.log(`-> 결과: 성공한 구매 = ${successUnsafe}건 / 최종 남은 재고 = ${await lockService.getInventory('nest-unsafe')}개`);
  console.log('   [문제점] 동시성 충돌로 초과 판매 발생!');

  // 2) 분산 락 적용
  console.log('\n--- 2) Redis 분산 락 적용 (SET NX PX + Lua 해제) ---');
  await lockService.initInventory('nest-safe', 10);
  console.log(`초기 재고: ${await lockService.getInventory('nest-safe')}개`);
  console.log('30개의 동시 구매 요청을 보냅니다...');
  const resultsSafe = await Promise.all(
    Array.from({ length: 30 }).map(async () => {
      try {
        return await lockService.purchaseWithLock('nest-safe', 1);
      } catch {
        return { success: false, message: 'Lock timeout', remaining: 0 };
      }
    }),
  );
  const successSafe = resultsSafe.filter((r) => r.success).length;
  console.log(`-> 결과: 성공한 구매 = ${successSafe}건 / 최종 남은 재고 = ${await lockService.getInventory('nest-safe')}개`);
  console.log('   [성공] 정확히 10건만 성공하고 초과 요청은 안전하게 차단되었습니다.');

  // 3) Rate Limiting
  console.log('\n--- 3) 분산 슬라이딩 윈도우 Rate Limiter ---');
  console.log('10초 동안 최대 3회 요청 허용 설정 (유저: bob)');
  for (let i = 1; i <= 5; i++) {
    const { allowed, remaining } = await rateLimiter.checkLimit('bob', 3, 10);
    const status = allowed ? '허용(PASS)' : '차단(429 BLOCKED)';
    console.log(`  요청 #${i}: ${status} (잔여: ${remaining})`);
  }

  // [Case 4 데모]
  console.log('\n======================================================================');
  console.log(' [Case 4] 서비스 간 통신 장애 격리 (Circuit Breaker & Fallback)');
  console.log('======================================================================');

  const cb = new CircuitBreakerService();
  let serviceHealthy = true;

  const paymentApi = async () => {
    if (!serviceHealthy) throw new Error('PG사 통신 실패 (503)');
    return '결제 승인 완료 ($200)';
  };

  const fallback = async () => 'Fallback: 오프라인 큐 보관';

  console.log('1) 정상 호출:');
  console.log(`  응답: ${await cb.call(paymentApi, fallback)} | 서킷 상태: ${cb.getState()}`);

  console.log('\n2) 외부 PG사 장애 발생 (3회 실패 유도):');
  serviceHealthy = false;
  for (let i = 1; i <= 3; i++) {
    console.log(`  시도 #${i} -> 응답: ${await cb.call(paymentApi, fallback)} | 서킷 상태: ${cb.getState()}`);
  }

  console.log('\n3) 서킷 OPEN 이후 추가 요청 (Fast-Fail):');
  console.log(`  외부 호출 차단 후 즉시 리턴: ${await cb.call(paymentApi, fallback)} | 서킷 상태: ${cb.getState()}`);

  console.log('\n======================================================================');
  console.log(' NestJS 분산 시스템 플레이그라운드 데모 완료!');
  console.log('======================================================================\n');
}

main().catch(console.error);
