from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """
    애플리케이션 전역 설정 클래스 (Pydantic Settings 기반).
    .env 파일 및 환경 변수로부터 설정을 자동으로 주입받습니다.
    """
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_name: str = "FastAPI Application"
    app_version: str = "0.1.0"
    debug: bool = False

    # 분산 클러스터 환경에서 현재 인스턴스 노드를 식별하기 위한 ID
    node_id: str = "node-default"

    # 분산 락, 큐, Pub/Sub 등 모든 분산 패턴에서 공유하는 Redis 접속 URL
    redis_url: str = "redis://localhost:6379/0"

    # CORS 설정
    allowed_origins: list[str] = ["*"]
    allowed_methods: list[str] = ["*"]
    allowed_headers: list[str] = ["*"]
    allow_credentials: bool = True


@lru_cache
def get_settings() -> Settings:
    """
    설정 싱글톤 인스턴스를 반환합니다.
    lru_cache를 사용하여 반복적인 파일 읽기를 방지합니다.
    """
    return Settings()


settings = get_settings()
