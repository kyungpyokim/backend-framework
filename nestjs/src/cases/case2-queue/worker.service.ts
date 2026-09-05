import { Injectable, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { QueueService, STREAM_NAME, CONSUMER_GROUP } from './queue.service';
import { RedisService } from '../../core/redis/redis.service';
import { config } from '../../config/configuration';

/**
 * Redis Stream 기반 백그라운드 워커 서비스.
 * - OnModuleInit 라이프사이클에서 RUN_WORKER 플래그 확인 후 비동기 폴링 루프 실행
 * - XREADGROUP을 통해 작업을 중복 없이 수신하고 완료 시 XACK로 승인
 */
@Injectable()
export class WorkerService implements OnModuleInit, OnModuleDestroy {
  private isRunning = false;
  private workerId: string;

  constructor(
    private readonly queueService: QueueService,
    private readonly redisService: RedisService,
  ) {
    this.workerId = `${config.nodeId}-worker`;
  }

  /** 모듈 초기화 시 RUN_WORKER가 true이면 백그라운드 작업 처리 루프를 구동 */
  onModuleInit() {
    if (process.env.RUN_WORKER === 'true') {
      this.isRunning = true;
      this.startLoop();
    }
  }

  /** 애플리케이션 종료 시 워커 루프 정지 */
  onModuleDestroy() {
    this.isRunning = false;
  }

  /**
   * Redis Stream Consumer Group으로부터 작업을 1건 소비하여 처리.
   * 1. XREADGROUP으로 미전달 메시지('>') 블로킹 대기
   * 2. 작업 상태 'PROCESSING'으로 갱신
   * 3. 비즈니스 로직 수행 후 성공 시 'COMPLETED', 실패 시 'FAILED'로 갱신
   * 4. XACK 전송으로 스트림 처리 완료 통보
   */
  async processOneJob(timeoutMs = 1000): Promise<boolean> {
    const redis = this.redisService.getClient();
    await this.queueService.ensureConsumerGroup();

    try {
      const response = await (redis as any).xreadgroup(
        'GROUP',
        CONSUMER_GROUP,
        this.workerId,
        'COUNT',
        1,
        'BLOCK',
        timeoutMs,
        'STREAMS',
        STREAM_NAME,
        '>',
      );

      if (!response || response.length === 0) {
        return false;
      }

      const [streamName, messages] = response[0];
      for (const [msgId, fields] of messages) {
        const data: Record<string, string> = {};
        for (let i = 0; i < fields.length; i += 2) {
          data[fields[i]] = fields[i + 1];
        }

        const jobId = data.job_id;
        if (!jobId) {
          await redis.xack(STREAM_NAME, CONSUMER_GROUP, msgId);
          continue;
        }

        await this.queueService.updateJobStatus(jobId, 'PROCESSING', this.workerId);

        try {
          const job = await this.queueService.getJob(jobId);
          const duration = job?.payload?.duration_sec || 0;
          await new Promise((r) => setTimeout(r, Math.min(duration * 1000, 2000)));
          const resultStr = `Processed '${data.task_type}' successfully in ${duration}s`;
          await this.queueService.updateJobStatus(jobId, 'COMPLETED', this.workerId, resultStr);
        } catch (err: any) {
          await this.queueService.updateJobStatus(jobId, 'FAILED', this.workerId, err.message);
        } finally {
          await redis.xack(STREAM_NAME, CONSUMER_GROUP, msgId);
        }
      }
      return true;
    } catch {
      return false;
    }
  }

  /** 워커 무한 폴링 루프 */
  private async startLoop() {
    console.log(`[${this.workerId}] Worker started listening on Redis Stream...`);
    while (this.isRunning) {
      await this.processOneJob(2000);
    }
  }
}
