"""Real Oracle DB client (thin mode via oracledb).

SQL patterns mirror db-es-audit/src/cmr_db.py exactly:
  - HAVING MAX(deleted) KEEP (DENSE_RANK LAST ORDER BY revision_id) = 0
  - REVISION_DATE filtered with TO_TIMESTAMP_TZ()
  - Provider derived from concept-id suffix: C1234-PROV → PROV
  - Tables: METADATA_DB.{PROVIDER}_GRANULES / _COLLECTIONS

Pagination uses Oracle 12c+ OFFSET/FETCH rather than cursor.fetchmany()
because the throttler enqueues discrete page work items (offset, limit).
"""
import logging
import re
from typing import Optional

import oracledb

from app.config import config

logger = logging.getLogger(__name__)

_BATCH_SIZE = 500

# ---------------------------------------------------------------------------
# Per-provider granule / collection SQL
# ---------------------------------------------------------------------------

_COUNT_SQL = """\
SELECT COUNT(*)
FROM (
    SELECT concept_id
    FROM METADATA_DB.{table}
    WHERE PARENT_COLLECTION_ID = :collection_id
    {after_clause}
    {before_clause}
    GROUP BY concept_id
    HAVING MAX(deleted) KEEP (DENSE_RANK LAST ORDER BY revision_id) = 0
)"""

_IDS_SQL = """\
SELECT concept_id, MAX(revision_id) AS revision_id
FROM METADATA_DB.{table}
WHERE PARENT_COLLECTION_ID = :collection_id
{after_clause}
{before_clause}
GROUP BY concept_id
HAVING MAX(deleted) KEEP (DENSE_RANK LAST ORDER BY revision_id) = 0
ORDER BY concept_id DESC
OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY"""

_COLLECTIONS_SQL = """\
SELECT concept_id
FROM METADATA_DB.{table}
GROUP BY concept_id
HAVING MAX(deleted) KEEP (DENSE_RANK LAST ORDER BY revision_id) = 0
ORDER BY concept_id"""

_PROVIDERS_SQL = """\
SELECT DISTINCT REGEXP_REPLACE(table_name, '_GRANULES$', '')
FROM all_tables
WHERE owner = 'METADATA_DB'
  AND table_name LIKE '%_GRANULES'
ORDER BY 1"""

# Used inline in granule SQL (must include the AND prefix)
_AFTER_CLAUSE  = "AND REVISION_DATE >= TO_TIMESTAMP_TZ(:after,  'YYYY-MM-DD\"T\"HH24:MI:SS TZH:TZM')"
_BEFORE_CLAUSE = "AND REVISION_DATE <= TO_TIMESTAMP_TZ(:before, 'YYYY-MM-DD\"T\"HH24:MI:SS TZH:TZM')"

# ---------------------------------------------------------------------------
# Shared / generic concept type SQL
# ---------------------------------------------------------------------------

# Used as individual conditions joined by AND (no leading AND)
_AFTER_COND  = "REVISION_DATE >= TO_TIMESTAMP_TZ(:after,  'YYYY-MM-DD\"T\"HH24:MI:SS TZH:TZM')"
_BEFORE_COND = "REVISION_DATE <= TO_TIMESTAMP_TZ(:before, 'YYYY-MM-DD\"T\"HH24:MI:SS TZH:TZM')"

_SHARED_IDS_SQL = """\
SELECT concept_id, MAX(revision_id) AS revision_id
FROM METADATA_DB.{table}
{where_clause}
GROUP BY concept_id
HAVING MAX(deleted) KEEP (DENSE_RANK LAST ORDER BY revision_id) = 0
ORDER BY concept_id"""

_SINGLE_CONCEPT_SQL = """\
SELECT concept_id, MAX(revision_id) AS revision_id
FROM METADATA_DB.{table}
WHERE concept_id = :concept_id{extra_cond}
GROUP BY concept_id
HAVING MAX(deleted) KEEP (DENSE_RANK LAST ORDER BY revision_id) = 0"""

# ---------------------------------------------------------------------------
# Type maps
# ---------------------------------------------------------------------------

