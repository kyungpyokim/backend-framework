# NestJS Distributed Systems Playground

NestJS (TypeScript), Redis, Nginx, Docker Compose를 활용하여 분산 서버의 핵심 아키텍처 패턴을 실습하는 프로젝트입니다.

---

## 📚 4대 분산 아키텍처 케이스

1. **Case 1: Stateless API 수평 확장 & 분산 락 / Rate Limit** (`src/cases/case1-lock/`)
   - `POST /case1/inventory/init`: 재고 세팅
   - `POST /case1/purchase/safe`: 분산 락 적용 안전 구매
   - `POST /case1/purchase/unsafe`: 분산 락 미적용 (Race condition 재현)
   - `GET /case1/rate-limit`: 슬라이딩 윈도우 Rate Limit
2. **Case 2: 이벤트 기반 비동기 작업 큐 & 분산 워커** (`src/cases/case2-queue/`)
   - `POST /case2/jobs`: 작업 비동기 등록
   - `GET /case2/jobs/:jobId`: 작업 상태 폴링
3. **Case 3: 실시간 다중 서버 브로드캐스트** (`src/cases/case3-websocket/`)
   - `WS /case3/ws/chat?room={roomId}`: 웹소켓 채팅
   - `POST /case3/broadcast`: 전 서버 브로드캐스트
4. **Case 4: 서비스 간 통신 장애 격리 (Circuit Breaker)** (`src/cases/case4-resilience/`)
   - `GET /case4/call`: 서킷 브레이커 보호 호출
   - `POST /case4/external/fault`: 외부 장애 토글
   - `GET /case4/circuit-status`: 상태 확인

---

## 🚀 실행 가이드

### 1) 터미널 즉시 데모 실행 (Docker 불필요)
```bash
npm run demo
```

### 2) 테스트 실행
```bash
npm test
```

### 3) Docker Compose 클러스터 구동
```bash
docker compose up --build
```
- Nginx 로드밸런서: `http://localhost:8081`
