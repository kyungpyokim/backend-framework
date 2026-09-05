import { Body, Controller, Get, HttpException, HttpStatus, Param, Post, Query } from '@nestjs/common';
import { config } from '../../config/configuration';
import { LockService } from './lock.service';
import { RateLimiterService } from './rate-limiter.service';

/**
 * Case 1: 무상태 수평 확장 및 분산 락 컨트롤러.
 * - 재고 초기화 및 조회
 * - 분산 락 적용 안전 구매 (/purchase/safe)
 * - 락 미적용 취약 구매 (/purchase/unsafe)
 * - 슬라이딩 윈도우 처리율 제한 (/rate-limit)
 */
@Controller('case1')
export class Case1Controller {
  constructor(
    private readonly lockService: LockService,
    private readonly rateLimiterService: RateLimiterService,
  ) {}

  /** 상품 재고 수량 초기화 */
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

  /** 현재 상품 재고 수량 조회 */
  @Get('inventory/:itemId')
  async fetchInventory(@Param('itemId') itemId: string) {
    const stock = await this.lockService.getInventory(itemId);
    return {
      node_id: config.nodeId,
      item_id: itemId,
      stock,
    };
  }

  /**
   * [동시성 안전] Redis 분산 락을 획득하여 재고를 차감합니다.
   * 락 획득 타임아웃 시 429 Too Many Requests 반환.
   */
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

  /**
   * [동시성 취약] 분산 락 없이 재고를 차감하여 Race Condition 발생을 시연합니다.
   */
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

  /**
   * [처리율 제한] 슬라이딩 윈도우 알고리즘 테스트 (10초 내 최대 5회).
   */
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
