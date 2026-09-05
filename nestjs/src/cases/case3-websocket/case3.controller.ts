import { Body, Controller, Post } from '@nestjs/common';
import { config } from '../../config/configuration';
import { WsPubsubGateway } from './ws-pubsub.manager';

@Controller('case3')
export class Case3Controller {
  constructor(private readonly wsGateway: WsPubsubGateway) {}

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
