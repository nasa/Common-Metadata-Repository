"""Unit tests for the require_auth FastAPI dependency.

Run with:
    cd reindexer
    PYTHONPATH=. python -m pytest tests/test_auth.py -v
"""
from unittest.mock import MagicMock, call, patch

import pytest
from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient

from app.auth import require_auth

_app = FastAPI()


@_app.post("/protected")
async def protected(token: str = Depends(require_auth)):
    return {"token": token}


client = TestClient(_app, raise_server_exceptions=False)

# ---------------------------------------------------------------------------
# Mock helpers
# ---------------------------------------------------------------------------

_REGISTERED_SID = "registered"
_GROUP_SID = "AG12345-CMR"

_ACL_WITH_UPDATE = {
    "concept_id": "ACL-001",
    "acl": {
        "group_permissions": [
            {"user_type": "registered", "permissions": ["read", "update"]}
        ],
        "system_identity": {"target": "INGEST_MANAGEMENT_ACL"},
    },
}

_ACL_READ_ONLY = {
    "concept_id": "ACL-002",
    "acl": {
        "group_permissions": [
            {"user_type": "registered", "permissions": ["read"]}
        ],
        "system_identity": {"target": "INGEST_MANAGEMENT_ACL"},
    },
}

_ACL_GROUP_UPDATE = {
    "concept_id": "ACL-003",
    "acl": {
        "group_permissions": [
            {"group_id": _GROUP_SID, "permissions": ["update"]}
        ],
        "system_identity": {"target": "INGEST_MANAGEMENT_ACL"},
    },
}


def _sids_resp(sids):
    r = MagicMock()
    r.status_code = 200
    r.json.return_value = sids
    return r


def _acls_resp(items):
    r = MagicMock()
    r.status_code = 200
    r.json.return_value = {"items": items, "hits": len(items)}
    return r


def _http_401():
    r = MagicMock()
    r.status_code = 401
    return r


def _post_get(sids, acl_items):
    """Return (mock_post, mock_get) pre-configured for the two-call auth flow."""
    return _sids_resp(sids), _acls_resp(acl_items)


# ---------------------------------------------------------------------------
# 401 — no token
# ---------------------------------------------------------------------------

def test_missing_token_returns_401():
    r = client.post("/protected")
    assert r.status_code == 401


# ---------------------------------------------------------------------------
# 401 — current-sids rejects the token
# ---------------------------------------------------------------------------

def test_invalid_token_returns_401():
    with patch("app.auth.httpx.post", return_value=_http_401()):
        r = client.post("/protected", headers={"Authorization": "bad-token"})
    assert r.status_code == 401


# ---------------------------------------------------------------------------
# 503 — ACL service unreachable on current-sids call
# ---------------------------------------------------------------------------

def test_sids_service_unreachable_returns_503():
    with patch("app.auth.httpx.post", side_effect=Exception("connection refused")):
        r = client.post("/protected", headers={"Authorization": "any-token"})
    assert r.status_code == 503


# ---------------------------------------------------------------------------
# 503 — ACL service unreachable on acls fetch
# ---------------------------------------------------------------------------

def test_acl_fetch_unreachable_returns_503():
    mock_post = _sids_resp([_REGISTERED_SID])
    with patch("app.auth.httpx.post", return_value=mock_post), \
         patch("app.auth.httpx.get", side_effect=Exception("connection refused")):
        r = client.post("/protected", headers={"Authorization": "any-token"})
    assert r.status_code == 503


# ---------------------------------------------------------------------------
# 403 — valid token but no update permission in any ACL
# ---------------------------------------------------------------------------

def test_no_update_permission_returns_403():
    mock_post, mock_get = _post_get([_REGISTERED_SID], [_ACL_READ_ONLY])
    with patch("app.auth.httpx.post", return_value=mock_post), \
         patch("app.auth.httpx.get", return_value=mock_get):
        r = client.post("/protected", headers={"Authorization": "read-only-token"})
    assert r.status_code == 403


def test_empty_acls_returns_403():
    mock_post, mock_get = _post_get([_REGISTERED_SID], [])
    with patch("app.auth.httpx.post", return_value=mock_post), \
         patch("app.auth.httpx.get", return_value=mock_get):
        r = client.post("/protected", headers={"Authorization": "no-acl-token"})
    assert r.status_code == 403


