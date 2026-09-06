# 백엔드 + AI 시스템 6주 개념·코딩 실습 가이드

이 문서의 목표는 기술 이름을 외우는 것이 아니다.

최종 목표는 어떤 기술을 보더라도 다음 질문에 답할 수 있는 상태가 되는 것이다.

```text
1. 이 기술은 무엇인가?
2. 어떤 문제 때문에 등장했는가?
3. 실제 서비스에서 언제 필요한가?
4. 어떻게 구현하는가?
5. 단점과 주의점은 무엇인가?
6. 다른 기술과 어떤 관계가 있는가?
7. 면접에서 어떻게 설명해야 하는가?
```

전체 학습 흐름은 다음과 같다.

```text
1주차
HTTP / Stateless / Load Balancer / Scale Out / Connection Pool

2주차
Transaction / ACID / Isolation / 동시성 / Lock / Index

3주차
Redis / Cache / Idempotency / Distributed Lock / Rate Limit

4주차
Spring Boot / JPA / DI / Transaction / N+1

5주차
Message Queue / Retry / Timeout / Circuit Breaker / Observability

6주차
Vector Search / BM25 / Hybrid Search / RRF / Reranker / RAG Evaluation
```

---

# 0. 실습 프로젝트 준비

처음 3주 동안은 FastAPI 기반 프로젝트를 하나 만든다.

새로운 프로젝트를 매번 만들기보다 하나의 프로젝트에 기능을 계속 추가하는 것이 좋다.

예를 들어:

```text
backend-study
```

라는 프로젝트를 만들고 아래 기능을 순차적으로 넣는다.

```text
Pet CRUD
↓
Record CRUD
↓
Transaction
↓
Redis Cache
↓
Idempotency
↓
Rate Limit
↓
Queue
```

이렇게 해야 각 기술이 서로 어떻게 연결되는지 볼 수 있다.

---

## FastAPI 프로젝트 생성

```bash
uv init backend-study
cd backend-study

uv add fastapi uvicorn sqlalchemy asyncpg alembic pydantic-settings redis httpx
```

추천 구조:

```text
backend-study/
├── app/
│   ├── main.py
│   ├── api/
│   │   ├── pets.py
│   │   └── records.py
│   ├── models/
│   ├── schemas/
│   ├── services/
│   ├── repositories/
│   └── core/
├── tests/
└── pyproject.toml
```

각 디렉터리 역할:

```text
api
→ HTTP 요청/응답 담당

schemas
→ API 요청/응답 데이터 구조

services
→ 비즈니스 로직

repositories
→ DB 접근

models
→ DB 테이블 모델

core
→ 설정, DB, Redis 등 공통 기능
```

---

# 1주차 — 웹 서버와 확장 구조 이해

---

# 1-1. HTTP

## HTTP가 무엇인가?

HTTP는 클라이언트와 서버가 통신하기 위한 규칙이다.

브라우저나 앱이 서버에 요청을 보내면 서버가 응답을 돌려준다.

```text
Client
   ↓
HTTP Request
   ↓
Server

Server
   ↓
HTTP Response
   ↓
Client
```

예:

```http
GET /pets/1
```

라는 요청은:

```text
"ID가 1인 반려동물 정보를 주세요."
```

라는 의미다.

서버는:

```json
{
  "id": 1,
  "name": "초코"
}
```

처럼 응답할 수 있다.

---

## HTTP Method

HTTP Method는 서버에게 어떤 작업을 원하는지 표현한다.

```text
GET
→ 조회

POST
→ 새로운 데이터 생성

PUT
→ 데이터 전체 수정

PATCH
→ 데이터 일부 수정

DELETE
→ 삭제
```

예:

```text
GET /pets/1
→ Pet 조회

POST /pets
→ Pet 생성

PATCH /pets/1
→ Pet 이름 수정

DELETE /pets/1
→ Pet 삭제
```

---

## Status Code

Status Code는 요청 처리 결과를 알려주는 숫자다.

대표적으로:

```text
200 OK
→ 정상 처리

201 Created
→ 새로운 리소스 생성 성공

204 No Content
→ 성공했지만 응답 Body 없음
```

클라이언트 오류:

```text
400 Bad Request
→ 요청 자체가 잘못됨

401 Unauthorized
→ 인증되지 않음

403 Forbidden
→ 로그인은 했지만 권한 없음

404 Not Found
→ 데이터 없음

409 Conflict
→ 현재 상태와 충돌

422 Unprocessable Entity
→ 데이터 검증 실패

429 Too Many Requests
→ 요청 너무 많음
```

서버 오류:

```text
500 Internal Server Error
503 Service Unavailable
```

---

## FastAPI 실습

```python
from fastapi import FastAPI

app = FastAPI()


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.get("/pets/{pet_id}")
async def get_pet(pet_id: int):
    return {
        "id": pet_id,
        "name": "초코",
    }
```

실행:

```bash
uv run uvicorn app.main:app --reload
```

확인:

```text
GET /health
GET /pets/1
```

일부러:

```text
GET /pets/abc
```

도 호출해본다.

FastAPI는 `pet_id`를 `int`로 정의했기 때문에 문자열이 들어오면 Validation Error가 발생한다.

이 경험을 통해:

```text
HTTP 요청
↓
Framework Validation
↓
Business Logic
↓
HTTP Response
```

흐름을 이해한다.

---

# 1-2. REST

REST는 HTTP를 이용해 API를 설계할 때 자주 사용하는 설계 방식이다.

핵심은 API URL을 **동사가 아니라 자원 중심으로 표현**하는 것이다.

좋은 예:

```text
GET /pets
POST /pets
GET /pets/1
DELETE /pets/1
```

좋지 않은 예:

```text
GET /getPet
POST /createPet
POST /deletePet
```

REST에서는 URL 자체보다 HTTP Method가 행동을 표현한다.

```text
/pets
→ 반려동물이라는 Resource

GET
→ 조회

POST
→ 생성
```

---

# 1-3. Stateless

## 왜 필요한가?

