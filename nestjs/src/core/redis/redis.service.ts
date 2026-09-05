import { Injectable, OnModuleDestroy } from '@nestjs/common';
import Redis from 'ioredis';
import { config } from '../../config/configuration';

/**
 * NestJS 전역 Redis 클라이언트 서비스 (ioredis 기반).
 * 모듈 수명 주기와 연동하여 연결 생성 및 애플리케이션 종료 시 안전하게 연결을 해제합니다.
 */
@Injectable()
export class RedisService implements OnModuleDestroy {
  private client: Redis;

  constructor() {
    this.client = new Redis(config.redisUrl, {
      maxRetriesPerRequest: 1,
      lazyConnect: true,
      retryStrategy: (times) => Math.min(times * 100, 2000),
    });
    // 백그라운드에서 비동기 연결 시도 (연결 실패 시에도 앱 부트스트랩이 블로킹되지 않도록 경고만 출력)
    this.client.connect().catch((err) => {
      console.warn(`[${config.nodeId}] Redis connection skipped or failed: ${err.message}`);
    });
  }

  /** 활성화된 ioredis 클라이언트 인스턴스를 반환합니다. */
  getClient(): Redis {
    return this.client;
  }

  /** NestJS 모듈 소멸 시(앱 종료 시) Redis 연결을 정상적으로 종료(quit/disconnect)합니다. */
  async onModuleDestroy() {
    try {
      await this.client.quit();
    } catch {
      this.client.disconnect();
    }
  }
}
