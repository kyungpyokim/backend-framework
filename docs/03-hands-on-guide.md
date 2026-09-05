# 03. 단계별 실습 가이드 (Hands-on Guide)

본 문서는 플레이그라운드에서 분산 서버의 각 기능을 직접 호출하고 관찰하는 실습 절차를 안내합니다.

---

## 🛠️ 실습 환경 준비

### 요구사항
- Python 3.12+ 및 `uv` (설치 완료됨)
- Docker & Docker Compose (클러스터 실습 시 필요)

---

## 🧪 실습 1: 로컬 터미널 1초 데모 (Docker 불필요)

외부 인프라(Redis)를 실행하지 않고도, 인메모리 `fakeredis`를 통해 4대 케이스의 동작 원리를 즉시 확인할 수 있습니다:

```bash
cd fastapi
uv run python scripts/demo_distributed_cases.py
```

### 관찰 포인트
1. **Case 1**: 분산 락 없을 때 30개 동시 요청 중 30개가 전부 성공 처리되고 재고가 9개나 남아있는 Race condition 버그 발생 확인 vs 분산 락 적용 시 정확히 10건만 성공하고 20건 차단되는 모습 비교.
2. **Case 2**: API 서버가 3개의 작업을 발행(PENDING)하고, 분산 워커들이 순차적으로 가져가 COMPLETED로 처리하는 흐름 확인.
3. **Case 4**: 3회 실패 후 서킷이 `OPEN`되어 즉시 Fallback을 반환하고, 2초 후 `HALF_OPEN`을 거쳐 자동 회복되는 상태 전이 확인.

---

## 🐳 실습 2: Docker Compose 기반 실제 분산 클러스터 실습

### 2.1 클러스터 구동
```bash
cd fastapi
docker compose up --build
```

- 모든 컨테이너가 정상 구동되면 아래와 같이 포트가 오픈됩니다:
  - Nginx 로드밸런서: `http://localhost:8080`
  - API Node #1: `http://localhost:8001`
  - API Node #2: `http://localhost:8002`
  - Redis: `localhost:6379`

### 2.2 로드밸런싱 동작 확인 (라운드로빈)
터미널을 새로 열고 로드밸런서 엔드포인트를 연속 호출합니다:

```bash
curl http://localhost:8080/cluster/info
# 응답: {"node_id": "node-1", ...}

curl http://localhost:8080/cluster/info
# 응답: {"node_id": "node-2", ...}
```
> [!NOTE]
> Nginx가 요청을 `node-1`과 `node-2`로 번갈아 분산 라우팅하는 것을 확인할 수 있습니다.

---

### 2.3 [Case 1 실습] 분산 락 & 레이트 리미트

#### 1) 재고 10개 초기화
```bash
curl -X POST http://localhost:8080/case1/inventory/init \
  -H "Content-Type: application/json" \
  -d '{"item_id": "concert-ticket", "stock": 10}'
```

#### 2) 분산 락 안전 구매 호출
```bash
curl -X POST http://localhost:8080/case1/purchase/safe \
  -H "Content-Type: application/json" \
  -d '{"item_id": "concert-ticket", "quantity": 1}'
```
- 어느 노드로 요청이 가든 Redis의 `lock:inventory:concert-ticket`을 원자적으로 획득하여 정확한 재고가 차감됩니다.

#### 3) 분산 슬라이딩 윈도우 Rate Limit 확인
```bash
# 10초 내에 5회 초과 요청 시 429 Too Many Requests 발생
for i in {1..7}; do
  curl -s -o /dev/null -w "요청 #$i: HTTP 상태코드 %{http_code}\n" "http://localhost:8080/case1/rate-limit?client_id=tester"
done
```
- 1~5회는 `200`, 6회부터는 `429` 에러가 반환됩니다.

---

### 2.4 [Case 2 실습] 비동기 작업 큐 & 분산 워커

#### 1) 무거운 작업 발행 (Producer)
```bash
curl -X POST http://localhost:8080/case2/jobs \
  -H "Content-Type: application/json" \
  -d '{"task_type": "ai_video_render", "payload": {"duration_sec": 3}}'
```
- 응답:
  ```json
  {
    "node_id": "node-1",
    "job_id": "7b627e31-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "status": "PENDING",
    "message": "Job accepted and enqueued."
  }
  ```

#### 2) 워커 로그 관찰
Docker compose 로그 창에서 `dist-worker-1` 또는 `dist-worker-2`가 Redis Stream에서 작업을 소비하여 처리하는 로그를 실시간으로 확인할 수 있습니다:
```text
dist-worker-1 | [worker-1] Processing task 'ai_video_render'...
dist-worker-1 | [worker-1] Task completed!
```

#### 3) 작업 결과 조회
```bash
curl http://localhost:8080/case2/jobs/<발급받은_JOB_ID>
```
- 상태가 `COMPLETED`로 변경되고, 어느 워커(`worker_id`)가 처리했는지와 처리 결과가 출력됩니다.

---

### 2.5 [Case 3 실습] WebSocket 다중 서버 실시간 브로드캐스트

서로 다른 서버 노드에 연결된 클라이언트 간의 실시간 메시지 전파를 테스트합니다:

1. **클라이언트 1**: API Node #1 (`ws://localhost:8001/case3/ws/chat/room1`)에 WebSocket 연결
2. **클라이언트 2**: API Node #2 (`ws://localhost:8002/case3/ws/chat/room1`)에 WebSocket 연결
3. **HTTP를 통한 외부 브로드캐스트 테스트**:
   ```bash
   curl -X POST http://localhost:8080/case3/broadcast \
     -H "Content-Type: application/json" \
     -d '{
       "room_id": "room1",
       "sender": "운영자",
       "content": "5분 뒤 시스템 점검이 시작됩니다."
     }'
   ```
- Node 1과 Node 2에 연결된 모든 WebSocket 클라이언트에게 실시간으로 메시지가 즉시 수신됩니다!

---

### 2.6 [Case 4 실습] 서킷 브레이커 장애 격리 & 복구

#### 1) 정상 호출
```bash
curl http://localhost:8080/case4/call
```
- `circuit_state: "CLOSED"`, `status: "SUCCESS"` 확인

#### 2) 외부 결제 시스템 장애 주입
```bash
curl -X POST http://localhost:8080/case4/external/fault \
  -H "Content-Type: application/json" \
  -d '{"is_healthy": false}'
```

#### 3) 3회 호출하여 서킷 OPEN 유도
```bash
for i in {1..3}; do
  curl -s http://localhost:8080/case4/call | grep -o '"circuit_state":"[^"]*"'
done
```
- 3회 연속 실패 후 서킷 상태가 `"circuit_state":"OPEN"`으로 전이됩니다.

#### 4) 빠른 실패(Fast-Fail) 및 Fallback 응답 확인
```bash
curl http://localhost:8080/case4/call
```
- 외부 서비스를 호출하여 대기하지 않고, 즉시 `fallback_used: true`가 반환되어 서버 스레드 고갈을 방지합니다.

#### 5) 서비스 정상화 복구
```bash
curl -X POST http://localhost:8080/case4/external/fault \
  -H "Content-Type: application/json" \
  -d '{"is_healthy": true}'

# 4초(recovery_timeout) 대기 후 호출
sleep 4
curl http://localhost:8080/case4/call
```
- 서킷이 `HALF_OPEN`에서 성공을 확인하고 다시 `CLOSED`로 안전하게 복귀합니다.