# Internal concept type → (table_name, document_name_filter or None)
_SHARED_TYPE_TABLES: dict[str, tuple[str, Optional[str]]] = {
    "variable":             ("cmr_variables", None),
    "service":              ("cmr_services", None),
    "tool":                 ("cmr_tools", None),
    "subscription":         ("cmr_subscriptions", None),
    "generic":              ("cmr_generic_documents", None),
    "data-quality-summary": ("cmr_generic_documents", "Data Quality Summary"),
    "order-option":         ("cmr_generic_documents", "Order Option"),
    "grid":                 ("cmr_generic_documents", "Grid"),
    "citation":             ("cmr_generic_documents", "Citation"),
    "visualization":        ("cmr_generic_documents", "Visualization"),
}

# concept-id prefix → (shared_table_name or None, document_name_or_table_suffix)
# None table_name means per-provider; second element is then the table suffix
_PREFIX_TO_LOOKUP: dict[str, tuple[Optional[str], Optional[str]]] = {
    "C":   (None, "_COLLECTIONS"),
    "G":   (None, "_GRANULES"),
    "V":   ("cmr_variables", None),
    "S":   ("cmr_services", None),
    "TL":  ("cmr_tools", None),
    "SUB": ("cmr_subscriptions", None),
    "DQS": ("cmr_generic_documents", "Data Quality Summary"),
    "OO":  ("cmr_generic_documents", "Order Option"),
    "GRD": ("cmr_generic_documents", "Grid"),
    "CIT": ("cmr_generic_documents", "Citation"),
    "VIS": ("cmr_generic_documents", "Visualization"),
}

_CONCEPT_ID_PREFIX_RE = re.compile(r'^([A-Z]+)')
_PROVIDER_ID_RE = re.compile(r'^[A-Z0-9_]+$')


def _validate_provider_id(provider_id: str) -> None:
    if not _PROVIDER_ID_RE.match(provider_id):
        raise ValueError(f"Invalid provider ID for Oracle table name: {provider_id!r}")


def _oracle_ts(value: str) -> str:
    """Convert ISO8601 Z-suffix to a string Oracle TO_TIMESTAMP_TZ accepts with TZH:TZM.

    Oracle's TZH:TZM expects a numeric offset (+HH:MM), not the letter Z.
    The format mask includes a space before TZH:TZM, so we replace Z with ' +00:00'.
    """
    return value.replace("Z", " +00:00")


def _parse_concept_prefix(concept_id: str) -> str:
    m = _CONCEPT_ID_PREFIX_RE.match(concept_id)
    return m.group(1) if m else ""


def _provider_from_collection(collection_id: str) -> str:
    provider = collection_id.split("-", 1)[1] if "-" in collection_id else collection_id
    _validate_provider_id(provider)
    return provider