웹 서비스 초기에는 서버 한 대만 있을 수 있다.

```text
Client
 ↓
Server A
```

이때 서버 메모리에 로그인 정보를 저장해도 당장은 문제가 없어 보인다.

예:

```python
logged_in_users = {
    100: True
}
```

그런데 사용자가 많아져 서버를 3대로 늘렸다고 하자.

```text
           ┌→ Server A
Client → LB├→ Server B
           └→ Server C
```

로그인 요청이 Server A로 갔다.

```text
Server A
user 100 = 로그인 상태
```

다음 요청은 Server B로 갈 수 있다.

Server B는 Server A 메모리를 볼 수 없다.

```text
Server B

"100번 사용자가 로그인했는지 모르겠는데?"
```

이 문제가 생긴다.

---

## Stateless의 의미

Stateless는 서버 자체가 사용자 요청 사이의 상태를 보관하지 않는 것이다.

상태가 필요하면 외부에 둔다.

대표적으로:

```text
JWT
Redis
Database
```

사용.

구조:

```text
Client
  │
  │ JWT
  ↓
Load Balancer
  │
  ├→ Server A
  ├→ Server B
  └→ Server C
```

어떤 서버로 가도 JWT에서 사용자를 확인할 수 있다.

---

## 중요한 관계

```text
Scale Out
   ↓
서버 여러 대
   ↓
아무 서버나 요청 처리 가능해야 함
   ↓
Stateless 필요
```

Stateless는 단순한 디자인 취향이 아니라 서버 확장성과 연결된다.

---

# 1-4. Load Balancer

Load Balancer는 여러 서버 앞에서 요청을 분배하는 역할을 한다.

```text
                    ┌→ Server A
Client → Load Balancer → Server B
                    └→ Server C
```

사용자가 3,000명 동시에 요청한다고 하자.

서버 한 대가 초당 1,000개만 처리할 수 있다면:

```text
Server 1대
→ 부족
```

서버 3대를 두고 요청을 나누면:

```text
Server A → 1,000
Server B → 1,000
Server C → 1,000
```

처럼 처리할 수 있다.

---

## Load Balancer가 하는 일

단순 분배만 하는 것은 아니다.

```text
트래픽 분산

Health Check

장애 서버 제외

SSL 종료

Routing
```

예를 들어 Server B가 죽었다.

```text
Server A → 정상
Server B → 장애
Server C → 정상
```

Load Balancer가 Health Check를 통해 이를 감지하면:

```text
A와 C만 사용
```

하도록 할 수 있다.

---

# 1-5. Scale Up과 Scale Out

## Scale Up

서버 한 대의 성능을 높이는 방식.

```text
4 CPU
8GB RAM

↓

32 CPU
128GB RAM
```

장점:

```text
구조 단순
설정 쉬움
```

단점:

```text
성능 증가에 한계
고사양 장비 비용 증가
한 대가 죽으면 장애 영향 큼
```

---

## Scale Out

서버 수를 늘리는 방식.

```text
Server 1대

↓

Server 10대
```

장점:

```text
트래픽 증가 대응
장애 대응 쉬움
수평 확장 가능
```

단점:

```text
분산 환경 문제가 발생
상태 관리 어려움
동시성 문제 증가
운영 복잡도 증가
```

중요한 흐름:

```text
Scale Out
↓
서버 여러 대
↓
Stateless
↓
Load Balancer
↓
Redis / DB 같은 공유 저장소 필요
```

---

# 1-6. DB Connection Pool

## 왜 필요한가?

API 요청마다 DB에 새로 연결한다고 생각해보자.

```text
Request
 ↓
DB Connection 생성
 ↓
Authentication
 ↓
Query
 ↓
Connection 종료
```

DB Connection 생성 자체에도 비용이 든다.

그래서 미리 여러 DB Connection을 만들어 놓는다.

```text
Connection Pool

[Connection 1]
[Connection 2]
[Connection 3]
[Connection 4]
```

요청:

```text
Request
 ↓
Pool에서 Connection 빌림
 ↓
Query
 ↓
Connection 반환
```

---

## SQLAlchemy 예

```python
from sqlalchemy.ext.asyncio import create_async_engine

engine = create_async_engine(
    "postgresql+asyncpg://user:password@localhost/study",
    pool_size=10,
    max_overflow=20,
)
```

의미:

```text
pool_size=10
→ 평소 유지하는 Connection 수

max_overflow=20
→ 순간적으로 추가 생성 가능한 Connection
```

최대:

```text
30개
```

까지 사용할 수 있다.

---

## 왜 크게 잡으면 안 되는가?

API 서버가 10대 있다고 하자.

각 서버:

```text
pool_size=50
```

이면:

```text
50 × 10
= 500 Connection
```

DB가 동시에 500개의 Connection을 처리해야 한다.

Connection은 공짜가 아니다.

각 Connection은:

```text
메모리 사용
DB Process/Thread 사용
Transaction 상태 유지
```

등의 비용이 있다.

따라서 Connection Pool은:

```text
클수록 좋다 ❌

DB 용량과 처리량에 맞게 설정 ✅
```

해야 한다.

---

# 1주차 면접 체크

다음 질문에 말로 답해본다.

```text
Stateless란 무엇입니까?

왜 Scale Out 환경에서 Stateless가 중요합니까?

Load Balancer는 어떤 역할을 합니까?

Scale Up과 Scale Out의 차이는?

DB Connection Pool을 사용하는 이유는?

Connection Pool이 너무 크면 어떤 문제가 생깁니까?
```

---

# 2주차 — DB와 동시성

이 주차는 백엔드 공부에서 가장 중요하다.

---

# 2-1. Transaction

## 문제 상황

온라인 쇼핑몰 주문을 생각해보자.

주문 하나를 만들 때:

```text
1. 주문 생성
2. 재고 감소
3. 결제 기록 생성
```

이 필요하다.

그런데:

```text
주문 생성 성공
재고 감소 성공
결제 기록 생성 실패
```

