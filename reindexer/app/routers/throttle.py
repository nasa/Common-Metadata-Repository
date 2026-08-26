import logging

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from app.auth import require_auth
from app.throttler.worker import throttler

router = APIRouter()
logger = logging.getLogger(__name__)


class ThrottleRequest(BaseModel):
    rate_per_minute: int


@router.get("/throttle")
async def get_throttle():
    return {"rate_per_minute": throttler.get_rate()}


@router.put("/throttle")
async def set_throttle(body: ThrottleRequest, _token: str = Depends(require_auth)):
    if body.rate_per_minute <= 0:
        raise HTTPException(status_code=400, detail="rate_per_minute must be a positive integer")
    throttler.set_rate(body.rate_per_minute)
    logger.info({"event": "throttle_rate_updated", "rate_per_minute": body.rate_per_minute})
    return {"rate_per_minute": body.rate_per_minute}
