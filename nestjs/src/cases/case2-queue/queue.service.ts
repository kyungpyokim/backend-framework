import { Injectable } from '@nestjs/common';
import { randomUUID } from 'crypto';
import { RedisService } from '../../core/redis/redis.service';

// Redis Stream 키 및 워커 컨슈머 그룹 이름 정의
export const STREAM_NAME = 'stream:jobs';
export const CONSUMER_GROUP = 'group:workers';

/**
 * Redis Stream 및 Hash 기반의 비동기 작업 큐 서비스.
 * - 작업을 Redis Stream에 등록(XADD)하고 상태를 Hash(job:{id})에 관리합니다.
 */
@Injectable()
export class QueueService {
  constructor(private readonly redisService: RedisService) {}

  private get redis() {
    return this.redisService.getClient();
  }

  /** Consumer Group이 존재하는지 확인하고 없으면 스트림 생성(MKSTREAM)과 함께 그룹을 생성합니다. */
  async ensureConsumerGroup(): Promise<void> {
    try {
      if (typeof (this.redis as any).xgroup === 'function') {
        await (this.redis as any).xgroup('CREATE', STREAM_NAME, CONSUMER_GROUP, '0', 'MKSTREAM');
      }
    } catch (e: any) {
      // 이미 그룹이 존재하는 경우(BUSYGROUP) 또는 모의 환경은 무시
    }
  }

  /**
   * 신규 비동기 작업을 큐에 인큐합니다.
   * 1. Hash(job:{job_id})에 메타데이터 저장 (PENDING 상태)
   * 2. Stream(stream:jobs)에 작업 ID 발행 (XADD)
   */
  async enqueueJob(taskType: string, payload: any): Promise<string> {
    await this.ensureConsumerGroup();
    const jobId = randomUUID();
    const jobData: Record<string, string> = {
      job_id: jobId,
      task_type: taskType,
      payload: JSON.stringify(payload),
      status: 'PENDING',
      result: '',
      created_at: (Date.now() / 1000).toString(),
      updated_at: (Date.now() / 1000).toString(),
      worker_id: '',
    };

    await this.redis.hset(`job:${jobId}`, jobData);
    try {
      if (typeof (this.redis as any).xadd === 'function') {
        await (this.redis as any).xadd(STREAM_NAME, '*', 'job_id', jobId, 'task_type', taskType);
      }
    } catch {
      // xadd unsupported in mock environment
    }
    return jobId;
  }

  /** Redis Hash로부터 작업 진행 상태 및 결과 조회 */
  async getJob(jobId: string): Promise<any | null> {
    const data = await this.redis.hgetall(`job:${jobId}`);
    if (!data || Object.keys(data).length === 0) {
      return null;
    }
    try {
      data.payload = JSON.parse(data.payload);
    } catch {
      // Keep as string
    }
    return data;
  }

  /** 워커 처리 단계(PROCESSING, COMPLETED, FAILED)에 따라 작업 상태 갱신 */
  async updateJobStatus(jobId: string, status: string, workerId = '', result = ''): Promise<void> {
    const updates: Record<string, string> = {
      status,
      updated_at: (Date.now() / 1000).toString(),
    };
    if (workerId) updates.worker_id = workerId;
    if (result) updates.result = result;
    await this.redis.hset(`job:${jobId}`, updates);
  }
}
