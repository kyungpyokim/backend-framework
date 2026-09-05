# FastAPI Backend

`uv` 기반 파이썬 가상환경 및 패키지 관리로 구성된 FastAPI 기본 골격 프로젝트입니다.

## 프로젝트 구조

```text
fastapi/
├── app/
│   ├── __init__.py
│   ├── main.py          # FastAPI app factory, lifespan, CORS, 라우터 등록
│   ├── config.py        # pydantic-settings 기반 환경설정
│   └── routers/
│       ├── __init__.py
│       └── health.py    # / 및 /health 엔드포인트
├── tests/
│   ├── __init__.py
│   ├── conftest.py      # httpx AsyncClient fixture
│   └── test_health.py   # 헬스체크 및 엔드포인트 비동기 테스트
├── .env.example
├── .env
├── pyproject.toml
└── README.md
```

## 개발 환경 실행 가이드

### 1. 의존성 설치 및 가상환경 동기화

```bash
uv sync
```

### 2. 개발 서버 구동

```bash
uv run uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- API 문서(Swagger UI): `http://localhost:8000/docs`
- 대안 문서(ReDoc): `http://localhost:8000/redoc`
- 헬스체크: `http://localhost:8000/health`

### 3. 테스트 실행

```bash
uv run pytest -v
```
