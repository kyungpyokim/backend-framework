import { Body, Controller, Get, HttpException, HttpStatus, Param, Post } from '@nestjs/common';
import { config } from '../../config/configuration';
import { QueueService } from './queue.service';

/**
 * Case 2: 비동기 작업 큐 및 워커 컨트롤러.
 * - 신규 비동기 작업 제출 (인큐)
 * - 작업 진행 상태 및 결과 비동기 폴링 조회
 */
@Controller('case2')
export class Case2Controller {
  constructor(private readonly queueService: QueueService) {}

  /**
   * [작업 인큐] 새로운 비동기 백그라운드 작업을 등록하고 작업 ID를 발급합니다.
   */
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

  /**
   * [작업 상태 조회] 작업의 현재 상태(PENDING/PROCESSING/COMPLETED/FAILED) 및 결과를 조회합니다.
   */
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