하면 데이터가 이상해진다.

주문은 있는데 결제는 없는 상태가 된다.

---

## Transaction

Transaction은 여러 DB 작업을 하나의 작업처럼 처리한다.

```text
BEGIN

주문 생성
재고 감소
결제 생성

COMMIT
```

전부 성공하면 Commit.

중간에 오류가 나면:

```text
ROLLBACK
```

해서 이전 상태로 되돌린다.

---

## SQLAlchemy 예

```python
async with session.begin():

    order = Order(
        user_id=user_id,
        product_id=product_id,
    )

    session.add(order)

    product.stock -= 1

    payment = Payment(
        order=order,
        amount=10000,
    )

    session.add(payment)
```

이 안에서 Exception이 발생하면 Transaction이 Rollback된다.

---

# 2-2. ACID

Transaction의 중요한 특성.

```text
Atomicity
Consistency
Isolation
Durability
```

---

## Atomicity

원자성.

```text
전부 성공하거나
전부 실패
```

해야 한다.

송금:

```text
A 계좌 -10,000
B 계좌 +10,000
```

A에서만 돈이 빠지고 B에는 입금되지 않으면 안 된다.

---

## Consistency

DB가 정해진 규칙을 유지해야 한다.

예:

```text
재고 >= 0

email UNIQUE

Foreign Key 유효
```

Transaction 전후로 이런 규칙이 깨지지 않아야 한다.

---

## Isolation

동시에 여러 Transaction이 실행되어도 서로 이상한 영향을 주지 않아야 한다.

예:

```text
사용자 A가 재고 조회
사용자 B도 재고 조회
```

동시에 접근할 때 잘못된 결과가 발생하지 않도록 하는 개념이다.

---

## Durability

Commit이 완료된 데이터는 시스템이 재시작돼도 유지되어야 한다.

```text
COMMIT 완료
↓
서버 재부팅
↓
데이터 유지
```

---

# 2-3. Isolation Level

Transaction을 얼마나 강하게 격리할지 결정한다.

대표적인 단계:

```text
READ UNCOMMITTED
READ COMMITTED
REPEATABLE READ
SERIALIZABLE
```

아래로 갈수록:

```text
데이터 안정성 ↑

동시 처리 성능 ↓
```

한다.

---

## READ COMMITTED

PostgreSQL 기본 Isolation Level.

다른 Transaction에서 Commit된 데이터만 읽는다.

예:

```text
Transaction A

UPDATE stock=5
아직 Commit 안 함
```

Transaction B는:

```text
기존 값
```

을 본다.

A가 Commit하면 그 다음 조회부터 새 값이 보인다.

---

## SERIALIZABLE

가장 강력한 격리 수준 중 하나.

Transaction이 마치 순서대로 하나씩 실행되는 것처럼 동작하게 만든다.

안전하지만:

```text
충돌
Retry
성능 저하
```

가능성이 있다.

---

# 2-4. Race Condition

Race Condition은 여러 실행 흐름이 같은 데이터를 동시에 수정하면서 결과가 실행 순서에 따라 달라지는 문제다.

예:

```text
stock = 1
```

사용자 A:

```text
SELECT stock
→ 1
```

사용자 B:

```text
SELECT stock
→ 1
```

A:

```text
재고 있음
→ 구매
```

B도:

```text
재고 있음
→ 구매
```

결국 하나뿐인 상품을 두 명에게 판매하게 된다.

---

## 중요한 점

코드만 보면:

```python
if product.stock > 0:
    product.stock -= 1
```

문제가 없어 보인다.

하지만 실제 서버에서는 여러 요청이 동시에 실행된다.

그래서 백엔드에서는 항상:

```text
"동시에 실행되면 어떻게 되지?"
```

를 생각해야 한다.

---

# 2-5. Pessimistic Lock

Pessimistic은 비관적이라는 뜻이다.

생각:

```text
"충돌이 일어날 가능성이 높다.
그러니 먼저 잠그자."
```

SQL:

```sql
SELECT *
FROM products
WHERE id = 1
FOR UPDATE;
```

Transaction A가 실행하면 해당 Row에 Lock이 걸린다.

```text
Transaction A
↓
Product 1 Lock
```

Transaction B가 같은 Product를 수정하려 하면:

```text
대기
```

한다.

A가 Commit한 뒤 B가 실행된다.

---

## SQLAlchemy

```python
stmt = (
    select(Product)
    .where(Product.id == product_id)
    .with_for_update()
)

result = await session.execute(stmt)

product = result.scalar_one()

if product.stock <= 0:
    raise Exception("품절")

product.stock -= 1
```

---

## 장점

```text
구현 직관적
강력한 충돌 방지
```

## 단점

```text
Lock 대기
처리량 감소
Deadlock 위험
```

---

# 2-6. Optimistic Lock

Optimistic은 낙관적이라는 뜻.

생각:

```text
"동시에 수정하는 경우가 그렇게 많지는 않을 것이다.
Lock을 걸지 말고 충돌하면 그때 처리하자."
```

이를 위해 Version을 사용한다.

현재:

```text
stock = 10
version = 5
```

A와 B가 동시에 읽는다.

둘 다:

```text
version = 5
```

A가 수정:

```sql
UPDATE products
SET
    stock = 9,
    version = 6
WHERE
    id = 1
AND
    version = 5;
```

성공.

B도 같은 SQL 수행:

```text
version 5인 데이터 없음
```

따라서:

```text
rowcount = 0
```

충돌 감지.

B는:

```text
다시 조회
→ Retry
```

할 수 있다.

---

## 장점

```text
Lock 대기 없음
읽기 많은 시스템에 유리
```

## 단점

```text
충돌이 많으면 Retry 증가
```

---

# 2-7. Deadlock

두 Transaction이 서로 상대방의 Lock을 기다리는 상황.

예:

```text
Transaction A
Product 1 Lock 획득

Transaction B
Product 2 Lock 획득
```

A:

```text
Product 2 필요
→ B가 잠금
```

B:

