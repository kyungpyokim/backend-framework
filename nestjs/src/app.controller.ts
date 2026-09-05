import { Controller, Get } from '@nestjs/common';
import { config } from './config/configuration';

@Controller()
export class AppController {
  @Get()
  root() {
    return {
      status: 'ok',
      app_name: 'NestJS Distributed Application',
      version: '0.1.0',
    };
  }

  @Get('health')
  health() {
    return {
      status: 'ok',
      app_name: 'NestJS Distributed Application',
      version: '0.1.0',
    };
  }

  @Get('cluster/info')
  clusterInfo() {
    return {
      node_id: config.nodeId,
      framework: 'NestJS (TypeScript)',
      port: config.port,
      redis_url: config.redisUrl,
    };
  }
}
