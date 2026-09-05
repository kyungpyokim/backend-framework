import { Body, Controller, Get, HttpException, HttpStatus, Param, Post, Query } from '@nestjs/common';
import { config } from '../../config/configuration';
import { LockService } from './lock.service';
import { RateLimiterService } from './rate-limiter.service';

@Controller('case1')
export class Case1Controller {
  constructor(
    private readonly lockService: LockService,
    private readonly rateLimiterService: RateLimiterService,
  ) {}

  @Post('inventory/init')
  async setupInventory(@Body() body: { item_id: string; stock: number }) {
    await this.lockService.initInventory(body.item_id, body.stock);
    return {
      node_id: config.nodeId,
      item_id: body.item_id,
      stock: body.stock,
      message: 'Inventory initialized',
    };
  }

  @Get('inventory/:itemId')
  async fetchInventory(@Param('itemId') itemId: string) {
    const stock = await this.lockService.getInventory(itemId);
    return {
      node_id: config.nodeId,
      item_id: itemId,
      stock,
    };
  }

  @Post('purchase/safe')
  async buySafe(@Body() body: { item_id: string; quantity?: number }) {
    try {
      const res = await this.lockService.purchaseWithLock(body.item_id, body.quantity || 1);
      return {
        node_id: config.nodeId,
        success: res.success,
        message: res.message,
        remaining_stock: res.remaining,
      };
    } catch (e: any) {
      throw new HttpException('Server busy, please retry in a moment', HttpStatus.TOO_MANY_REQUESTS);
    }
  }

  @Post('purchase/unsafe')
  async buyUnsafe(@Body() body: { item_id: string; quantity?: number }) {
    const res = await this.lockService.purchaseWithoutLock(body.item_id, body.quantity || 1);
    return {
      node_id: config.nodeId,
      success: res.success,
      message: res.message,
      remaining_stock: res.remaining,
    };
  }

  @Get('rate-limit')
  async testRateLimit(@Query('client_id') clientId = 'client-default') {
    const { allowed, remaining } = await this.rateLimiterService.checkLimit(clientId, 5, 10);
    if (!allowed) {
      throw new HttpException(`Rate limit exceeded. Remaining: ${remaining}`, HttpStatus.TOO_MANY_REQUESTS);
    }
    return {
      node_id: config.nodeId,
      client_id: clientId,
      allowed,
      remaining_requests: remaining,
    };
  }
}