```text
Product 1 필요
→ A가 잠금
```

결과:

```text
A는 B 기다림
B는 A 기다림
```

영원히 진행할 수 없다.

DB는 이를 감지하고 하나의 Transaction을 종료한다.

---

## 예방 방법

대표적으로:

```text
항상 동일한 순서로 Lock 획득

Transaction 짧게 유지

불필요한 Lock 최소화

Retry 전략 적용
```

---

# 2-8. Index

## 왜 필요한가?

사용자가 1,000만 명 있는 테이블에서 이메일을 찾는다고 하자.

Index가 없으면 DB가:

```text
1번 Row
2번 Row
3번 Row
...
10,000,000번 Row
```

을 확인할 수 있다.

이를:

```text
Full Table Scan
Seq Scan
```

이라고 한다.

---

## Index

Index는 책의 목차와 비슷하다.

책에서 "Transaction"이라는 단어를 찾을 때 처음부터 끝까지 모든 페이지를 읽지 않는다.

목차나 색인을 보고 위치를 찾는다.

DB Index도 비슷하다.

```text
Index
↓
위치 탐색
↓
실제 데이터 접근
```

---

## B-Tree Index

가장 대표적인 Index.

정렬된 Tree 구조로 데이터를 관리한다.

다음과 같은 비교 검색에 강하다.

```text
=
>
<
>=
<=
ORDER BY
```

---

# Index 실습

```sql
EXPLAIN ANALYZE
SELECT *
FROM users
WHERE email = 'test@example.com';
```

Index가 없다면:

```text
Seq Scan
```

이 나올 수 있다.

추가:

```sql
CREATE INDEX idx_users_email
ON users(email);
```

다시 실행.

```text
Index Scan
```

여부 확인.

---

# Index의 단점

Index가 많을수록 항상 좋은 것은 아니다.

INSERT:

```text
Table 데이터 저장

+

모든 관련 Index 업데이트
```

필요.

즉:

```text
조회 성능 ↑

쓰기 비용 ↑

저장 공간 ↑
```

이다.

---

# 2-9. Composite Index

여러 컬럼을 하나의 Index에 넣는다.

```sql
CREATE INDEX idx_records_pet_created
ON records(pet_id, created_at);
```

Index 구조는 대략:

```text
pet_id 기준
   ↓
created_at 정렬
```

이라고 생각하면 된다.

따라서:

```sql
WHERE pet_id = 10
```

좋다.

```sql
WHERE pet_id = 10
ORDER BY created_at
```

매우 좋을 수 있다.

하지만:

```sql
WHERE created_at > ...
```

만 사용하면 `pet_id`를 건너뛰기 때문에 효율이 떨어질 수 있다.

그래서:

```text
Composite Index는 컬럼 순서가 중요하다.
```

---

# 3주차 — Redis / Cache / Idempotency

---

# 3-1. Redis

Redis는 메모리 기반 데이터 저장소다.

PostgreSQL은 주로 Disk 기반이고 Redis는 주로 RAM을 사용한다.

```text
PostgreSQL
→ 영구 데이터 저장

Redis
→ 빠른 임시 데이터 접근
```

Redis는 매우 빠르기 때문에 다음 용도로 많이 사용한다.

```text
Cache

Session

Rate Limit

Distributed Lock

Queue

실시간 Counter
```

---

# 3-2. Cache

## Cache를 왜 쓰는가?

Pet 정보가 거의 바뀌지 않는데 1초에 10,000번 조회된다고 해보자.

매 요청:

```text
API
 ↓
PostgreSQL
```

이면 DB에 부하가 집중된다.

Redis를 넣는다.

```text
API
 ↓
Redis
 ↓
있음 → 반환

없음
 ↓
PostgreSQL
```

DB까지 가지 않는 요청이 많아진다.

---

# Cache Aside Pattern

가장 흔한 패턴.

```text
1. Cache 조회

2. 데이터 있으면 반환

3. 없으면 DB 조회

4. DB 결과를 Cache에 저장

5. 사용자에게 반환
```

구현:

```python
async def get_pet(pet_id: int):

    key = f"pet:{pet_id}"

    cached = await redis_client.get(key)

    if cached:
        return json.loads(cached)

    pet = await repository.get_pet(pet_id)

    await redis_client.set(
        key,
        json.dumps(pet),
        ex=60,
    )

    return pet
```

---

# TTL

TTL은 Time To Live.

Cache 데이터가 얼마나 오래 살아있을지 정한다.

```text
TTL = 60초
```

이면:

```text
60초 후 Redis에서 자동 삭제
```

한다.

---

# Cache Invalidation

Cache에서 가장 어려운 문제 중 하나다.

DB:

```text
Pet name = 콩이
```

Redis:

```text
Pet name = 초코
```

라면 사용자가 오래된 데이터를 받는다.

따라서 Pet을 수정할 때:

```python
await update_pet()

await redis_client.delete(
    f"pet:{pet_id}"
)
```

처럼 Cache를 삭제해야 한다.

이것이:

```text
Cache Invalidation
```

이다.

---

# 3-3. Cache Stampede

Cache 데이터가 만료된 순간 수많은 요청이 동시에 들어오는 상황.

```text
Cache 만료
↓
10,000 요청
↓
모두 Cache Miss
↓
10,000 요청이 DB 접근
```

DB가 갑자기 터질 수 있다.

해결 방법:

```text
TTL Randomization

Lock

Background Refresh
```

등이 있다.

이런 개념까지 알면 실무 이해도가 높아진다.

---

# 3-4. Idempotency

## 왜 필요한가?

사용자가:

```text
POST /records
```

요청.

서버는 Record를 DB에 저장했다.

그런데 서버 응답이 네트워크 문제로 클라이언트에 도착하지 않았다.

클라이언트 입장:

```text
"실패했나?"
```

Retry.

서버:

```text
새로운 Record 생성
```

결과:

```text
동일 Record 2개
```

---

## Idempotency Key

클라이언트가 요청마다 고유한 Key를 만든다.

예:

