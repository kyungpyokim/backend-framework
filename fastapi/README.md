# FastAPI Distributed Systems Playground

`uv` 기반 파이썬 가상환경과 FastAPI, Redis, Nginx, Docker Compose를 활용하여 분산 서버의 핵심 아키텍처 패턴을 직접 실습하고 학습할 수 있는 플레이그라운드 프로젝트입니다.

---

## 📚 4대 분산 아키텍처 케이스

### 1. Case 1: 무상태(Stateless) API 수평 확장 & 분산 락 / Rate Limit
- **위치**: `app/cases/case1_lock/`
- **핵심 개념**:
  - 다중 서버가 동일한 자원(재고, 쿠폰)에 동시 접근할 때의 **Race Condition** 해결
  - Redis 기반 **분산 락 (Distributed Lock)** (`SET resource token NX PX` + 안전한 Lua 해제)
  - 분산 환경에서의 **슬라이딩 윈도우 Rate Limiter** (Sorted Set 기반 정밀 제한)
- **엔드포인트**:
  - `POST /case1/inventory/init`: 재고 초기화
  - `POST /case1/purchase/safe`: 분산 락 적용 안전 구매
  - `POST /case1/purchase/unsafe`: 분산 락 미적용 (Race Condition 버그 재현)
  - `GET /case1/rate-limit`: 슬라이딩 윈도우 처리율 제한 테스트

### 2. Case 2: 이벤트 기반 비동기 작업 큐 & 분산 워커 (Producer-Consumer)
- **위치**: `app/cases/case2_queue/`
- **핵심 개념**:
  - 무거운 작업을 API 응답 루프에서 분리하여 즉시 `202 Accepted` 응답
  - **Redis Streams** (`XADD`, Consumer Group `XREADGROUP`, `XACK`) 기반 신뢰성 있는 작업 전달
  - 독립된 분산 워커 프로세스들이 작업을 병렬로 분산 처리
- **엔드포인트 및 모듈**:
  - `POST /case2/jobs`: 작업 비동기 등록
  - `GET /case2/jobs/{job_id}`: 작업 상태 및 결과 폴링
  - `python -m app.cases.case2_queue.worker`: 분산 워커 프로세스

### 3. Case 3: 실시간 다중 서버 브로드캐스트 (WebSocket + Redis Pub/Sub)
- **위치**: `app/cases/case3_websocket/`
- **핵심 개념**:
  - 서로 다른 서버 인스턴스에 연결된 WebSocket 클라이언트 간의 메시지 동기화
  - **Redis Pub/Sub** 채널을 통한 크로스 노드 실시간 브로드캐스트
- **엔드포인트**:
  - `WS /case3/ws/chat/{room_id}`: 웹소켓 실시간 채팅
  - `POST /case3/broadcast`: 외부/HTTP를 통한 전 서버 실시간 푸시

### 4. Case 4: 서비스 간 통신 장애 격리 (Circuit Breaker & Fallback)
- **위치**: `app/cases/case4_resilience/`
- **핵심 개념**:
  - 외부 의존 서비스(결제 PG사 등) 장애 시 연쇄 다운(Cascading Failure) 방지
  - 서킷 브레이커 상태 머신 (`CLOSED` -> `OPEN` -> `HALF_OPEN`)
  - 장애 발생 시 빠른 실패(Fast-Fail) 및 안전한 Fallback 응답 반환
- **엔드포인트**:
  - `GET /case4/call`: 서킷 브레이커 보호를 통한 외부 호출
  - `POST /case4/external/fault`: 외부 서비스 장애 여부 토글 (학습용)
  - `GET /case4/circuit-status`: 현재 서킷 상태 모니터링

---

## 🚀 빠른 시작 가이드

### Option A: 로컬에서 즉시 데모 실행 (Docker 불필요)

외부 인프라(Redis) 없이도 `fakeredis`를 통해 4대 분산 패턴의 실행 결과를 터미널에서 즉시 확인할 수 있습니다:

```bash
cd fastapi
uv sync
uv run python scripts/demo_distributed_cases.py
```

### Option B: 전체 테스트 실행

```bash
cd fastapi
uv run pytest -v
```

### Option C: Docker Compose로 실제 분산 클러스터 띄우기

Nginx(로드밸런서) + API 인스턴스 2대 + Redis + 분산 워커 2대가 하나의 클러스터로 구동됩니다:

```bash
cd fastapi
docker compose up --build
```

- **통합 엔트리포인트 (Nginx Load Balancer)**: `http://localhost:8080`
- **API Node #1 직접 접근**: `http://localhost:8001/docs`
- **API Node #2 직접 접근**: `http://localhost:8002/docs`
- **클러스터 노드 확인**: `http://localhost:8080/cluster/info` (요청마다 node-1, node-2로 번갈아 라우팅됨)

