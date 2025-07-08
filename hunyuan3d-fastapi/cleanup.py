import asyncio
import time
from pathlib import Path
from datetime import datetime, timedelta


async def cleanup_old_files():

    now = datetime.now()

    temp_cutoff = now - timedelta(hours=settings.TEMP_CLEANUP_HOURS)

    for temp_file in settings.TEMP_DIR.rglob("*"):
        if temp_file.is_file():
            file_time = datetime.fromtimestamp(temp_file.stat().st_mtime)
            if file_time < temp_cutoff:
                temp_file.unlink()
                print(f"🗑️  임시 파일 삭제: {temp_file}")

    output_cutoff = now - timedelta(days=settings.OUTPUT_RETENTION_DAYS)

    for output_file in settings.OUTPUT_DIR.glob("*.glb"):
        if output_file.is_file():
            file_time = datetime.fromtimestamp(output_file.stat().st_mtime)
            if file_time < output_cutoff:
                output_file.unlink()
                print(f"🗑️  출력 파일 삭제: {output_file}")