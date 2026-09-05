import { Module } from '@nestjs/common';
import { Case3Controller } from './case3.controller';
import { WsPubsubGateway } from './ws-pubsub.manager';

@Module({
  controllers: [Case3Controller],
  providers: [WsPubsubGateway],
  exports: [WsPubsubGateway],
})
export class Case3Module {}
