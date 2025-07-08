import uuid
import asyncio
import os
import shutil
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional
import uvicorn
from fastapi import FastAPI, File, UploadFile, HTTPException, BackgroundTasks, Header
from fastapi.responses import FileResponse
from pydantic import BaseModel
import logging
from PIL import Image
import tempfile

try:
    from hy3dgen.shapegen import Hunyuan3DDiTFlowMatchingPipeline
    from hy3dgen.texgen import Hunyuan3DPaintPipeline

    HUNYUAN_AVAILABLE = True
    print("\u2705 Hunyuan3D 모델이 성공적으로 로드되었습니다.")
except ImportError as e:
    print(f"\u26a0\ufe0f  Hunyuan3D 모델을 로드할 수 없습니다: {e}")
    print("시뮬레이션 모드로 실행됩니다.")
    HUNYUAN_AVAILABLE = False

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Hunyuan3D GLB Generation API",
    description="3D Avatar generation using Hunyuan3D-2 model",
    version="1.0.0"
)

TEMP_DIR = Path("./temp")
OUTPUT_DIR = Path("./output")
MODELS_DIR = Path("./models")
TASKS: Dict[str, dict] = {}

TEMP_DIR.mkdir(exist_ok=True)
OUTPUT_DIR.mkdir(exist_ok=True)
MODELS_DIR.mkdir(exist_ok=True)

shape_pipeline = None
texture_pipeline = None


async def initialize_models():
    global shape_pipeline, texture_pipeline

    if not HUNYUAN_AVAILABLE:
        logger.warning("Hunyuan3D 모델을 사용할 수 없습니다. 시뮬레이션 모드로 실행됩니다.")
        return

    try:
        logger.info("Hunyuan3D 모델 파이프라인을 초기화하는 중...")
        shape_pipeline = Hunyuan3DDiTFlowMatchingPipeline.from_pretrained('tencent/Hunyuan3D-2')
        texture_pipeline = Hunyuan3DPaintPipeline.from_pretrained('tencent/Hunyuan3D-2')
        logger.info("\ud83c\udf89 모든 Hunyuan3D 파이프라인이 성공적으로 로드되었습니다!")
    except Exception as e:
        logger.error(f"\u274c 모델 초기화 실패: {str(e)}")
        raise


class TaskStatus(BaseModel):
    task_id: str
    status: str
    progress: int
    result_url: Optional[str] = None
    error: Optional[str] = None
    created_at: datetime
    updated_at: datetime


@app.on_event("startup")
async def startup_event():
    await initialize_models()


@app.post("/api/v1/generate-avatar")
async def generate_avatar(
    background_tasks: BackgroundTasks,
    front: UploadFile = File(...),
    left: UploadFile = File(...),
    back: UploadFile = File(...),
    output_format: str = "glb",
    quality: str = "high",
    texture_resolution: int = 1024,
    mesh_quality: str = "medium",
    callback_url: Optional[str] = None,
    authorization: Optional[str] = Header(None),
    x_job_id: Optional[str] = Header(None)
):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Invalid authorization")

    task_id = str(uuid.uuid4())
    logger.info(f"작업 생성: {task_id}")

    await validate_images([front, left, back])
    primary_image = front

    TASKS[task_id] = {
        "task_id": task_id,
        "status": "pending",
        "progress": 0,
        "result_url": None,
        "error": None,
        "created_at": datetime.now(),
        "updated_at": datetime.now(),
        "spring_job_id": x_job_id,
        "callback_url": callback_url
    }

    background_tasks.add_task(
        process_glb_generation,
        task_id,
        primary_image,
        {
            "output_format": output_format,
            "quality": quality,
            "texture_resolution": texture_resolution,
            "mesh_quality": mesh_quality,
            "callback_url": callback_url
        }
    )

    return {
        "task_id": task_id,
        "status": "pending",
        "message": "GLB generation started",
        "estimated_duration_minutes": 30 if HUNYUAN_AVAILABLE else 1
    }


