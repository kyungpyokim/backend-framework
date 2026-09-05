import { Injectable } from '@nestjs/common';

export enum CircuitState {
  CLOSED = 'CLOSED',
  OPEN = 'OPEN',
  HALF_OPEN = 'HALF_OPEN',
}

@Injectable()
export class CircuitBreakerService {
  private state: CircuitState = CircuitState.CLOSED;
  private failureCount = 0;
  private failureThreshold = 3;
  private recoveryTimeoutMs = 4000;
  private lastStateChange = Date.now();

  getState(): CircuitState {
    this.updateStateIfNeeded();
    return this.state;
  }

  private updateStateIfNeeded() {
    if (this.state === CircuitState.OPEN) {
      const elapsed = Date.now() - this.lastStateChange;
      if (elapsed >= this.recoveryTimeoutMs) {
        this.state = CircuitState.HALF_OPEN;
        this.lastStateChange = Date.now();
      }
    }
  }

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

  private onSuccess() {
    if (this.state === CircuitState.HALF_OPEN) {
      this.state = CircuitState.CLOSED;
      this.failureCount = 0;
      this.lastStateChange = Date.now();
    } else if (this.state === CircuitState.CLOSED) {
      this.failureCount = 0;
    }
  }

  private onFailure() {
    this.failureCount++;
    if (this.state === CircuitState.HALF_OPEN || this.failureCount >= this.failureThreshold) {
      this.state = CircuitState.OPEN;
      this.lastStateChange = Date.now();
    }
  }

  getStatus() {
    return {
      state: this.getState(),
      failure_count: this.failureCount,
      failure_threshold: this.failureThreshold,
      recovery_timeout_sec: this.recoveryTimeoutMs / 1000,
    };
  }
}
