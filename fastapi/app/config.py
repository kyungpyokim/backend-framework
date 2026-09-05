from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_name: str = "FastAPI Application"
    app_version: str = "0.1.0"
    debug: bool = False

    # CORS
    allowed_origins: list[str] = ["*"]
    allowed_methods: list[str] = ["*"]
    allowed_headers: list[str] = ["*"]
    allow_credentials: bool = True


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
