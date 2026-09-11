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


@functools.lru_cache(maxsize=1)
def _checkpoint_table():
    dynamodb = boto3.resource(
        "dynamodb",
        region_name=config.aws_region,
        endpoint_url=config.dynamodb_endpoint_url,
        aws_access_key_id=config.aws_access_key_id,
        aws_secret_access_key=config.aws_secret_access_key,
    )
    return dynamodb.Table(config.dynamodb_checkpoint_table)


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
        concept_id: Optional[str] = None,
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
            "total_dispatched": 0,
            "ttl": ttl,
        }
        if provider_id is not None:
            item["provider_id"] = provider_id
        if collection_id is not None:
            item["collection_id"] = collection_id
        if concept_id is not None:
            item["concept_id"] = concept_id
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

    def increment_collections_split(self, job_id: str) -> None:
        self._table().update_item(
            Key={"job_id": job_id},
            UpdateExpression="ADD collections_split :one SET last_heartbeat = :ts",
            ExpressionAttributeValues={":one": 1, ":ts": _now_iso()},
        )

    def update_dispatched(self, job_id: str, count: int) -> None:
        self._table().update_item(
            Key={"job_id": job_id},
            UpdateExpression="ADD total_dispatched :n SET last_heartbeat = :ts",
            ExpressionAttributeValues={":n": count, ":ts": _now_iso()},
        )

    def mark_job(self, job_id: str, status: str) -> bool:
        """Set job status.  Returns False (no-op) if the job is already in a terminal status.

        Terminal statuses (completed, failed, interrupted, cancelled) cannot be overwritten.
        This prevents a background task — still running after the user cancelled a job — from
        silently resurrecting it by calling mark_job('dispatching') or mark_job('completed').
        """
        now = _now_iso()
        terminal = status in ("completed", "failed", "interrupted", "cancelled")
        update_expr = "SET #st = :s, last_heartbeat = :ts"
        if terminal:
            update_expr += ", completed_at = :ts"
        try:
            self._table().update_item(
                Key={"job_id": job_id},
                UpdateExpression=update_expr,
                ConditionExpression=(
                    "#st <> :completed AND #st <> :failed"
                    " AND #st <> :cancelled AND #st <> :interrupted"
                ),
                ExpressionAttributeNames={"#st": "status"},
                ExpressionAttributeValues={
                    ":s": status,
                    ":ts": now,
                    ":completed": "completed",
                    ":failed": "failed",
                    ":cancelled": "cancelled",
                    ":interrupted": "interrupted",
                },
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
        """Return up to `limit` jobs, scanning DynamoDB pages until enough are found.

        Uses page-by-page scanning rather than _scan_all so it stops as soon as
        `limit` filtered results are accumulated — avoids reading the entire table
        for small result sets when no status filter is applied.
        """
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

    def find_interrupted_jobs(self) -> list:
        """Return all jobs in the interrupted status (set by graceful shutdown handler).

        These jobs were mid-run when the ECS task received SIGTERM and need to be
        re-enqueued by the next task's startup resume pass.
        """
        items = self._scan_all(
            FilterExpression="#st = :interrupted",
            ExpressionAttributeNames={"#st": "status"},
            ExpressionAttributeValues={":interrupted": "interrupted"},
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

    def try_mark_interrupted(self, job_id: str) -> bool:
        """Atomically mark a job interrupted only if it is not already in a terminal status.

        Prevents a task shutdown from overwriting a cancelled/completed status with interrupted,
        which would cause resume logic to incorrectly re-enqueue the job on next startup.
        Returns True if marked interrupted, False if already terminal.
        """
        try:
            self._table().update_item(
                Key={"job_id": job_id},
                UpdateExpression="SET #st = :interrupted, completed_at = :now, last_heartbeat = :now",
                ConditionExpression=(
                    "#st <> :completed AND #st <> :failed"
                    " AND #st <> :interrupted AND #st <> :cancelled"
                ),
                ExpressionAttributeNames={"#st": "status"},
                ExpressionAttributeValues={
                    ":interrupted": "interrupted",
                    ":completed": "completed",
                    ":failed": "failed",
                    ":cancelled": "cancelled",
                    ":now": _now_iso(),
                },
            )
            logger.info({"event": "job_interrupted", "job_id": job_id})
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
                    "#st <> :completed AND #st <> :failed AND #st <> :cancelled"
                ),
                ExpressionAttributeNames={"#st": "status"},
                ExpressionAttributeValues={
                    ":cancelled": "cancelled",
                    ":completed": "completed",
                    ":failed": "failed",
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


class CheckpointStore:
    """Per-collection keyset resume cursors for in-progress granule reindex jobs.

    Each record tracks how far a collection has been streamed so a SIGTERM/restart
    can resume mid-collection rather than restarting from offset 0.

    Table schema (partition key: job_id, sort key: collection_id):
      job_id            String  PK
      collection_id     String  SK
      last_concept_id   String  keyset cursor (resume after this concept_id)
      chunks_dispatched Number  diagnostic
      granules_dispatched Number cumulative granules dispatched for this collection
      updated_at        String  ISO timestamp
    """

    def _table(self):
        return _checkpoint_table()

    def write_collection_checkpoint(
        self,
        job_id: str,
        collection_id: str,
        last_concept_id: str,
        granules_dispatched: int,
        chunks_dispatched: int,
    ) -> None:
        ttl = int((datetime.now(timezone.utc) + timedelta(days=30)).timestamp())
        self._table().put_item(Item={
            "job_id": job_id,
            "collection_id": collection_id,
            "last_concept_id": last_concept_id,
            "chunks_dispatched": chunks_dispatched,
            "granules_dispatched": granules_dispatched,
            "updated_at": _now_iso(),
            "ttl": ttl,
        })

    def get_collection_checkpoint(self, job_id: str, collection_id: str) -> Optional[dict]:
        resp = self._table().get_item(Key={"job_id": job_id, "collection_id": collection_id})
        item = resp.get("Item")
        return _deserialize(item) if item is not None else None

    def delete_collection_checkpoint(self, job_id: str, collection_id: str) -> None:
        self._table().delete_item(Key={"job_id": job_id, "collection_id": collection_id})


checkpoint_store = CheckpointStore()
