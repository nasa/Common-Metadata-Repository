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


def _deserialize(item: dict) -> dict:
    result = {}
    for k, v in item.items():
        if isinstance(v, decimal.Decimal):
            result[k] = int(v) if v == int(v) else float(v)
        elif isinstance(v, set):
            result[k] = list(v)
        else:
            result[k] = v
    return result


class JobStore:
    def _table(self):
        return _dynamo_table()

    def create_job(
        self,
        job_id: str,
        concept_type: str,
        *,
        provider_id: Optional[str] = None,
        collection_id: Optional[str] = None,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> None:
        now = _now_iso()
        ttl = int((datetime.now(timezone.utc) + timedelta(days=30)).timestamp())
        item: dict = {
            "job_id": job_id,
            "status": "running",
            "concept_type": concept_type,
            "last_heartbeat": now,
            "started_at": now,
            "work_items_enqueued": 0,
            "collections_split": 0,
            "total_granules_expected": 0,
            "total_dispatched": 0,
            "ttl": ttl,
        }
        if provider_id is not None:
            item["provider_id"] = provider_id
        if collection_id is not None:
            item["collection_id"] = collection_id
        if after is not None:
            item["after"] = after
        if before is not None:
            item["before"] = before
        self._table().put_item(Item=item)
        logger.info({"event": "job_created", "job_id": job_id, "concept_type": concept_type})

    def update_heartbeat(self, job_id: str) -> None:
        self._table().update_item(
            Key={"job_id": job_id},
            UpdateExpression="SET last_heartbeat = :ts",
            ExpressionAttributeValues={":ts": _now_iso()},
        )

    def update_progress(
        self,
        job_id: str,
        *,
        provider_enqueued: Optional[str] = None,
        providers_to_process: Optional[list] = None,
        work_items_delta: int = 0,
    ) -> None:
        now = _now_iso()
        set_parts = ["last_heartbeat = :ts"]
        add_parts: list = []
        values: dict = {":ts": now}

        if providers_to_process:
            set_parts.append("providers_to_process = :ptp")
            values[":ptp"] = set(providers_to_process)

        if provider_enqueued:
            add_parts.append("providers_enqueued :pe")
            values[":pe"] = {provider_enqueued}

        if work_items_delta > 0:
            add_parts.append("work_items_enqueued :wi")
            values[":wi"] = work_items_delta

        expression = "SET " + ", ".join(set_parts)
        if add_parts:
            expression += " ADD " + ", ".join(add_parts)

        self._table().update_item(
            Key={"job_id": job_id},
            UpdateExpression=expression,
            ExpressionAttributeValues=values,
        )

    def increment_collections_split(self, job_id: str, granule_count: int) -> None:
        self._table().update_item(
            Key={"job_id": job_id},
            UpdateExpression="ADD collections_split :one, total_granules_expected :n SET last_heartbeat = :ts",
            ExpressionAttributeValues={":one": 1, ":n": granule_count, ":ts": _now_iso()},
        )

    def update_dispatched(self, job_id: str, count: int) -> None:
        self._table().update_item(
            Key={"job_id": job_id},
            UpdateExpression="ADD total_dispatched :n SET last_heartbeat = :ts",
            ExpressionAttributeValues={":n": count, ":ts": _now_iso()},
        )

    def mark_job(self, job_id: str, status: str) -> None:
        now = _now_iso()
        terminal = status in ("completed", "failed", "interrupted", "cancelled")
        if terminal:
            self._table().update_item(
                Key={"job_id": job_id},
                UpdateExpression="SET #st = :s, completed_at = :ts, last_heartbeat = :ts",
                ExpressionAttributeNames={"#st": "status"},
                ExpressionAttributeValues={":s": status, ":ts": now},
            )
        else:
            self._table().update_item(
                Key={"job_id": job_id},
                UpdateExpression="SET #st = :s, last_heartbeat = :ts",
                ExpressionAttributeNames={"#st": "status"},
                ExpressionAttributeValues={":s": status, ":ts": now},
            )
        logger.info({"event": "job_status_updated", "job_id": job_id, "status": status})

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
        kwargs: dict = {}
        if status_filter:
            kwargs["FilterExpression"] = "#st = :s"
            kwargs["ExpressionAttributeNames"] = {"#st": "status"}
            kwargs["ExpressionAttributeValues"] = {":s": status_filter}
        items = self._scan_all(**kwargs)
        return [_deserialize(item) for item in items[:limit]]

    def find_stalled_jobs(self, stale_minutes: int = 10) -> list:
        cutoff = (datetime.now(timezone.utc) - timedelta(minutes=stale_minutes)).strftime(
            "%Y-%m-%dT%H:%M:%SZ"
        )
        items = self._scan_all(
            FilterExpression="(#st = :running OR #st = :dispatching) AND last_heartbeat < :cutoff",
            ExpressionAttributeNames={"#st": "status"},
            ExpressionAttributeValues={":running": "running", ":dispatching": "dispatching", ":cutoff": cutoff},
        )
        return [_deserialize(item) for item in items]

    def find_cancelled_jobs(self) -> list:
        items = self._scan_all(
            FilterExpression="#st = :cancelled",
            ExpressionAttributeNames={"#st": "status"},
            ExpressionAttributeValues={":cancelled": "cancelled"},
        )
        return [_deserialize(item) for item in items]

    def try_complete_job(self, job_id: str) -> bool:
        """Conditionally mark a granule job completed if all collections split and all granules dispatched.

        Returns True if the job was marked completed, False if the condition wasn't met.
        """
        try:
            self._table().update_item(
                Key={"job_id": job_id},
                UpdateExpression="SET #st = :completed, completed_at = :now, last_heartbeat = :now",
                ConditionExpression=(
                    "#st = :dispatching"
                    " AND collections_split = work_items_enqueued"
                    " AND total_dispatched >= total_granules_expected"
                ),
                ExpressionAttributeNames={"#st": "status"},
                ExpressionAttributeValues={
                    ":completed": "completed",
                    ":dispatching": "dispatching",
                    ":now": _now_iso(),
                },
            )
            logger.info({"event": "job_completed", "job_id": job_id})
            return True
        except ClientError as e:
            if e.response["Error"]["Code"] == "ConditionalCheckFailedException":
                return False
            raise

    def try_cancel_job(self, job_id: str) -> bool:
        """Atomically cancel a job unless it is already in a terminal status.

        Returns True if cancelled, False if condition failed (already terminal).
        """
        try:
            self._table().update_item(
                Key={"job_id": job_id},
                UpdateExpression="SET #st = :cancelled, completed_at = :now, last_heartbeat = :now",
                ConditionExpression=(
                    "#st <> :completed AND #st <> :failed"
                    " AND #st <> :interrupted AND #st <> :cancelled"
                ),
                ExpressionAttributeNames={"#st": "status"},
                ExpressionAttributeValues={
                    ":cancelled": "cancelled",
                    ":completed": "completed",
                    ":failed": "failed",
                    ":interrupted": "interrupted",
                    ":now": _now_iso(),
                },
            )
            logger.info({"event": "job_cancelled", "job_id": job_id})
            return True
        except ClientError as e:
            if e.response["Error"]["Code"] == "ConditionalCheckFailedException":
                return False
            raise

    def claim_stalled_job(self, job_id: str, last_heartbeat: str) -> bool:
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