```text
550e8400-e29b-41d4-a716-446655440000
```

Header:

```http
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

서버는:

```text
이 Key를 처리한 적 있는가?
```

확인한다.

---

## 최초 요청

```text
Key 없음
↓
작업 실행
↓
결과 저장
↓
Key 저장
```

---

## Retry

```text
같은 Key 존재
↓
실제 작업 다시 하지 않음
↓
기존 결과 반환
```

---

# Request Hash가 필요한 이유

사용자가 같은 Key를 사용하면서 다른 요청을 보낼 수 있다.

첫 요청:

```text
Key = ABC

amount = 10,000
```

두 번째:

```text
Key = ABC

amount = 100,000
```

Key만 비교하면 위험하다.

그래서 Request Body를 Hash한다.

```python
import hashlib
import json


def make_request_hash(data: dict):

    payload = json.dumps(
        data,
        sort_keys=True,
    )

    return hashlib.sha256(
        payload.encode()
    ).hexdigest()
```

비교:

```text
같은 Key
+
같은 Hash

→ 기존 결과
```

```text
같은 Key
+
다른 Hash

→ 409 Conflict
```

---

# UNIQUE Constraint

애플리케이션 코드만으로는 완벽하게 중복 방지하기 어렵다.

왜냐하면 동시에 두 요청이 들어올 수 있기 때문이다.

```text
Request A
Request B
```

A:

```text
Key 없음
```

B:

```text
Key 없음
```

둘 다 실행 가능.

그래서 DB에서:

```sql
CREATE TABLE idempotency_requests (
    key VARCHAR(100) PRIMARY KEY,
    ...
);
```

처럼 Unique를 강제한다.

DB를 최종 방어선으로 두는 것이다.

---

# 3-5. Distributed Lock

서버 한 대라면 Python Lock 같은 것을 사용할 수 있다.

하지만:

```text
Server A
Server B
Server C
```

가 있으면:

```text
Server A의 Lock
≠
Server B의 Lock
```

이다.

서버끼리 메모리를 공유하지 않는다.

그래서 외부 공유 저장소를 사용한다.

대표적으로 Redis.

```text
Server A ─┐
Server B ─┼→ Redis Lock
Server C ─┘
```

예:

```text
lock:product:100
```

이라는 Lock Key를 Redis에 생성한다.

---

## 언제 사용하는가?

예:

```text
동일 사용자 작업 중복 실행

예약 처리

배치 작업 중복 실행

분산 Scheduler

특정 자원에 대한 순차 처리
```

---

## 주의

Distributed Lock은 단순히:

```text
SET key value
```

만 하는 문제가 아니다.

반드시 고려:

```text
TTL

Lock 소유자 확인

장애 시 Lock 해제

Lock 만료

네트워크 단절
```

해야 한다.

---

# 3-6. Rate Limit

API 사용량을 제한하는 기능.

예:

```text
한 사용자당
1분에 60번
```

61번째 요청:

```text
429 Too Many Requests
```

---

## 왜 필요한가?

```text
악성 사용자 방지

API 남용 방지

비용 제어

서버 보호

LLM API 비용 보호
```

특히 AI 서비스에서는 매우 중요하다.

사용자가 무제한으로 LLM 호출하면:

```text
API 비용 폭증
```

가능.

---

## Redis 방식

```python
async def rate_limit(user_id: int):

    key = f"rate:{user_id}"

    count = await redis_client.incr(key)

    if count == 1:
        await redis_client.expire(key, 60)

    if count > 60:
        raise HTTPException(
            status_code=429
        )
```

---

# Rate Limit 알고리즘

## Fixed Window

```text
01:00~01:01
60회
```

같은 고정 시간 창 사용.

구현 간단.

단점:

시간 경계에서 요청이 몰릴 수 있다.

---

## Sliding Window

최근 60초를 계속 계산.

더 정확하지만 구현이 복잡하다.

---

## Token Bucket

Bucket에 Token을 채워두고 요청할 때 하나씩 소비.

```text
Token 있음
→ 요청 처리

Token 없음
→ 제한
```

Burst Traffic 처리에 좋다.

---

# 4주차 — Spring Boot / JPA

이 주차의 목적은 Java 문법 공부가 아니다.

FastAPI에서 알고 있는 개념을 Spring으로 옮기는 것이다.

---

# 4-1. Spring 구조

대표적인 Layer:

```text
Controller
↓
Service
↓
Repository
↓
Database
```

각 역할:

```text
Controller
→ HTTP 처리

Service
→ 비즈니스 로직

Repository
→ DB 접근

Entity
→ DB 객체

DTO
→ API 데이터
```

---

# Controller

```java
@RestController
@RequestMapping("/pets")
public class PetController {

    private final PetService petService;

    public PetController(PetService petService) {
        this.petService = petService;
    }

    @GetMapping("/{id}")
    public PetResponse getPet(
        @PathVariable Long id
    ) {
        return petService.getPet(id);
    }
}
```

FastAPI로 치면:

```python
@app.get("/pets/{id}")
```

와 유사하다.

---

# 4-2. Dependency Injection

Dependency Injection은 객체가 필요한 Dependency를 직접 만들지 않고 외부에서 주입받는 방식이다.

좋지 않은 예:

```java
public class PetService {

    private PetRepository repo =
        new PetRepository();
}
```

PetService가 Repository 생성 책임까지 가진다.

Spring에서는:

```java
public PetService(
    PetRepository repository
) {
    this.repository = repository;
}
```

처럼 받는다.

Spring이 객체를 생성하고 연결해준다.

---

## 왜 좋은가?

```text
결합도 감소

테스트 쉬움

구현 교체 쉬움
```

예:

```text
Real Repository

↓

Mock Repository
```

교체 가능.

---

# 4-3. JPA

JPA는 Java 객체와 관계형 DB를 연결하는 ORM 표준이다.

예:

```java
@Entity
public class Pet {

    @Id
    @GeneratedValue
    private Long id;

