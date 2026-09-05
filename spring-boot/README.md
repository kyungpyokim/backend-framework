# Spring Boot Distributed Systems Playground

Spring Boot 3.3 (Java 21), Spring Data Redis, Nginx, Docker Compose를 활용하여 분산 서버의 핵심 아키텍처 패턴을 실습하는 프로젝트입니다.

---

## 📚 4대 분산 아키텍처 케이스

1. **Case 1: Stateless API 수평 확장 & 분산 락 / Rate Limit** (`case1/`)
   - `POST /case1/inventory/init`: 재고 초기화
   - `POST /case1/purchase/safe`: Redis Lua 기반 분산 락 구매
   - `POST /case1/purchase/unsafe`: Race condition 발생 시연
   - `GET /case1/rate-limit`: 슬라이딩 윈도우 Rate Limit
2. **Case 2: 이벤트 기반 비동기 작업 큐 & 분산 워커** (`case2/`)
   - `POST /case2/jobs`: 작업 비동기 등록 (`202 Accepted`)
   - `GET /case2/jobs/{jobId}`: 작업 상태 폴링
3. **Case 3: 실시간 다중 서버 브로드캐스트** (`case3/`)
   - `WS /case3/ws/chat?room={roomId}`: Spring WebSocket 채팅
   - `POST /case3/broadcast`: 전 서버 브로드캐스트
4. **Case 4: 서비스 간 통신 장애 격리 (Circuit Breaker)** (`case4/`)
   - `GET /case4/call`: 서킷 브레이커 보호 호출
   - `POST /case4/external/fault`: 외부 장애 토글
   - `GET /case4/circuit-status`: 상태 확인

---

## 🚀 실행 가이드

### 1) 빌드 및 테스트
```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test
```

### 2) 로컬 서버 실행
```bash
./gradlew bootRun
```

### 3) Docker Compose 클러스터 구동
```bash
docker compose up --build
```
- Nginx 로드밸런서: `http://localhost:8082`
- API Node #1: `http://localhost:8083`
- API Node #2: `http://localhost:8084`
