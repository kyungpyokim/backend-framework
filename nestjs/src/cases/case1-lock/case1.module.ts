import { Module } from '@nestjs/common';
import { Case1Controller } from './case1.controller';
import { LockService } from './lock.service';
import { RateLimiterService } from './rate-limiter.service';

@Module({
  controllers: [Case1Controller],
  providers: [LockService, RateLimiterService],
  exports: [LockService, RateLimiterService],
})
export class Case1Module {}
