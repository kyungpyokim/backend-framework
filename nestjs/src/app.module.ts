import { Module } from '@nestjs/common';
import { AppController } from './app.controller';
import { RedisModule } from './core/redis/redis.module';
import { Case1Module } from './cases/case1-lock/case1.module';
import { Case2Module } from './cases/case2-queue/case2.module';
import { Case3Module } from './cases/case3-websocket/case3.module';
import { Case4Module } from './cases/case4-resilience/case4.module';

@Module({
  imports: [
    RedisModule,
    Case1Module,
    Case2Module,
    Case3Module,
    Case4Module,
  ],
  controllers: [AppController],
})
export class AppModule {}
