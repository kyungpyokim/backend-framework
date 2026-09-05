import { Injectable, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { QueueService, STREAM_NAME, CONSUMER_GROUP } from './queue.service';
import { RedisService } from '../../core/redis/redis.service';
import { config } from '../../config/configuration';

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

  onModuleInit() {
    // Background worker loop can be started if configured
    if (process.env.RUN_WORKER === 'true') {
      this.isRunning = true;
      this.startLoop();
    }
  }

  onModuleDestroy() {
    this.isRunning = false;
  }

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

  private async startLoop() {
    console.log(`[${this.workerId}] Worker started listening on Redis Stream...`);
    while (this.isRunning) {
      await this.processOneJob(2000);
    }
  }
}
