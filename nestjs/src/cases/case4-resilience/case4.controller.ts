import { Body, Controller, Get, Post } from '@nestjs/common';
import { config } from '../../config/configuration';
import { CircuitBreakerService } from './circuit-breaker.service';

/**
 * Case 4: 서비스 간 회복 탄력성(Resilience) 및 서킷 브레이커 컨트롤러.
 * - 모의 외부 결제 시스템 장애 주입
 * - 서킷 브레이커 보호 호출 (/call)
 * - 서킷 상태 및 메트릭 모니터링 (/circuit-status)
 */
@Controller('case4')
export class Case4Controller {
  // 모의 외부 서비스의 정상/장애 상태 플래그
  private isHealthy = true;

  constructor(private readonly cbService: CircuitBreakerService) {}

  /** [장애 주입] 모의 외부 서비스의 상태(정상/고장)를 인위적으로 전환 */
  @Post('external/fault')
  setFault(@Body() body: { is_healthy: boolean }) {
    this.isHealthy = body.is_healthy;
    return {
      message: `External service healthy status set to: ${this.isHealthy}`,
      is_healthy: this.isHealthy,
    };
  }

  /**
   * [서킷 보호 호출] 서킷 브레이커를 경유하여 외부 결제 서비스를 호출합니다.
   * 외부 서비스 장애 시 Fallback 응답을 반환합니다.
   */
  @Get('call')
  async callExternal() {
    // 외부 결제 게이트웨이 시뮬레이션
    const mockExternalPayment = async () => {
      await new Promise((r) => setTimeout(r, 20));
      if (!this.isHealthy) {
        throw new Error('External Payment Gateway is unreachable (503 Service Unavailable)');
      }
      return { status: 'SUCCESS', tx_id: 'tx_mock_9999', amount: 1000 };
    };

    // 장애 시 실행할 안전한 Fallback 핸들러
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

  /** [상태 모니터링] 현재 서킷 브레이커 상태 및 외부 서비스 정상 여부 조회 */
  @Get('circuit-status')
  getCircuitStatus() {
    return {
      ...this.cbService.getStatus(),
      node_id: config.nodeId,
      external_service_healthy: this.isHealthy,
    };
  }
}
