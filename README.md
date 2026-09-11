# Backend Framework Playground

다양한 백엔드 프레임워크 및 분산 시스템 아키텍처를 학습, 실험, 비교하는 플레이그라운드 저장소입니다.

## 📖 학습 문서 가이드

- [01. 시스템 아키텍처 개요 (`docs/01-architecture.md`)](./docs/01-architecture.md): 수평 확장, 무상태 API, 클러스터 토폴로지
- [02. 분산 서버 4대 패턴 상세 (`docs/02-distributed-patterns.md`)](./docs/02-distributed-patterns.md): 분산 락, 작업 큐, 웹소켓 Pub/Sub, 서킷 브레이커
- [03. 단계별 실습 가이드 (`docs/03-hands-on-guide.md`)](./docs/03-hands-on-guide.md): 1초 터미널 데모, Docker 클러스터 실습, curl 시나리오
- [04. 3대 프레임워크 비교 분석 (`docs/04-framework-comparison.md`)](./docs/04-framework-comparison.md): FastAPI vs NestJS vs Spring Boot 1:1 비교
- [05. Production Agent Engineering 12주 학습 계획서 (`docs/05-production-agent-engineering.md`)](./docs/05-production-agent-engineering.md): 프레임워크 종속 탈피, 70/20/10 원칙, Agent Loop/Runtime/Evals/MCP/Security 실습 로드맵

## 📂 포함된 프로젝트 (3대 백엔드 프레임워크)

| 프레임워크 | 경로 | 주요 기술 스택 | 클러스터 포트 (Nginx) |
|---|---|---|---|
| **FastAPI** | [`./fastapi`](./fastapi) | Python 3.12, `uv`, `redis.asyncio` | `http://localhost:8080` |
| **NestJS** | [`./nestjs`](./nestjs) | TypeScript, Node.js 22, `ioredis`, `@nestjs/websockets` | `http://localhost:8081` |
| **Spring Boot** | [`./spring-boot`](./spring-boot) | Java 21, Spring Boot 3.3, Gradle 8, Spring Data Redis | `http://localhost:8082` |


