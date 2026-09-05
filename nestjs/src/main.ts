import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { WsAdapter } from '@nestjs/platform-ws';
import { AppModule } from './app.module';
import { config } from './config/configuration';

/**
 * NestJS 애플리케이션 엔트리포인트.
 * - 모듈 부트스트랩
 * - CORS 허용
 * - WsAdapter 등록 (표준 WebSocket 프로토콜 지원)
 * - 설정된 포트(3000 등)로 서버 리스닝
 */
async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  app.enableCors();
  // Case 3 웹소켓 실시간 분산 브로드캐스트를 위한 표준 WebSocket 어댑터 설정
  app.useWebSocketAdapter(new WsAdapter(app));

  await app.listen(config.port, '0.0.0.0');
  console.log(`[${config.nodeId}] NestJS server is running on port ${config.port}`);
}

bootstrap();
