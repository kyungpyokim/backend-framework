import { Module } from '@nestjs/common';
import { Case4Controller } from './case4.controller';
import { CircuitBreakerService } from './circuit-breaker.service';

@Module({
  controllers: [Case4Controller],
  providers: [CircuitBreakerService],
  exports: [CircuitBreakerService],
})
export class Case4Module {}
