import { Injectable } from '@nestjs/common';

/**
 * 서킷 브레이커 상태 열거형.
 * - CLOSED: 정상 상태. 모든 요청 허용
 * - OPEN: 장애 상태. 요청 차단(Fast-fail) 및 Fallback 실행
 * - HALF_OPEN: 복구 시험 상태. 제한된 시험 호출로 복구 여부 확인
 */
export enum CircuitState {
  CLOSED = 'CLOSED',
  OPEN = 'OPEN',
  HALF_OPEN = 'HALF_OPEN',
}

/**
 * 서비스 간 장애 격리 및 연쇄 장애 차단을 위한 서킷 브레이커 서비스.
 */
@Injectable()
export class CircuitBreakerService {
  private state: CircuitState = CircuitState.CLOSED;
  private failureCount = 0;
  private failureThreshold = 3;
  private recoveryTimeoutMs = 4000;
  private lastStateChange = Date.now();

  /** 현재 서킷 상태를 확인하고 필요 시 HALF_OPEN으로 갱신 후 반환 */
  getState(): CircuitState {
    this.updateStateIfNeeded();
    return this.state;
  }

  /** OPEN 상태에서 쿨다운 타임아웃(recoveryTimeoutMs)이 경과했으면 HALF_OPEN으로 전이 */
  private updateStateIfNeeded() {
    if (this.state === CircuitState.OPEN) {
      const elapsed = Date.now() - this.lastStateChange;
      if (elapsed >= this.recoveryTimeoutMs) {
        this.state = CircuitState.HALF_OPEN;
        this.lastStateChange = Date.now();
      }
    }
  }

  /**
   * 서킷 브레이커 보호 하에 비동기 함수를 실행합니다.
   * - OPEN 상태인 경우 호출을 차단하고 fallback 실행 (fallback 없으면 예외 발생)
   * - 성공 시 성공 핸들러(onSuccess) 호출
   * - 실패 시 실패 핸들러(onFailure) 호출 및 fallback 실행
   */
  async call<T>(func: () => Promise<T>, fallback?: () => Promise<T>): Promise<T> {
    this.updateStateIfNeeded();

    if (this.state === CircuitState.OPEN) {
      if (fallback) {
        return fallback();
      }
      throw new Error('Circuit is OPEN (Fast-fail)');
    }

    try {
      const result = await func();
      this.onSuccess();
      return result;
    } catch (err) {
      this.onFailure();
      if (fallback) {
        return fallback();
      }
      throw err;
    }
  }

  /** 호출 성공 처리: HALF_OPEN 상태였다면 CLOSED로 완전 복구 */
  private onSuccess() {
    if (this.state === CircuitState.HALF_OPEN) {
      this.state = CircuitState.CLOSED;
      this.failureCount = 0;
      this.lastStateChange = Date.now();
    } else if (this.state === CircuitState.CLOSED) {
      this.failureCount = 0;
    }
  }

  /** 호출 실패 처리: 연속 실패 횟수 증가 및 임계치 도달 시 OPEN으로 전이 */
  private onFailure() {
    this.failureCount++;
    if (this.state === CircuitState.HALF_OPEN || this.failureCount >= this.failureThreshold) {
      this.state = CircuitState.OPEN;
      this.lastStateChange = Date.now();
    }
  }

  /** 서킷 브레이커 상태 및 메트릭 반환 */
  getStatus() {
    return {
      state: this.getState(),
      failure_count: this.failureCount,
      failure_threshold: this.failureThreshold,
      recovery_timeout_sec: this.recoveryTimeoutMs / 1000,
    };
  }
}