class OracleClient:
    def __init__(self) -> None:
        self._pool = None

    def _get_pool(self):
        if self._pool is None:
            dsn = f"{config.db_host}:{config.db_port}/{config.db_service}"
            self._pool = oracledb.create_pool(
                user=config.db_user,
                password=config.db_password,
                dsn=dsn,
                min=1,
                max=5,
                increment=1,
            )
            logger.info({"event": "oracle_pool_created", "dsn": dsn})
        return self._pool

    # ------------------------------------------------------------------
    # Provider / collection / granule (per-provider tables)
    # ------------------------------------------------------------------

    def get_all_provider_ids(self) -> list[str]:
        with self._get_pool().acquire() as conn, conn.cursor() as cur:
            cur.execute(_PROVIDERS_SQL)
            return [row[0] for row in cur.fetchall()]

    def get_collection_ids_for_provider(self, provider_id: str) -> list[str]:
        _validate_provider_id(provider_id)
        table = f"{provider_id}_COLLECTIONS"
        sql = _COLLECTIONS_SQL.format(table=table)
        with self._get_pool().acquire() as conn, conn.cursor() as cur:
            cur.arraysize = _BATCH_SIZE
            cur.execute(sql)
            return [row[0] for row in cur.fetchall()]

    def get_granule_count(
        self,
        collection_id: str,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> int:
        provider = _provider_from_collection(collection_id)
        table = f"{provider}_GRANULES"
        sql = _COUNT_SQL.format(
            table=table,
            after_clause=_AFTER_CLAUSE if after else "",
            before_clause=_BEFORE_CLAUSE if before else "",
        )
        bind: dict = {"collection_id": collection_id}
        if after:
            bind["after"] = _oracle_ts(after)
        if before:
            bind["before"] = _oracle_ts(before)
        with self._get_pool().acquire() as conn, conn.cursor() as cur:
            cur.execute(sql, bind)
            row = cur.fetchone()
            return row[0] if row else 0

    def get_granule_ids(
        self,
        collection_id: str,
        offset: int,
        limit: int,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> list[tuple[str, int]]:
        provider = _provider_from_collection(collection_id)
        table = f"{provider}_GRANULES"
        sql = _IDS_SQL.format(
            table=table,
            after_clause=_AFTER_CLAUSE if after else "",
            before_clause=_BEFORE_CLAUSE if before else "",
        )
        bind: dict = {"collection_id": collection_id, "offset": offset, "limit": limit}
        if after:
            bind["after"] = _oracle_ts(after)
        if before:
            bind["before"] = _oracle_ts(before)
        with self._get_pool().acquire() as conn, conn.cursor() as cur:
            cur.arraysize = min(limit, 5000)
            cur.execute(sql, bind)
            return [(row[0], row[1]) for row in cur.fetchall()]

    # ------------------------------------------------------------------
    # Shared concept type tables
    # ------------------------------------------------------------------

    def get_concept_ids_by_type(
        self,
        concept_type: str,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> list[tuple[str, int]]:
        """Return (concept_id, revision_id) for all live concepts of the given type."""
        if concept_type == "collection":
            return self._get_all_collection_ids(after=after, before=before)

        table_doc = _SHARED_TYPE_TABLES.get(concept_type)
        if table_doc is None:
            raise ValueError(f"Unknown concept type: {concept_type!r}")

        table, document_name = table_doc
        return self._query_concept_ids(table, document_name=document_name, after=after, before=before)

    def get_concept_by_id(self, concept_id: str) -> Optional[dict]:
        """Return {"concept-id": ..., "revision-id": ...} for a live concept, or None."""
        prefix = _parse_concept_prefix(concept_id)
        lookup = _PREFIX_TO_LOOKUP.get(prefix)
        if lookup is None:
            return None

        table_name, extra = lookup

        if table_name is None:
            # Per-provider table; extra is the table suffix
            provider = _provider_from_collection(concept_id)
            table = f"{provider}{extra}"
            sql = _SINGLE_CONCEPT_SQL.format(table=table, extra_cond="")
            bind: dict = {"concept_id": concept_id}
        elif extra is not None:
            # Shared table with document_name filter (generic documents)
            sql = _SINGLE_CONCEPT_SQL.format(
                table=table_name,
                extra_cond=" AND document_name = :document_name",
            )
            bind = {"concept_id": concept_id, "document_name": extra}
        else:
            # Shared table, no additional filter
            sql = _SINGLE_CONCEPT_SQL.format(table=table_name, extra_cond="")
            bind = {"concept_id": concept_id}

        with self._get_pool().acquire() as conn, conn.cursor() as cur:
            cur.execute(sql, bind)
            row = cur.fetchone()
            if row is None:
                return None
            return {"concept-id": row[0], "revision-id": row[1]}

    # ------------------------------------------------------------------
    # Private helpers
    # ------------------------------------------------------------------

    def _get_all_collection_ids(
        self,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> list[tuple[str, int]]:
        results: list[tuple[str, int]] = []
        for provider_id in self.get_all_provider_ids():
            _validate_provider_id(provider_id)
            table = f"{provider_id}_COLLECTIONS"
            results.extend(self._query_concept_ids(table, document_name=None, after=after, before=before))
        return results

    def _query_concept_ids(
        self,
        table: str,
        document_name: Optional[str] = None,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> list[tuple[str, int]]:
        conditions: list[str] = []
        bind: dict = {}

        if document_name is not None:
            conditions.append("document_name = :document_name")
            bind["document_name"] = document_name
        if after:
            conditions.append(_AFTER_COND)
            bind["after"] = _oracle_ts(after)
        if before:
            conditions.append(_BEFORE_COND)
            bind["before"] = _oracle_ts(before)

        where_clause = ("WHERE " + " AND ".join(conditions)) if conditions else ""
        sql = _SHARED_IDS_SQL.format(table=table, where_clause=where_clause)

        with self._get_pool().acquire() as conn, conn.cursor() as cur:
            cur.arraysize = _BATCH_SIZE
            cur.execute(sql, bind)
            return [(row[0], row[1]) for row in cur.fetchall()]