@app.get("/api/v1/task/{task_id}/status")
async def get_task_status(task_id: str, authorization: Optional[str] = Header(None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Invalid authorization")

    if task_id not in TASKS:
        raise HTTPException(status_code=404, detail="Task not found")

    task = TASKS[task_id]

    return {
        "task_id": task_id,
        "status": task["status"],
        "progress": task["progress"],
        "result_url": task["result_url"],
        "error": task["error"],
        "created_at": task["created_at"].isoformat(),
        "updated_at": task["updated_at"].isoformat()
    }


@app.get("/api/v1/download/{task_id}")
async def download_glb_file(task_id: str, authorization: Optional[str] = Header(None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Invalid authorization")

    if task_id not in TASKS:
        raise HTTPException(status_code=404, detail="Task not found")

    task = TASKS[task_id]

    if task["status"] != "completed":
        raise HTTPException(status_code=400, detail="Task not completed")

    file_path = OUTPUT_DIR / f"{task_id}.glb"

    if not file_path.exists():
        raise HTTPException(status_code=404, detail="GLB file not found")

    return FileResponse(
        path=file_path,
        media_type="model/gltf-binary",
        filename=f"avatar_{task_id}.glb"
    )


async def validate_images(images: List[UploadFile]):
    for image in images:
        if image.size and image.size > 10 * 1024 * 1024:
            raise HTTPException(status_code=400, detail=f"이미지 {image.filename}이 너무 큽니다 (최대 10MB)")

        if not image.content_type or not image.content_type.startswith("image/"):
            raise HTTPException(status_code=400, detail=f"잘못된 이미지 형식: {image.filename}")


async def process_glb_generation(task_id: str, primary_image: UploadFile, params: dict):
    try:
        logger.info(f"작업 {task_id} 처리 시작")
        update_task_status(task_id, "processing", 5)

        task_dir = TEMP_DIR / task_id
        task_dir.mkdir(exist_ok=True)

        image_content = await primary_image.read()
        temp_image_path = task_dir / f"input_image.jpg"

        with Image.open(tempfile.BytesIO(image_content)) as img:
            if img.mode != 'RGB':
                img = img.convert('RGB')
            img.save(temp_image_path, 'JPEG', quality=95)

        update_task_status(task_id, "processing", 15)

        output_path = await run_hunyuan3d_model(task_id, str(temp_image_path), params)
        update_task_status(task_id, "processing", 95)

        final_output = OUTPUT_DIR / f"{task_id}.glb"
        shutil.move(output_path, final_output)
        shutil.rmtree(task_dir)

        result_url = f"/api/v1/download/{task_id}"
        update_task_status(task_id, "completed", 100, result_url)

        if params.get("callback_url"):
            await send_completion_callback(task_id, params["callback_url"])

        logger.info(f"GLB 생성 완료: {task_id}")

    except Exception as e:
        logger.error(f"작업 {task_id} 실패: {str(e)}")
        update_task_status(task_id, "failed", None, None, str(e))


async def run_hunyuan3d_model(task_id: str, image_path: str, params: dict) -> str:
    try:
        logger.info(f"Hunyuan3D 실행 중: {task_id}")

        if not HUNYUAN_AVAILABLE:
            return await simulate_model_execution(task_id)

        output_glb_path = TEMP_DIR / task_id / f"{task_id}.glb"

        update_task_status(task_id, "processing", 30)
        mesh = shape_pipeline(image=image_path)[0]
        update_task_status(task_id, "processing", 60)

        mesh_textured = texture_pipeline(mesh, image=image_path)
        update_task_status(task_id, "processing", 85)

        mesh_textured.export(str(output_glb_path))

        return str(output_glb_path)

    except Exception as e:
        logger.error(f"Hunyuan3D 모델 실행 오류: {str(e)}")
        raise


async def simulate_model_execution(task_id: str) -> str:
    logger.info(f"시뮬레이션 모드 실행: {task_id}")
    for progress in range(20, 95, 10):
        await asyncio.sleep(1)
        update_task_status(task_id, "processing", progress)

    dummy_glb_path = TEMP_DIR / task_id / f"{task_id}.glb"
    dummy_glb_path.parent.mkdir(exist_ok=True)
    with open(dummy_glb_path, "wb") as f:
        f.write(b"glTF\x02\x00\x00\x00" + b"\x00" * 1000)

    return str(dummy_glb_path)


def update_task_status(task_id: str, status: str, progress: Optional[int],
                       result_url: Optional[str] = None, error: Optional[str] = None):
    if task_id in TASKS:
        TASKS[task_id].update({
            "status": status,
            "updated_at": datetime.now()
        })
        if progress is not None:
            TASKS[task_id]["progress"] = progress
        if result_url:
            TASKS[task_id]["result_url"] = result_url
        if error:
            TASKS[task_id]["error"] = error


async def send_completion_callback(task_id: str, callback_url: str):
    try:
        import httpx
        async with httpx.AsyncClient() as client:
            await client.post(
                callback_url,
                json={
                    "task_id": task_id,
                    "status": "completed",
                    "result_url": f"/api/v1/download/{task_id}"
                },
                timeout=30
            )
        logger.info(f"콜백 전송 완료: {task_id} → {callback_url}")
    except Exception as e:
        logger.error(f"콜백 전송 실패: {str(e)}")


@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "service": "hunyuan3d-api",
        "hunyuan_available": HUNYUAN_AVAILABLE,
        "active_tasks": len([t for t in TASKS.values() if t["status"] in ["pending", "processing"]])
    }


@app.get("/api/v1/tasks")
async def list_all_tasks(authorization: Optional[str] = Header(None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Invalid authorization")
    return {"tasks": list(TASKS.values())}


if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=False,
        log_level="info"
    )
