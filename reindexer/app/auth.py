"""FastAPI dependency for validating CMR INGEST_MANAGEMENT_ACL update permission."""
import logging
from typing import Optional

import httpx
from fastapi import Depends, Header, HTTPException

from app.config import config

logger = logging.getLogger(__name__)


def _extract_token(
    authorization: Optional[str] = Header(None, alias="Authorization"),
    echo_token: Optional[str] = Header(None, alias="Echo-Token"),
) -> str:
    token = authorization or echo_token
    if not token:
        raise HTTPException(status_code=401, detail="Authorization token required")
    if token.lower().startswith("bearer "):
        token = token[7:]
    return token


def _get_sids(token: str) -> list:
    """Return the current user's SIDs (group concept IDs + 'registered'/'guest')."""
    try:
        r = httpx.post(
            f"{config.acl_base_url}/current-sids",
            json={"user-token": token},
            timeout=10.0,
        )
    except Exception as exc:
        logger.error({"event": "sids_check_error", "error": str(exc)})
        raise HTTPException(status_code=503, detail="ACL service unavailable")

    if r.status_code == 401:
        raise HTTPException(status_code=401, detail="Invalid or expired token")
    if r.status_code != 200:
        logger.warning({"event": "sids_check_unexpected_status", "status": r.status_code})
        raise HTTPException(status_code=503, detail="ACL service error")

    return r.json()


def _get_ingest_mgmt_acls() -> list:
    """Fetch all INGEST_MANAGEMENT_ACL system ACLs with full ACL detail."""
    try:
        r = httpx.get(
            f"{config.acl_base_url}/acls",
            params={"target": "INGEST_MANAGEMENT_ACL", "include_full_acl": "true"},
            headers={"Authorization": config.echo_system_token},
            timeout=10.0,
        )
    except Exception as exc:
        logger.error({"event": "acl_fetch_error", "error": str(exc)})
        raise HTTPException(status_code=503, detail="ACL service unavailable")

    if r.status_code != 200:
        logger.warning({"event": "acl_fetch_unexpected_status", "status": r.status_code})
        raise HTTPException(status_code=503, detail="ACL service error")

    return r.json().get("items", [])


def _sid_has_update(sids: list, acls: list) -> bool:
    """Return True if any ACL grants 'update' to any of the user's SIDs."""
    sid_set = set(sids)
    for item in acls:
        for gp in item.get("acl", {}).get("group_permissions", []):
            if "update" not in gp.get("permissions", []):
                continue
            if gp.get("user_type") in sid_set or gp.get("group_id") in sid_set:
                return True
    return False


def require_auth(token: str = Depends(_extract_token)) -> str:
    """Check that the token has INGEST_MANAGEMENT_ACL update permission."""
    sids = _get_sids(token)
    acls = _get_ingest_mgmt_acls()

    if not _sid_has_update(sids, acls):
        raise HTTPException(
            status_code=403,
            detail="Insufficient permissions: INGEST_MANAGEMENT_ACL update required",
        )

    return token
