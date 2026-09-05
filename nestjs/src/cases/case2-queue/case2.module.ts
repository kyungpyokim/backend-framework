import { Module } from '@nestjs/common';
import { Case2Controller } from './case2.controller';
import { QueueService } from './queue.service';
import { WorkerService } from './worker.service';

@Module({
  controllers: [Case2Controller],
  providers: [QueueService, WorkerService],
  exports: [QueueService, WorkerService],
})
export class Case2Module {}
