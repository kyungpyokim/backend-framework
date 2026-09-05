import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { WsAdapter } from '@nestjs/platform-ws';
import { AppModule } from './app.module';
import { config } from './config/configuration';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  app.enableCors();
  app.useWebSocketAdapter(new WsAdapter(app));

  await app.listen(config.port, '0.0.0.0');
  console.log(`[${config.nodeId}] NestJS server is running on port ${config.port}`);
}

bootstrap();