# ---------------------------------------------------------------------------
# 200 — authorized via user_type SID match
# ---------------------------------------------------------------------------

def test_registered_sid_with_update_permission_passes():
    mock_post, mock_get = _post_get([_REGISTERED_SID], [_ACL_WITH_UPDATE])
    with patch("app.auth.httpx.post", return_value=mock_post), \
         patch("app.auth.httpx.get", return_value=mock_get):
        r = client.post("/protected", headers={"Authorization": "good-token"})
    assert r.status_code == 200
    assert r.json()["token"] == "good-token"


# ---------------------------------------------------------------------------
# 200 — authorized via group_id SID match
# ---------------------------------------------------------------------------

def test_group_sid_with_update_permission_passes():
    mock_post, mock_get = _post_get([_GROUP_SID], [_ACL_GROUP_UPDATE])
    with patch("app.auth.httpx.post", return_value=mock_post), \
         patch("app.auth.httpx.get", return_value=mock_get):
        r = client.post("/protected", headers={"Authorization": "group-token"})
    assert r.status_code == 200


# ---------------------------------------------------------------------------
# Bearer prefix is stripped before forwarding to current-sids
# ---------------------------------------------------------------------------

def test_bearer_prefix_stripped():
    mock_post, mock_get = _post_get([_REGISTERED_SID], [_ACL_WITH_UPDATE])
    with patch("app.auth.httpx.post", return_value=mock_post) as mock_p, \
         patch("app.auth.httpx.get", return_value=mock_get):
        r = client.post("/protected", headers={"Authorization": "Bearer my-secret-token"})
    assert r.status_code == 200
    assert r.json()["token"] == "my-secret-token"
    body = mock_p.call_args[1]["json"]
    assert body["user-token"] == "my-secret-token"


# ---------------------------------------------------------------------------
# Echo-Token header is accepted
# ---------------------------------------------------------------------------

def test_echo_token_header_passes():
    mock_post, mock_get = _post_get([_REGISTERED_SID], [_ACL_WITH_UPDATE])
    with patch("app.auth.httpx.post", return_value=mock_post), \
         patch("app.auth.httpx.get", return_value=mock_get):
        r = client.post("/protected", headers={"Echo-Token": "echo-token-value"})
    assert r.status_code == 200


# ---------------------------------------------------------------------------
# current-sids is called via POST with token in body (not URL)
# ---------------------------------------------------------------------------

def test_token_not_in_url():
    mock_post, mock_get = _post_get([_REGISTERED_SID], [_ACL_WITH_UPDATE])
    with patch("app.auth.httpx.post", return_value=mock_post) as mock_p, \
         patch("app.auth.httpx.get", return_value=mock_get):
        client.post("/protected", headers={"Authorization": "secret-token"})
    url = mock_p.call_args[0][0]
    assert "secret-token" not in url
    assert mock_p.call_args[1]["json"] == {"user-token": "secret-token"}


# ---------------------------------------------------------------------------
# ACL fetch uses system token, not user token
# ---------------------------------------------------------------------------

def test_acl_fetch_uses_system_token():
    from app.config import config
    mock_post, mock_get = _post_get([_REGISTERED_SID], [_ACL_WITH_UPDATE])
    with patch("app.auth.httpx.post", return_value=mock_post), \
         patch("app.auth.httpx.get", return_value=mock_get) as mock_g:
        client.post("/protected", headers={"Authorization": "user-token"})
    headers = mock_g.call_args[1]["headers"]
    assert headers["Authorization"] == config.echo_system_token
    assert headers["Authorization"] != "user-token"


# ---------------------------------------------------------------------------
# ACL query uses correct params (no invalid 'permission' param)
# ---------------------------------------------------------------------------

def test_acl_query_params():
    mock_post, mock_get = _post_get([_REGISTERED_SID], [_ACL_WITH_UPDATE])
    with patch("app.auth.httpx.post", return_value=mock_post), \
         patch("app.auth.httpx.get", return_value=mock_get) as mock_g:
        client.post("/protected", headers={"Authorization": "tok"})
    params = mock_g.call_args[1]["params"]
    assert params["target"] == "INGEST_MANAGEMENT_ACL"
    assert params["include_full_acl"] == "true"
    assert "permission" not in params
