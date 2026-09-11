from fastapi import APIRouter

from app.config import config

router = APIRouter()


@router.get("/health")
async def health():
    return {"service": config.service_name, "version": config.service_version, "status": "ok"}
