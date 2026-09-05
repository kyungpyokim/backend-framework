import { Injectable, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { WebSocketGateway, WebSocketServer, OnGatewayConnection, OnGatewayDisconnect, SubscribeMessage } from '@nestjs/websockets';
import { Server, WebSocket } from 'ws';
import Redis from 'ioredis';
import { config } from '../../config/configuration';

import { RedisService } from '../../core/redis/redis.service';

const CHANNEL_PREFIX = 'ws:room:';

@Injectable()
@WebSocketGateway({ path: '/case3/ws/chat' })
export class WsPubsubGateway implements OnGatewayConnection, OnGatewayDisconnect, OnModuleInit, OnModuleDestroy {
  @WebSocketServer()
  server: Server;

  private localRooms = new Map<string, Set<WebSocket>>();
  private subRedis: Redis | null = null;

  constructor(private readonly redisService: RedisService) {}

  onModuleInit() {
    this.initPubSub().catch((e) => {
      console.warn(`[${config.nodeId}] WS Redis PubSub init skipped: ${e.message}`);
    });
  }

  async onModuleDestroy() {
    if (this.subRedis) {
      try {
        await this.subRedis.quit();
      } catch {
        this.subRedis.disconnect();
      }
    }
  }

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

  handleConnection(client: WebSocket, req: any) {
    // URL format: /case3/ws/chat?room=room1
    const url = new URL(req.url || '', `http://${req.headers.host}`);
    const roomId = url.searchParams.get('room') || 'default';

    (client as any).roomId = roomId;
    if (!this.localRooms.has(roomId)) {
      this.localRooms.set(roomId, new Set());
    }
    this.localRooms.get(roomId)!.add(client);

    this.publishToCluster(roomId, {
      type: 'system',
      message: `New user connected to room '${roomId}' via node [${config.nodeId}]`,
      node_id: config.nodeId,
    });
  }

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

  @SubscribeMessage('chat')
  handleChatMessage(client: WebSocket, payload: string) {
    const roomId = (client as any).roomId || 'default';
    this.publishToCluster(roomId, {
      type: 'chat',
      content: payload,
      from_node: config.nodeId,
    });
  }

  async publishToCluster(roomId: string, message: any) {
    try {
      await this.redisService.getClient().publish(`${CHANNEL_PREFIX}${roomId}`, JSON.stringify(message));
    } catch {
      // publish failed or mock
    }
  }

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
