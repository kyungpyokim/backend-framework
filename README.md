# Backend Framework Playground

다양한 백엔드 프레임워크 및 분산 시스템 아키텍처를 학습, 실험, 비교하는 플레이그라운드 저장소입니다.

## 📖 학습 문서 가이드

- [01. 시스템 아키텍처 개요 (`docs/01-architecture.md`)](./docs/01-architecture.md): 수평 확장, 무상태 API, 클러스터 토폴로지
- [02. 분산 서버 4대 패턴 상세 (`docs/02-distributed-patterns.md`)](./docs/02-distributed-patterns.md): 분산 락, 작업 큐, 웹소켓 Pub/Sub, 서킷 브레이커
- [03. 단계별 실습 가이드 (`docs/03-hands-on-guide.md`)](./docs/03-hands-on-guide.md): 1초 터미널 데모, Docker 클러스터 실습, curl 시나리오

## 📂 포함된 프로젝트

- [FastAPI Distributed Playground](./fastapi):
  - `uv` 기반 Python 3.12 패키지 및 가상환경 관리
  - 4대 분산 아키텍처 케이스 구현
  - Nginx + 다중 API 노드 + Redis + 분산 워커의 Docker Compose 클러스터

