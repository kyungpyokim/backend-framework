from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.core.redis import init_redis_pool, close_redis_pool
from app.cases.case1_lock.router import router as case1_router
from app.cases.case2_queue.router import router as case2_router
from app.cases.case3_websocket.router import router as case3_router
from app.cases.case3_websocket.pubsub_manager import manager as ws_manager
from app.cases.case4_resilience.router import router as case4_router
from app.routers import health


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """
    FastAPI 비동기 수명 주기(Lifespan) 컨텍스트 매니저.
    - 시작(Startup): Redis 커넥션 풀을 초기화하고, 클러스터 간 웹소켓 Pub/Sub 백그라운드 리스너를 실행합니다.
    - 종료(Shutdown): 웹소켓 리스너를 취소하고 Redis 커넥션 풀을 안전하게 닫습니다.
    """
    # 1. Startup: Redis 풀 초기화 및 분산 웹소켓 Pub/Sub 리스너 시작
    redis = None
    try:
        redis = await init_redis_pool()
        await ws_manager.start_listener(redis)
    except Exception as e:
        print(f"[{settings.node_id}] Warning: Redis initialization skipped or failed: {e}")

    yield

    # 2. Shutdown: Pub/Sub 리스너 정지 및 Redis 풀 정리
    try:
        await ws_manager.stop_listener()
        await close_redis_pool()
    except Exception as e:
        print(f"[{settings.node_id}] Warning during cleanup: {e}")


def create_app() -> FastAPI:
    """
    FastAPI 애플리케이션 팩토리 함수.
    미들웨어 설정, 헬스체크 라우터, 클러스터 노드 정보 및 4가지 분산 패턴 라우터를 등록합니다.
    """
    app = FastAPI(
        title=f"{settings.app_name} [{settings.node_id}]",
        version=settings.app_version,
        debug=settings.debug,
        lifespan=lifespan,
    )

    # Middlewares: CORS 설정 등록
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.allowed_origins,
        allow_credentials=settings.allow_credentials,
        allow_methods=settings.allowed_methods,
        allow_headers=settings.allowed_headers,
    )

    # Core Health Router 등록 (기본 상태 확인)
    app.include_router(health.router)

    # Cluster Node Info: 로드밸런서(Nginx) 뒤에서 요청을 처리한 특정 노드 식별 엔드포인트
    @app.get("/cluster/info", tags=["Cluster"])
    async def cluster_info():
        """로드 밸런싱 환경에서 현재 요청을 처리한 인스턴스의 노드 ID 및 설정을 반환합니다."""
        return {
            "node_id": settings.node_id,
            "app_name": settings.app_name,
            "version": settings.app_version,
            "redis_url": settings.redis_url,
        }

    # 4대 분산 시스템 패턴 라우터 등록
    app.include_router(case1_router)
    app.include_router(case2_router)
    app.include_router(case3_router)
    app.include_router(case4_router)

    return app


app = create_app()
