from fastapi import APIRouter
from pydantic import BaseModel

from app.config import settings

router = APIRouter()


class HealthResponse(BaseModel):
    """헬스체크 응답 데이터 모델"""
    status: str
    app_name: str
    version: str


@router.get("/", response_model=HealthResponse)
async def root() -> HealthResponse:
    """루트 엔드포인트: 서비스 기본 생존 여부 확인"""
    return HealthResponse(
        status="ok",
        app_name=settings.app_name,
        version=settings.app_version,
    )


@router.get("/health", response_model=HealthResponse)
async def health_check() -> HealthResponse:
    """헬스체크 엔드포인트: 로드밸런서(Nginx)의 업스트림 상태 확인용"""
    return HealthResponse(
        status="ok",
        app_name=settings.app_name,
        version=settings.app_version,
    )