    private String name;
}
```

이 Java 객체가 DB Table과 연결된다.

```text
Java Pet
↕

PostgreSQL pet table
```

---

# 4-4. Persistence Context

JPA에서 매우 중요한 개념.

Entity를 조회하면 JPA가 관리한다.

```text
DB
↓
Pet Entity
↓
Persistence Context
```

이후 Entity 값을 바꾸면:

```java
pet.setName("콩이");
```

직접 UPDATE SQL을 쓰지 않아도 Transaction Commit 때 변경 감지를 통해 UPDATE할 수 있다.

이것이:

```text
Dirty Checking
```

이다.

---

# 4-5. Lazy Loading

연관된 데이터를 실제 사용할 때 조회하는 방식.

예:

```java
pet.getRecords()
```

를 호출하는 순간 Record Query가 실행될 수 있다.

장점:

```text
필요 없는 데이터 미리 안 가져옴
```

단점:

```text
N+1 발생 가능
```

---

# 4-6. N+1

Pet 100개 조회.

```sql
SELECT * FROM pet;
```

1 Query.

각 Pet의 Records 접근.

```sql
SELECT * FROM record WHERE pet_id=1;
SELECT * FROM record WHERE pet_id=2;
...
```

100 Query.

총:

```text
1 + 100
```

그래서 N+1.

---

## 해결

Fetch Join:

```java
@Query("""
    select p
    from Pet p
    join fetch p.records
""")
List<Pet> findAllWithRecords();
```

한 번에 필요한 데이터 조회.

---

# 5주차 — 비동기 처리와 장애 대응

---

# 5-1. Message Queue

## 왜 필요한가?

사용자가 Record를 생성한다고 하자.

기존:

```text
Record 저장
↓
LLM 호출
↓
분석
↓
알림
↓
응답
```

LLM에 10초 걸리면:

```text
사용자 10초 기다림
```

한다.

이 작업이 반드시 즉시 완료될 필요가 없다면 Queue로 넘길 수 있다.

```text
Client
↓
API
↓
Record 저장
↓
Queue에 Job 넣음
↓
즉시 응답
```

Worker:

```text
Queue
↓
Job 가져옴
↓
LLM
↓
결과 저장
```

사용자 응답 시간이 크게 줄어든다.

---

# Queue의 장점

```text
비동기 처리

서비스 간 결합도 감소

트래픽 흡수

작업 재시도

Worker 수평 확장
```

---

# 5-2. Kafka

Kafka는 대규모 이벤트 스트리밍 시스템이다.

대표 구조:

```text
Producer
↓
Topic
↓
Partition
↓
Consumer
```

예:

```text
OrderCreated
```

라는 Event가 발생.

이를:

```text
결제 서비스

배송 서비스

통계 서비스

추천 서비스
```

가 각각 소비할 수 있다.

---

# Kafka 핵심 용어

```text
Broker
→ Kafka 서버

Topic
→ Event 종류

Partition
→ Topic을 나눈 단위

Producer
→ Event 생성

Consumer
→ Event 처리

Consumer Group
→ Consumer 묶음

Offset
→ 어디까지 읽었는지 위치
```

---

# 5-3. RabbitMQ

RabbitMQ는 전통적인 Message Broker.

```text
Producer
↓
Exchange
↓
Queue
↓
Consumer
```

간단한 차이:

```text
RabbitMQ
→ 작업 전달 중심

Kafka
→ 이벤트 로그/스트리밍 중심
```

이 정도부터 이해하면 충분하다.

---

# 5-4. Retry

외부 API 호출은 실패할 수 있다.

```text
네트워크 장애

Timeout

429

503
```

이때 Retry할 수 있다.

하지만 무조건 즉시 Retry하면 안 된다.

외부 서버가 죽어있는데:

```text
Retry
Retry
Retry
Retry
```

하면 더 큰 부하를 준다.

---

# Exponential Backoff

재시도 간격을 점점 늘린다.

```text
1초

2초

4초

8초
```

코드:

```python
for attempt in range(4):

    try:
        return await call_llm()

    except Exception:
        await asyncio.sleep(
            2 ** attempt
        )
```

실무에서는 Jitter도 추가한다.

```text
2초

+

Random 값
```

여러 서버가 동시에 Retry하는 것을 줄이기 위해서다.

---

# 5-5. Timeout

외부 API가 응답하지 않을 수 있다.

Timeout이 없다면:

```text
Request
↓
영원히 대기
```

할 수 있다.

그 동안:

```text
Connection
Worker
Memory
```

등 자원을 계속 사용한다.

그래서:

```python
async with httpx.AsyncClient(
    timeout=5.0
) as client:
    ...
```

같이 제한을 둔다.

---

# 5-6. Circuit Breaker

외부 시스템이 이미 장애 상태인데 계속 호출하는 것은 의미가 없다.

Circuit Breaker는 장애가 많아지면 잠시 호출 자체를 차단한다.

상태:

```text
Closed
↓
정상 요청

실패 증가
↓

Open
↓
요청 차단

시간 경과
↓

Half Open
↓
시험 요청
```

시험 요청이 성공하면:

```text
Closed
```

복귀.

---

## AI 시스템 예

```text
GPT 호출
↓
5회 연속 실패
↓
Circuit Open
↓
GPT 호출 중단
↓
Gemini 사용
```

이런 구조를 만들 수 있다.

---

# 5-7. Observability

서비스를 운영하면 다음 질문에 답할 수 있어야 한다.

```text
왜 느리지?

어디서 에러 났지?

특정 요청은 어떤 서버를 거쳤지?

DB가 느린가?

LLM이 느린가?
```

이를 위해:

```text
Logs
Metrics
Traces
```

를 사용한다.

---

# Logs

사건 기록.

예:

```text
request_id=abc123

user_id=100

endpoint=/records

status=500

error=db timeout
```

---

# Metrics

숫자 기반 상태.

예:

```text
Requests/sec = 500

Error Rate = 1%

P95 Latency = 800ms

CPU = 75%

