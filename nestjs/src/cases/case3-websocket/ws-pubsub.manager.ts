import { Injectable, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { WebSocketGateway, WebSocketServer, OnGatewayConnection, OnGatewayDisconnect, SubscribeMessage } from '@nestjs/websockets';
import { Server, WebSocket } from 'ws';
import Redis from 'ioredis';
import { config } from '../../config/configuration';

import { RedisService } from '../../core/redis/redis.service';

// Redis Pub/Sub 방 채널 네임스페이스
const CHANNEL_PREFIX = 'ws:room:';

/**
 * NestJS 웹소켓 게이트웨이 및 분산 Pub/Sub 매니저.
 * - 클러스터 환경에서 개별 서버는 자신이 수립한 로컬 웹소켓 연결(localRooms)만 보유합니다.
 * - 특정 방(room)으로 전송되는 모든 메시지는 Redis 채널(`ws:room:*`)로 발행(PUBLISH)하여
 *   모든 NestJS 노드가 수신하고, 각 노드는 자신의 로컬 연결 세션에 브로드캐스트합니다.
 */
@Injectable()
@WebSocketGateway({ path: '/case3/ws/chat' })
export class WsPubsubGateway implements OnGatewayConnection, OnGatewayDisconnect, OnModuleInit, OnModuleDestroy {
  @WebSocketServer()
  server: Server;

  // 방 ID(roomId) -> 현재 노드에 연결된 활성 WebSocket 세션 집합
  private localRooms = new Map<string, Set<WebSocket>>();
  // Pub/Sub 패턴 구독 전용 Redis 클라이언트 (구독 모드에서는 일반 커맨드 불가하므로 별도 연결 사용)
  private subRedis: Redis | null = null;

  constructor(private readonly redisService: RedisService) {}

  /** 모듈 시작 시 Redis Pub/Sub 패턴 구독 클라이언트 초기화 */
  onModuleInit() {
    this.initPubSub().catch((e) => {
      console.warn(`[${config.nodeId}] WS Redis PubSub init skipped: ${e.message}`);
    });
  }

  /** 모듈 소멸 시 Pub/Sub Redis 연결 해제 */
  async onModuleDestroy() {
    if (this.subRedis) {
      try {
        await this.subRedis.quit();
      } catch {
        this.subRedis.disconnect();
      }
    }
  }

  /**
   * Redis Pub/Sub 채널 패턴(`ws:room:*`)을 구독하고,
   * 메시지 수신 시 해당 방의 로컬 웹소켓 세션들로 브로드캐스트합니다.
   */
  private async initPubSub() {
    try {
      this.subRedis = new Redis(config.redisUrl, { lazyConnect: true, maxRetriesPerRequest: 1 });
      await this.subRedis.connect();
      await this.subRedis.psubscribe(`${CHANNEL_PREFIX}*`);
      this.subRedis.on('pmessage', (pattern, channel, message) => {
        const roomId = channel.replace(CHANNEL_PREFIX, '');
        try {
          const data = JSON.parse(message);
          this.broadcastLocally(roomId, data);
        } catch {
          // invalid json
        }
      });
    } catch (e: any) {
      this.subRedis = null;
    }
  }

  /**
   * 클라이언트 웹소켓 연결 수립 시 호출.
   * 쿼리 파라미터(?room=xxx)로부터 방 ID를 추출하고 로컬 방 세션에 추가합니다.
   */
  handleConnection(client: WebSocket, req: any) {
    // URL format: /case3/ws/chat?room=room1
    const url = new URL(req.url || '', `http://${req.headers.host}`);
    const roomId = url.searchParams.get('room') || 'default';

    (client as any).roomId = roomId;
    if (!this.localRooms.has(roomId)) {
      this.localRooms.set(roomId, new Set());
    }
    this.localRooms.get(roomId)!.add(client);

    // 접속 알림을 클러스터 전체에 브로드캐스트
    this.publishToCluster(roomId, {
      type: 'system',
      message: `New user connected to room '${roomId}' via node [${config.nodeId}]`,
      node_id: config.nodeId,
    });
  }

  /** 클라이언트 웹소켓 연결 종료 시 호출. 로컬 방 세션에서 제거 */
  handleDisconnect(client: WebSocket) {
    const roomId = (client as any).roomId;
    if (roomId && this.localRooms.has(roomId)) {
      this.localRooms.get(roomId)!.delete(client);
      this.publishToCluster(roomId, {
        type: 'system',
        message: `User disconnected from room '${roomId}' on node [${config.nodeId}]`,
        node_id: config.nodeId,
      });
    }
  }

  /** 클라이언트로부터 채팅 메시지 수신 시 클러스터 전체로 발행 */
  @SubscribeMessage('chat')
  handleChatMessage(client: WebSocket, payload: string) {
    const roomId = (client as any).roomId || 'default';
    this.publishToCluster(roomId, {
      type: 'chat',
      content: payload,
      from_node: config.nodeId,
    });
  }

  /** Redis 채널로 메시지를 발행(PUBLISH)하여 모든 클러스터 인스턴스로 전파 */
  async publishToCluster(roomId: string, message: any) {
    try {
      await this.redisService.getClient().publish(`${CHANNEL_PREFIX}${roomId}`, JSON.stringify(message));
    } catch {
      // publish failed or mock
    }
  }

  /** 현재 노드에 연결된 지정 방의 모든 활성 웹소켓 클라이언트에게 로컬 전송 */
  broadcastLocally(roomId: string, message: any) {
    const clients = this.localRooms.get(roomId);
    if (!clients) return;
    const msgStr = JSON.stringify(message);
    for (const ws of clients) {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(msgStr);
      }
    }
  }
}
