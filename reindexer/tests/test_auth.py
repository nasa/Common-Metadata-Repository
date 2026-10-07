"""Unit tests for the require_auth FastAPI dependency, and which app routes use it."""
from unittest.mock import MagicMock, patch

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


def _acl(permission, identity="system", **grantee):
    grantee = grantee or {"user_type": "registered"}
    target = (
        {"system_identity": {"target": "INGEST_MANAGEMENT_ACL"}} if identity == "system"
        else {"provider_identity": {"provider_id": "PROV1", "target": "INGEST_MANAGEMENT_ACL"}}
    )
    return {"acl": {"group_permissions": [{**grantee, "permissions": ["read", permission]}], **target}}


def _sids_resp(sids):
    r = MagicMock(status_code=200)
    r.json.return_value = sids
    return r


def _acls_resp(items):
    r = MagicMock(status_code=200)
    r.json.return_value = {"items": items, "hits": len(items)}
    return r


def _post(headers, sids, acls):
    with patch("app.auth.httpx.post", return_value=_sids_resp(sids)) as mock_post, \
         patch("app.auth.httpx.get", return_value=_acls_resp(acls)) as mock_get:
        r = client.post("/protected", headers=headers)
    return r, mock_post, mock_get


# ---------------------------------------------------------------------------
# 401 / 503
# ---------------------------------------------------------------------------

def test_missing_token_returns_401():
    assert client.post("/protected").status_code == 401


def test_invalid_token_returns_401():
    with patch("app.auth.httpx.post", return_value=MagicMock(status_code=401)):
        r = client.post("/protected", headers={"Authorization": "bad-token"})
    assert r.status_code == 401


def test_sids_service_unreachable_returns_503():
    with patch("app.auth.httpx.post", side_effect=Exception("connection refused")):
        r = client.post("/protected", headers={"Authorization": "any-token"})
    assert r.status_code == 503


def test_acl_fetch_unreachable_returns_503():
    with patch("app.auth.httpx.post", return_value=_sids_resp([_REGISTERED_SID])), \
         patch("app.auth.httpx.get", side_effect=Exception("connection refused")):
        r = client.post("/protected", headers={"Authorization": "any-token"})
    assert r.status_code == 503


# ---------------------------------------------------------------------------
# 403 / 200
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("acl", [_acl("read"), _acl("update", identity="provider")], ids=["read-only", "provider-level"])
def test_without_system_level_update_returns_403(acl):
    r, _, _ = _post({"Authorization": "some-token"}, [_REGISTERED_SID], [acl])
    assert r.status_code == 403


@pytest.mark.parametrize("sid, acl", [
    (_REGISTERED_SID, _acl("update")),
    (_GROUP_SID, _acl("update", group_id=_GROUP_SID)),
], ids=["user-type", "group"])
def test_system_level_update_passes(sid, acl):
    r, _, _ = _post({"Echo-Token": "good-token"}, [sid], [acl])
    assert (r.status_code, r.json()["token"]) == (200, "good-token")


def test_bearer_prefix_stripped_and_token_sent_in_body_not_url():
    r, mock_post, _ = _post({"Authorization": "Bearer my-secret-token"}, [_REGISTERED_SID], [_acl("update")])
    assert r.json()["token"] == "my-secret-token"
    assert "my-secret-token" not in mock_post.call_args[0][0]
    assert mock_post.call_args[1]["json"] == {"user-token": "my-secret-token"}


def test_acl_request_uses_system_token_and_system_level_acls():
    from app.config import config
    _, _, mock_get = _post({"Authorization": "user-token"}, [_REGISTERED_SID], [_acl("update")])
    assert mock_get.call_args[1]["headers"]["Authorization"] == config.echo_system_token
    params = mock_get.call_args[1]["params"]
    assert (params["target"], params["identity_type"], params["include_full_acl"]) == (
        "INGEST_MANAGEMENT_ACL", "system", "true",
    )


# ---------------------------------------------------------------------------
# Which app routes require auth
# ---------------------------------------------------------------------------

@pytest.fixture
def real_app(monkeypatch):
    from app.main import app
    monkeypatch.setattr(app, "dependency_overrides", {})
    monkeypatch.setattr("app.routers.status.job_store", MagicMock(**{"list_jobs.return_value": []}))
    monkeypatch.setattr("app.routers.throttle.throttler", MagicMock(**{"get_rate.return_value": 600}))
    return TestClient(app, raise_server_exceptions=False)


@pytest.mark.parametrize("method, path, body", [
    ("POST", "/reindexer/reindex/granules", None),
    ("POST", "/reindexer/reindex/granules/provider/PROV", None),
    ("POST", "/reindexer/reindex/granules/providers", {"provider_ids": ["PROV"]}),
    ("POST", "/reindexer/reindex/granules/collection/C1-PROV", None),
    ("POST", "/reindexer/reindex/concept/V1-PROV", None),
    ("POST", "/reindexer/reindex/variables", None),
    ("DELETE", "/reindexer/jobs/job-1", None),
    ("PUT", "/reindexer/throttle", {"rate_per_minute": 100}),
])
def test_mutating_routes_require_a_token(real_app, method, path, body):
    assert real_app.request(method, path, json=body).status_code == 401


@pytest.mark.parametrize("path", ["/reindexer/jobs", "/reindexer/throttle"])
def test_read_routes_are_open(real_app, path):
    assert real_app.get(path).status_code == 200
