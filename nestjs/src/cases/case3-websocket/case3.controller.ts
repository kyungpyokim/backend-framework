import { Body, Controller, Post } from '@nestjs/common';
import { config } from '../../config/configuration';
import { WsPubsubGateway } from './ws-pubsub.manager';

/**
 * Case 3: 실시간 분산 브로드캐스트 컨트롤러.
 * - HTTP API 호출을 통해 특정 방(room)의 웹소켓 클라이언트들에게 메시지를 외부 브로드캐스트합니다.
 */
@Controller('case3')
export class Case3Controller {
  constructor(private readonly wsGateway: WsPubsubGateway) {}

  /**
   * [HTTP 기반 브로드캐스트]
   * 외부 시스템에서 HTTP POST로 메시지를 전달하면 Redis Pub/Sub을 통해 모든 서버의 웹소켓 클라이언트로 전송합니다.
   */
  @Post('broadcast')
  async broadcast(@Body() body: { room_id: string; sender: string; content: string }) {
    await this.wsGateway.publishToCluster(body.room_id, {
      type: 'broadcast',
      sender: body.sender,
      content: body.content,
      origin_node: config.nodeId,
    });

    return {
      status: 'published',
      room_id: body.room_id,
      origin_node: config.nodeId,
    };
  }
}
