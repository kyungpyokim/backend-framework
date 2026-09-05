import { Body, Controller, Get, Post } from '@nestjs/common';
import { config } from '../../config/configuration';
import { CircuitBreakerService } from './circuit-breaker.service';

@Controller('case4')
export class Case4Controller {
  private isHealthy = true;

  constructor(private readonly cbService: CircuitBreakerService) {}

  @Post('external/fault')
  setFault(@Body() body: { is_healthy: boolean }) {
    this.isHealthy = body.is_healthy;
    return {
      message: `External service healthy status set to: ${this.isHealthy}`,
      is_healthy: this.isHealthy,
    };
  }

  @Get('call')
  async callExternal() {
    const mockExternalPayment = async () => {
      await new Promise((r) => setTimeout(r, 20));
      if (!this.isHealthy) {
        throw new Error('External Payment Gateway is unreachable (503 Service Unavailable)');
      }
      return { status: 'SUCCESS', tx_id: 'tx_mock_9999', amount: 1000 };
    };

    const fallbackHandler = async () => {
      return {
        status: 'FALLBACK',
        message: 'Payment system temporarily unavailable. Queued for offline processing.',
        fallback_used: true,
      };
    };

    const result = await this.cbService.call<any>(mockExternalPayment, fallbackHandler);

    return {
      node_id: config.nodeId,
      circuit_state: this.cbService.getState(),
      result,
    };
  }

  @Get('circuit-status')
  getCircuitStatus() {
    return {
      ...this.cbService.getStatus(),
      node_id: config.nodeId,
      external_service_healthy: this.isHealthy,
    };
  }
}
