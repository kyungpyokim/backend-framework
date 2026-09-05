# 04. 3대 프레임워크 분산 아키텍처 비교 분석 (FastAPI vs NestJS vs Spring Boot)

본 문서는 동일한 4대 분산 아키텍처 패턴을 **FastAPI(Python)**, **NestJS(TypeScript)**, **Spring Boot(Java 21)**로 각각 구현했을 때의 아키텍처 차이점, 런타임 특성 및 코드 비교를 정리합니다.

---

## 1. 3대 프레임워크 종합 비교표

| 비교 항목 | FastAPI (`fastapi/`) | NestJS (`nestjs/`) | Spring Boot (`spring-boot/`) |
|---|---|---|---|
| **언어 및 런타임** | Python 3.12 (`uv`) | Node.js 22 (TypeScript) | Java 21 (Gradle 8) |
| **I/O 및 동시성 모델** | 단일 스레드 비동기 (`async/await`, uvloop) | 단일 스레드 이벤트 루프 (libuv) | 멀티스레드 풀 (Tomcat / NIO / Virtual Threads) |
| **Redis 드라이버** | `redis.asyncio` | `ioredis` | `Spring Data Redis` (Lettuce) |
| **Case 1: 분산 락** | `SET NX PX` + Lua 스크립트 | `ioredis.eval` Lua 스크립트 | `StringRedisTemplate.execute` Lua 스크립트 |
| **Case 1: Rate Limit** | Redis Sorted Set 슬라이딩 윈도우 | Redis Sorted Set 슬라이딩 윈도우 | Redis Sorted Set 슬라이딩 윈도우 |
| **Case 2: 작업 큐** | Redis Streams (`XADD`, `XREADGROUP`) | Redis Streams (`xadd`, `xreadgroup`) | Redis Streams (`StreamRecords`, `opsForStream`) |
| **Case 3: WebSocket** | FastAPI 내장 WebSocket + Redis Pub/Sub | `@nestjs/platform-ws` + Redis Pub/Sub | Spring `TextWebSocketHandler` + Redis Pub/Sub |
| **Case 4: 서킷 브레이커** | 자체 경량 상태 머신 (`asyncio.Lock`) | 자체 TS 클래스 상태 머신 | 자체 상태 머신 (`synchronized`) / Resilience4j 호환 |
| **클러스터 포트 (Nginx)** | `http://localhost:8080` | `http://localhost:8081` | `http://localhost:8082` |
| **단위/통합 테스트** | `pytest` + `fakeredis` (0.75s) | `jest` + `ioredis-mock` (1.13s) | `JUnit 5` + Mockito (2.0s) |

---

## 2. 핵심 분산 패턴 코드 1:1 비교

### 2.1 분산 락 해제 (Safe Release Lua Script)

- **FastAPI**:
  ```python
  RELEASE_LOCK_LUA = """
  if redis.call("get", KEYS[1]) == ARGV[1] then
      return redis.call("del", KEYS[1])
  else
      return 0
  end
  """
  await redis.eval(RELEASE_LOCK_LUA, 1, lock_key, token)
  ```
- **NestJS**:
  ```typescript
  const RELEASE_LOCK_LUA = `
  if redis.call("get", KEYS[1]) == ARGV[1] then
      return redis.call("del", KEYS[1])
  else
      return 0
  end
  `;
  await this.redis.eval(RELEASE_LOCK_LUA, 1, lockKey, token);
  ```
- **Spring Boot**:
  ```java
  private static final String RELEASE_LOCK_LUA =
      "if redis.call('get', KEYS[1]) == ARGV[1] then " +
      "    return redis.call('del', KEYS[1]) " +
      "else " +
      "    return 0 " +
      "end";
  redisTemplate.execute(releaseScript, Collections.singletonList(lockKey), token);
  ```

---

## 3. 프레임워크 선택 가이드 및 트레이드오프

### 1) FastAPI
- **강점**: 가볍고 빠른 개발 속도, AI/ML 및 데이터 파이프라인 연동에 압도적, Pydantic을 통한 자동 스키마 검증.
- **적합 대상**: AI 서빙, 데이터 집약적 마이크로서비스, 가벼운 고성능 API.

### 2) NestJS
- **강점**: Angular 스타일의 강력한 모듈화 및 DI 구조, 프론트엔드(TypeScript/React)와의 타입 공유, 풍부한 엔터프라이즈 데코레이터.
- **적합 대상**: 풀스택 TypeScript 팀, 구조화된 아키텍처가 필요한 웹 서비스.

### 3) Spring Boot
- **강점**: 엔터프라이즈 환경에서의 견고한 생태계, 대규모 트래픽 처리, 안정적인 트랜잭션 관리와 보안(Spring Security), 풍부한 모니터링(Actuator, Micrometer).
- **적합 대상**: 금융, 결제, 대규모 커머스, 엄격한 일관성과 장기적 유지보수가 요구되는 시스템.