DB Connections = 80
```

---

## P95

100개의 요청을 빠른 순서대로 정렬했을 때:

```text
95번째 요청의 응답 시간
```

이다.

평균보다 실제 사용자 체감을 이해하는 데 유용하다.

---

# Trace

한 요청이 여러 시스템을 어떻게 지나갔는지 추적한다.

```text
API
20ms
↓
DB
30ms
↓
Retriever
50ms
↓
LLM
2500ms
```

이렇게 보면:

```text
LLM이 가장 느림
```

을 바로 알 수 있다.

---

# 6주차 — AI Engineering

여기서는 모델 자체보다 RAG 시스템 품질을 개선하고 평가하는 능력에 집중한다.

---

# 6-1. Embedding

Embedding은 텍스트를 숫자 Vector로 바꾸는 것이다.

```text
"강아지가 양파를 먹었어요"

↓

[0.15, -0.28, 0.91, ...]
```

의미가 비슷한 문장은 Vector 공간에서 가까워질 가능성이 높다.

예:

```text
"강아지가 양파를 먹음"

"개가 양파를 섭취함"
```

단어는 다르지만 의미가 비슷하다.

Embedding Search가 이런 경우에 유리하다.

---

# 6-2. Vector Search

Query를 Embedding으로 변환한다.

```text
Query
↓
Embedding Model
↓
Query Vector
```

DB에 저장된 Document Vector들과 거리 계산.

```text
가장 가까운 Document
```

를 찾는다.

주로:

```text
Cosine Similarity

Dot Product

Euclidean Distance
```

등을 사용한다.

---

# 6-3. BM25

BM25는 Keyword 기반 검색 알고리즘.

Vector Search는 의미 검색에 강하지만 정확한 단어 검색에서는 약할 수 있다.

예:

```text
"GPT-5.6"
```

처럼 정확한 제품명이나 코드가 중요한 경우 BM25가 유리할 수 있다.

---

# 6-4. Hybrid Search

그래서 두 방법을 같이 사용한다.

```text
Vector Search
+
BM25
```

Vector는:

```text
의미
```

BM25는:

```text
단어
```

를 잘 잡는다.

둘을 결합:

```text
Hybrid Search
```

---

# 6-5. RRF

두 검색 시스템에서 나온 순위를 결합한다.

Vector:

```text
1위 A
2위 B
3위 C
```

BM25:

```text
1위 B
2위 D
3위 A
```

RRF를 사용하면:

```text
B
A
...
```

같이 공통적으로 높은 문서를 우선할 수 있다.

공식:

```text
score(d)

=

Σ 1 / (k + rank(d))
```

중요:

```text
Embedding Vector를 합치는 것이 아니다.

각 검색기의 Ranking을 합치는 것이다.
```

---

# 6-6. Reranker

Retriever는 빠르게 후보를 찾는다.

예:

```text
100만 문서

↓

Top 50
```

하지만 Retriever 순위가 완벽하지 않다.

Reranker는 후보 50개를 더 정교하게 다시 평가한다.

```text
Query + Document
↓
Relevance Score
```

그 후:

```text
Top 5
```

선택.

---

## 왜 Retriever와 Reranker를 나누는가?

Reranker는 보통 더 정확하지만 비싸다.

100만 문서를 전부 Rerank하면 너무 느리다.

그래서:

```text
빠른 Retriever

↓

후보 축소

↓

비싼 Reranker
```

구조를 사용한다.

---

# 전체 RAG 검색 구조

```text
User Query
    ↓
Query Processing
    ↓
┌───────────────┐
│               │
BM25         Vector Search
│               │
└───────┬───────┘
        ↓
       RRF
        ↓
   Top 20 후보
        ↓
    Reranker
        ↓
     Top 5
        ↓
       LLM
        ↓
    Final Answer
```

---

# 6-7. Recall@K

정답 문서를 찾았는지를 평가한다.

정답:

```text
Document 7
```

검색 결과:

```text
Top 5

D2
D7
D9
D10
D20
```

정답 D7이 포함되어 있으므로 성공.

여러 Query에서 측정한다.

예:

```text
100개 Query

정답 포함 85개

Recall@5 = 0.85
```

---

# 6-8. Precision

검색한 문서 중 얼마나 관련 문서였는지 본다.

예:

```text
Top 5

관련 문서 4개
```

이면:

```text
Precision@5 = 4 / 5
```

---

# 6-9. MRR

정답이 몇 번째에 처음 등장했는지를 평가한다.

예:

정답이:

```text
1위
```

이면:

```text
1 / 1 = 1
```

3위:

```text
1 / 3
```

정답을 위쪽에 배치하는 것이 중요할 때 사용한다.

---

# 6-10. nDCG

nDCG는 단순히 정답이 있느냐뿐 아니라:

```text
좋은 문서가 좋은 순서에 배치됐는가?
```

를 평가한다.

예:

검색 A:

```text
1위 매우 관련
2위 관련
3위 관련 없음
```

검색 B:

```text
1위 관련 없음
2위 관련
3위 매우 관련
```

둘 다 관련 문서 수는 같을 수 있다.

하지만 A가 더 좋은 검색 결과다.

이 차이를 평가하는 데 nDCG가 유용하다.

---

# RAG Evaluation 실습

평가 데이터 준비:

```json
{
  "query": "강아지가 양파를 먹었어요",
  "relevant_document_ids": [
    101,
    105
  ]
}
```

Retriever 실행:

```python
results = search(
    query,
    top_k=5,
)
```

검색된 ID:

```python
retrieved_ids = {
    item.id
    for item in results
}
```

정답과 비교:

```python
hit = bool(
    retrieved_ids
    & relevant_document_ids
)
```

100개 Query 반복.

---

# 실험 비교

다음처럼 기록한다.

| 검색 방식 | Recall@5 | nDCG@5 | P95 Latency |
|---|---:|---:|---:|
| Vector | 0.78 | 0.69 | 40ms |
| BM25 | 0.71 | 0.66 | 20ms |
| Hybrid | 0.84 | 0.75 | 55ms |
| Hybrid + Reranker | 0.86 | 0.82 | 130ms |

중요한 것은:

```text
Hybrid가 더 좋았다.
```

로 끝내는 것이 아니다.

면접에서는:

```text
Recall@5는 6%p 개선됐지만

