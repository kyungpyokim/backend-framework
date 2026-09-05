import { Body, Controller, Get, HttpException, HttpStatus, Param, Post } from '@nestjs/common';
import { config } from '../../config/configuration';
import { QueueService } from './queue.service';

@Controller('case2')
export class Case2Controller {
  constructor(private readonly queueService: QueueService) {}

  @Post('jobs')
  async submitJob(@Body() body: { task_type?: string; payload?: any }) {
    const taskType = body.task_type || 'heavy_computation';
    const payload = body.payload || { duration_sec: 1 };
    const jobId = await this.queueService.enqueueJob(taskType, payload);

    return {
      node_id: config.nodeId,
      job_id: jobId,
      status: 'PENDING',
      message: 'Job accepted and enqueued. Poll GET /case2/jobs/:jobId for status.',
    };
  }

  @Get('jobs/:jobId')
  async checkJob(@Param('jobId') jobId: string) {
    const job = await this.queueService.getJob(jobId);
    if (!job) {
      throw new HttpException('Job not found', HttpStatus.NOT_FOUND);
    }
    return {
      node_id: config.nodeId,
      job_id: job.job_id,
      status: job.status,
      task_type: job.task_type,
      payload: job.payload,
      result: job.result,
      worker_id: job.worker_id,
    };
  }
}
