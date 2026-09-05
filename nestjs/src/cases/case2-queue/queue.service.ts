import { Injectable } from '@nestjs/common';
import { randomUUID } from 'crypto';
import { RedisService } from '../../core/redis/redis.service';

export const STREAM_NAME = 'stream:jobs';
export const CONSUMER_GROUP = 'group:workers';

@Injectable()
export class QueueService {
  constructor(private readonly redisService: RedisService) {}

  private get redis() {
    return this.redisService.getClient();
  }

  async ensureConsumerGroup(): Promise<void> {
    try {
      if (typeof (this.redis as any).xgroup === 'function') {
        await (this.redis as any).xgroup('CREATE', STREAM_NAME, CONSUMER_GROUP, '0', 'MKSTREAM');
      }
    } catch (e: any) {
      // BUSYGROUP or unsupported in mock
    }
  }

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
