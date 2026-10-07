"""DynamoDB job tracking store for cmr-reindexer."""
import decimal
import functools
import logging
from datetime import datetime, timedelta, timezone
from typing import Optional

import boto3
from botocore.exceptions import ClientError

from app.config import config

logger = logging.getLogger(__name__)


def _now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _expiry() -> int:
    """Set only when a job finishes, so a long-running job never expires."""
    return int((datetime.now(timezone.utc) + timedelta(days=30)).timestamp())


@functools.lru_cache(maxsize=1)
def _dynamo_table():
    dynamodb = boto3.resource(
        "dynamodb",
        region_name=config.aws_region,
        endpoint_url=config.dynamodb_endpoint_url,
        aws_access_key_id=config.aws_access_key_id,
        aws_secret_access_key=config.aws_secret_access_key,
    )
    return dynamodb.Table(config.dynamodb_table_name)


def _deserialize(value):
    """Decimal -> int/float and set -> list, recursively."""
    if isinstance(value, decimal.Decimal):
        return int(value) if value == int(value) else float(value)
    if isinstance(value, set):
        return list(value)
    if isinstance(value, dict):
        return {k: _deserialize(v) for k, v in value.items()}
    if isinstance(value, list):
        return [_deserialize(v) for v in value]
    return value


class JobStore:
    def _table(self):
        return _dynamo_table()

    def create_job(
        self,
        job_id: str,
        concept_type: str,
        *,
        providers: Optional[list[str]] = None,
        collection_id: Optional[str] = None,
        concept_id: Optional[str] = None,
        after: Optional[str] = None,
        before: Optional[str] = None,
        source_url: Optional[str] = None,
    ) -> None:
        now = _now_iso()
        item: dict = {
            "job_id": job_id,
            "status": "running",
            "concept_type": concept_type,
            "last_heartbeat": now,
            "started_at": now,
            "total_dispatched": 0,
        }
        if providers is not None:
            item["providers_requested"] = providers  # a list: providers are scanned in this order
        if collection_id is not None:
            item["collection_id"] = collection_id
        if concept_id is not None:
            item["concept_id"] = concept_id
        if after is not None:
            item["after"] = after
        if before is not None:
            item["before"] = before
        if source_url is not None:
            item["source_url"] = source_url
        self._table().put_item(Item=item)
        logger.info({"event": "job_created", "job_id": job_id, "concept_type": concept_type})

    def update_heartbeat(self, job_id: str) -> None:
        self._table().update_item(
            Key={"job_id": job_id},
            UpdateExpression="SET last_heartbeat = :ts",
            ExpressionAttributeValues={":ts": _now_iso()},
        )

    def update_dispatched(self, job_id: str, count: int) -> None:
        self._table().update_item(
            Key={"job_id": job_id},
            UpdateExpression="ADD total_dispatched :n SET last_heartbeat = :ts",
            ExpressionAttributeValues={":n": count, ":ts": _now_iso()},
        )

    def update_scan_cursor(self, job_id: str, cursor, provider_id: Optional[str] = None) -> None:
        sets, values = ["scan_cursor = :c", "last_heartbeat = :ts"], {":c": cursor, ":ts": _now_iso()}
        if provider_id is not None:
            sets.append("scan_provider = :p")
            values[":p"] = provider_id
        self._table().update_item(
            Key={"job_id": job_id}, UpdateExpression="SET " + ", ".join(sets), ExpressionAttributeValues=values,
        )

    def finish_provider(self, job_id: str, provider_id: str) -> None:
        """Mark a provider done and clear its cursor in one write, so a restart can't
        apply it to the next provider."""
        self._table().update_item(
            Key={"job_id": job_id},
            UpdateExpression="ADD providers_done :p SET last_heartbeat = :ts REMOVE scan_cursor, scan_provider",
            ExpressionAttributeValues={":p": {provider_id}, ":ts": _now_iso()},
        )

    def mark_job(self, job_id: str, status: str) -> bool:
        """False (no-op) if the job already finished, so work still running after a cancel
        can't resurrect it."""
        now = _now_iso()
        terminal = status in ("completed", "failed", "cancelled")
        update_expr = "SET #st = :s, last_heartbeat = :ts"
        names = {"#st": "status"}
        values = {":s": status, ":ts": now, ":completed": "completed", ":failed": "failed", ":cancelled": "cancelled"}
        if terminal:
            update_expr += ", completed_at = :ts, #ttl = :ttl"
            names["#ttl"] = "ttl"
            values[":ttl"] = _expiry()
        try:
            self._table().update_item(
                Key={"job_id": job_id},
                UpdateExpression=update_expr,
                ConditionExpression="#st <> :completed AND #st <> :failed AND #st <> :cancelled",
                ExpressionAttributeNames=names,
                ExpressionAttributeValues=values,
            )
            logger.info({"event": "job_status_updated", "job_id": job_id, "status": status})
            return True
        except ClientError as e:
            if e.response["Error"]["Code"] == "ConditionalCheckFailedException":
                logger.info({
                    "event": "job_status_update_skipped",
                    "job_id": job_id,
                    "status": status,
                    "reason": "already in terminal status",
                })
                return False
            raise

    def get_job(self, job_id: str) -> Optional[dict]:
        resp = self._table().get_item(Key={"job_id": job_id})
        item = resp.get("Item")
        return _deserialize(item) if item is not None else None

    def _scan_all(self, **kwargs) -> list:
        items = []
        while True:
            resp = self._table().scan(**kwargs)
            items.extend(resp.get("Items", []))
            last = resp.get("LastEvaluatedKey")
            if not last:
                break
            kwargs["ExclusiveStartKey"] = last
        return items

    def list_jobs(self, status_filter: Optional[str] = None, limit: int = 50) -> list:
        """Up to `limit` jobs, unsorted, reading pages only until `limit` are found."""
        table = self._table()
        kwargs: dict = {}
        if status_filter:
            kwargs["FilterExpression"] = "#st = :s"
            kwargs["ExpressionAttributeNames"] = {"#st": "status"}
            kwargs["ExpressionAttributeValues"] = {":s": status_filter}

        items: list = []
        while len(items) < limit:
            resp = table.scan(**kwargs)
            items.extend(resp.get("Items", []))
            if "LastEvaluatedKey" not in resp:
                break
            kwargs["ExclusiveStartKey"] = resp["LastEvaluatedKey"]
        return [_deserialize(item) for item in items[:limit]]

    def find_cancelled_jobs(self) -> list:
        items = self._scan_all(
            FilterExpression="#st = :cancelled",
            ExpressionAttributeNames={"#st": "status"},
            ExpressionAttributeValues={":cancelled": "cancelled"},
        )
        return [_deserialize(item) for item in items]

    def find_lapsed_jobs(self, lease_minutes: int) -> list:
        cutoff = (datetime.now(timezone.utc) - timedelta(minutes=lease_minutes)).strftime(
            "%Y-%m-%dT%H:%M:%SZ"
        )
        items = self._scan_all(
            FilterExpression="#st = :running AND last_heartbeat < :cutoff",
            ExpressionAttributeNames={"#st": "status"},
            ExpressionAttributeValues={":running": "running", ":cutoff": cutoff},
        )
        return [_deserialize(item) for item in items]

    def try_cancel_job(self, job_id: str) -> bool:
        """False if the job already finished."""
        try:
            self._table().update_item(
                Key={"job_id": job_id},
                UpdateExpression="SET #st = :cancelled, completed_at = :now, last_heartbeat = :now, #ttl = :ttl",
                ConditionExpression="#st <> :completed AND #st <> :failed AND #st <> :cancelled",
                ExpressionAttributeNames={"#st": "status", "#ttl": "ttl"},
                ExpressionAttributeValues={
                    ":cancelled": "cancelled",
                    ":completed": "completed",
                    ":failed": "failed",
                    ":now": _now_iso(),
                    ":ttl": _expiry(),
                },
            )
            logger.info({"event": "job_cancelled", "job_id": job_id})
            return True
        except ClientError as e:
            if e.response["Error"]["Code"] == "ConditionalCheckFailedException":
                return False
            raise

    def claim_lapsed_job(self, job_id: str, last_heartbeat: str) -> bool:
        """Bump the heartbeat only if it is still the one we saw; False if another task
        claimed the job first."""
        try:
            self._table().update_item(
                Key={"job_id": job_id},
                UpdateExpression="SET last_heartbeat = :now",
                ConditionExpression="last_heartbeat = :expected",
                ExpressionAttributeValues={
                    ":now": _now_iso(),
                    ":expected": last_heartbeat,
                },
            )
            return True
        except ClientError as e:
            if e.response["Error"]["Code"] == "ConditionalCheckFailedException":
                return False
            raise


job_store = JobStore()

