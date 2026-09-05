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
    # 1. Startup: Initialize Redis pool and PubSub listener
    redis = None
    try:
        redis = await init_redis_pool()
        await ws_manager.start_listener(redis)
    except Exception as e:
        print(f"[{settings.node_id}] Warning: Redis initialization skipped or failed: {e}")

    yield

    # 2. Shutdown: Close PubSub listener and Redis pool
    try:
        await ws_manager.stop_listener()
        await close_redis_pool()
    except Exception as e:
        print(f"[{settings.node_id}] Warning during cleanup: {e}")


def create_app() -> FastAPI:
    app = FastAPI(
        title=f"{settings.app_name} [{settings.node_id}]",
        version=settings.app_version,
        debug=settings.debug,
        lifespan=lifespan,
    )

    # Middlewares
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.allowed_origins,
        allow_credentials=settings.allow_credentials,
        allow_methods=settings.allowed_methods,
        allow_headers=settings.allowed_headers,
    )

    # Core Health Router
    app.include_router(health.router)

    # Cluster Node Info
    @app.get("/cluster/info", tags=["Cluster"])
    async def cluster_info():
        return {
            "node_id": settings.node_id,
            "app_name": settings.app_name,
            "version": settings.app_version,
            "redis_url": settings.redis_url,
        }

    # 4 Distributed System Cases
    app.include_router(case1_router)
    app.include_router(case2_router)
    app.include_router(case3_router)
    app.include_router(case4_router)

    return app


app = create_app()