Latency가 75ms 증가했고

서비스 요구사항상 허용 가능한 수준이어서

Reranker를 적용했다.
```

정도로 설명하는 것이 좋다.

---

# 최종 프로젝트

6주 동안 공부한 기술을 하나로 합친다.

구조:

```text
                         ┌→ FastAPI Server A
Client
  ↓
API Gateway
  ↓
Load Balancer ──────────┼→ FastAPI Server B
                         └→ FastAPI Server C
                                │
                     ┌──────────┴──────────┐
                     ↓                     ↓
                   Redis               PostgreSQL
                     │
                     ↓
                   Queue
                     │
                     ↓
                AI Worker
                     │
                     ↓
             Retriever / LLM
```

---

# 구현해야 할 기능

API:

```text
POST /pets

GET /pets/{id}

POST /records

GET /records/{id}

POST /ai/questions
```

적용:

```text
Transaction

Connection Pool

Index

Optimistic Lock

Redis Cache

Cache Invalidation

Idempotency Key

Rate Limit

Queue

Retry

Timeout

Logging
```

AI:

```text
Embedding

Vector Search

BM25

Hybrid Search

RRF

Reranker

Recall@K

nDCG
```

---

# 하루 공부 방식

2시간 기준:

```text
30분

개념 공부

↓

60분

직접 코드 작성

↓

20분

일부러 문제 만들기

↓

10분

면접 답변 정리
```

특히:

```text
일부러 문제 만들기
```

가 중요하다.

예:

```text
Lock 없이 재고 감소
→ Race Condition 발생시켜보기

Index 제거
→ Query 느려지는지 확인

Redis 끄기
→ 시스템이 어떻게 동작하는지 확인

LLM Timeout 발생
→ Retry 확인
```

문제를 직접 만들어봐야 개념이 오래 남는다.

---

# 개념 공부 템플릿

모든 개념을 아래처럼 정리한다.

## 예: Idempotency

### 1. 무엇인가?

동일한 요청을 여러 번 보내더라도 결과가 한 번 요청한 것과 같도록 만드는 성질.

### 2. 왜 필요한가?

```text
Network Retry

Double Click

Timeout

Queue 중복 전달
```

때문에 동일 작업이 여러 번 실행될 수 있다.

### 3. 어떻게 구현하는가?

```text
UUID 생성

↓

Idempotency-Key Header

↓

DB Unique Constraint

↓

Request Hash

↓

기존 결과 저장
```

### 4. 단점

```text
Idempotency 데이터 저장 필요

TTL 관리

동시성 처리 필요

저장 용량 증가
```

### 5. 서비스에서는?

```text
결제

주문

회원가입

쿠폰 사용

Pet 기록 생성
```

등에 사용할 수 있다.

---

# 면접 질문 공부법

하나의 기술을 배운 후 반드시 아래 질문에 답한다.

```text
이 기술이 무엇인가?

왜 필요합니까?

없으면 어떤 문제가 생깁니까?

다른 해결 방법은 무엇입니까?

장점과 단점은 무엇입니까?

트래픽이 100배 증가하면 어떻게 됩니까?

서버가 10대면 어떻게 달라집니까?

장애가 발생하면 어떻게 합니까?
```

이 질문에 자연스럽게 답할 수 있다면:

```text
외운 것

↓

이해한 것
```

으로 넘어간 것이다.

---

# 최종 공부 우선순위

## 최우선

```text
Transaction

동시성

Pessimistic Lock

Optimistic Lock

Index

Connection Pool

Idempotency
```

이 부분은 백엔드 면접에서 매우 중요하다.

---

## 두 번째

```text
Stateless

Scale Out

Load Balancer

Redis

Cache

Distributed Lock

Rate Limit
```

분산 서버 이해를 위해 중요하다.

---

## 세 번째

```text
Message Queue

Retry

Timeout

Circuit Breaker

Observability
```

운영 경험을 설명할 때 중요하다.

---

## AI Engineering 핵심

```text
Embedding

Vector Search

BM25

Hybrid Search

RRF

Reranker

Recall@K

nDCG
```

단순히:

```text
RAG를 만들었다.
```

보다:

```text
Retriever 구조를 설계하고

검색 성능을 측정하고

Latency와 Recall의 Trade-off를 비교했다.
```

라고 말할 수 있어야 한다.

---

# 6주 후 목표

6주 후에는 다음과 같은 질문을 받았을 때:

```text
"트래픽이 갑자기 10배 증가하면 어떻게 하시겠습니까?"
```

단순히:

```text
서버를 늘리겠습니다.
```

가 아니라:

```text
우선 Metric과 Trace로 병목을 확인합니다.

API 서버 CPU가 병목이면 Stateless 구조를 유지한 상태에서
Scale Out과 Load Balancer를 사용합니다.

DB가 병목이면 Query Plan과 Index를 먼저 확인하고,
읽기 요청이 많다면 Redis Cache 적용을 검토합니다.

DB Connection은 서버 수가 증가하면서 전체 Connection 수도 증가하므로
Connection Pool 크기도 다시 계산해야 합니다.

비동기로 처리 가능한 작업은 Queue와 Worker로 분리해
API Request Path를 짧게 만들 수 있습니다.
```

정도로 설명할 수 있는 상태가 목표다.

즉 최종적으로 만들어야 하는 사고방식은:

```text
문제 발견
↓
병목 측정
↓
원인 분석
↓
기술 선택
↓
구현
↓
성능 측정
↓
Trade-off 판단
```

이다.

이 수준까지 올라가면 단순히 프레임워크를 사용할 줄 아는 개발자가 아니라 **시스템이 왜 그렇게 설계되는지 설명할 수 있는 백엔드/AI 엔지니어**로 연결된다.