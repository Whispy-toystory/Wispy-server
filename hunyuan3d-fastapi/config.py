import os
from pathlib import Path
from typing import Optional


class Settings:
    API_KEY: str = os.getenv("HUNYUAN3D_API_KEY", "default-secret-key")

    SPRING_BOOT_BASE_URL: str = os.getenv("SPRING_BOOT_BASE_URL", "http://localhost:8080")

    BASE_DIR: Path = Path(__file__).parent
    TEMP_DIR: Path = BASE_DIR / "temp"
    OUTPUT_DIR: Path = BASE_DIR / "output"
    MODELS_DIR: Path = BASE_DIR / "models"
    HUNYUAN_DIR: Path = BASE_DIR / "Hunyuan3D-2"

    MAX_CONCURRENT_TASKS: int = int(os.getenv("MAX_CONCURRENT_TASKS", "3"))
    MAX_FILE_SIZE_MB: int = 10
    SUPPORTED_FORMATS: list = ["image/jpeg", "image/jpg", "image/png"]

    MODEL_DEVICE: str = "cuda" if os.getenv("CUDA_VISIBLE_DEVICES") else "cpu"
    MODEL_DTYPE: str = "float16"

    ENABLE_MEMORY_EFFICIENT: bool = True
    ENABLE_ATTENTION_SLICING: bool = True

    TEMP_CLEANUP_HOURS: int = int(os.getenv("TEMP_CLEANUP_HOURS", "24"))
    OUTPUT_RETENTION_DAYS: int = 7
    AUTO_CLEANUP_ENABLED: bool = True

    LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO")
    LOG_FILE: Optional[str] = os.getenv("LOG_FILE")

    def __post_init__(self):
        for directory in [self.TEMP_DIR, self.OUTPUT_DIR, self.MODELS_DIR]:
            directory.mkdir(exist_ok=True)


settings = Settings()