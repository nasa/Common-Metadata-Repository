"""Oracle DB client using the oracledb thin driver.

SQL patterns used:
  - HAVING MAX(deleted) KEEP (DENSE_RANK LAST ORDER BY revision_id) = 0
  - REVISION_DATE filtered with TO_TIMESTAMP_TZ()
  - Provider derived from concept-id suffix: C1234-PROV → PROV
  - Tables: METADATA_DB.{PROVIDER}_GRANULES / _COLLECTIONS

Granule dispatch uses stream_granule_ids() (keyset pagination via fetchmany)
rather than OFFSET/FETCH, so cost is O(page_size) not O(n^2).
"""
import logging
import re
import threading
from typing import Iterator, Optional

import oracledb

from app.config import config

logger = logging.getLogger(__name__)

_BATCH_SIZE = 500

# ---------------------------------------------------------------------------
# Per-provider granule / collection SQL
# ---------------------------------------------------------------------------

# Streaming keyset query: no OFFSET/FETCH — cursor.fetchmany() controls chunk size.
# ORDER BY ASC is required for the keyset forward scan (concept_id > :start_after).
_STREAM_IDS_SQL = """\
SELECT concept_id, MAX(revision_id) AS revision_id
FROM METADATA_DB.{table}
WHERE PARENT_COLLECTION_ID = :collection_id
{after_clause}
{before_clause}
{keyset_clause}
GROUP BY concept_id
HAVING MAX(deleted) KEEP (DENSE_RANK LAST ORDER BY revision_id) = 0
ORDER BY concept_id ASC"""

# Keyset resume cursor: skip rows already dispatched ('' → all rows on first run, but
# Oracle treats '' as NULL so we omit the clause entirely when start_after is falsy).
_KEYSET_CLAUSE = "AND concept_id > :start_after"

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

# Two-query keyset strategy for non-granule types:
#   1. _PAGE_IDS_SQL  — fast DISTINCT index scan to find the page boundary (no aggregation)
#   2. _SHARED_IDS_SQL — bounded aggregation for only those concept_ids (concept_id <= page_end)
# Separating them lets Oracle use the concept_id index for the boundary scan without
# blocking on a full-table GROUP BY before FETCH FIRST can apply.
_PAGE_IDS_SQL = """\
SELECT DISTINCT concept_id
FROM METADATA_DB.{table}
{where_clause}
ORDER BY concept_id
FETCH FIRST {page_size} ROWS ONLY"""

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

# Internal concept type → (table_name, concept_id LIKE prefix or None)
# Generic document subtypes share cmr_generic_documents; their concept_id prefix is
# globally unique within that table so LIKE lets Oracle use the concept_id index
# directly instead of filtering on the unindexed `schema` column.
# "generic" (no prefix) targets all rows in the table regardless of subtype.
_SHARED_TYPE_TABLES: dict[str, tuple[str, Optional[str]]] = {
    "variable":             ("cmr_variables",          None),
    "service":              ("cmr_services",            None),
    "tool":                 ("cmr_tools",               None),
    "subscription":         ("cmr_subscriptions",       None),
    "generic":              ("cmr_generic_documents",   None),
    "data-quality-summary": ("cmr_generic_documents",   "DQS%"),
    "order-option":         ("cmr_generic_documents",   "OO%"),
    "grid":                 ("cmr_generic_documents",   "GRD%"),
    "citation":             ("cmr_generic_documents",   "CIT%"),
    "visualization":        ("cmr_generic_documents",   "VIS%"),
}

# concept-id prefix → (shared_table_name or None, table_suffix or None)
# None table_name means per-provider table; second element is the table name suffix.
# Generic documents (DQS, OO, GRD, CIT, VIS) all share cmr_generic_documents; their
# concept_id prefix is globally unique within that table so no extra filter is needed.
_PREFIX_TO_LOOKUP: dict[str, tuple[Optional[str], Optional[str]]] = {
    "C":   (None, "_COLLECTIONS"),
    "G":   (None, "_GRANULES"),
    "V":   ("cmr_variables", None),
    "S":   ("cmr_services", None),
    "TL":  ("cmr_tools", None),
    "SUB": ("cmr_subscriptions", None),
    "DQS": ("cmr_generic_documents", None),
    "OO":  ("cmr_generic_documents", None),
    "GRD": ("cmr_generic_documents", None),
    "CIT": ("cmr_generic_documents", None),
    "VIS": ("cmr_generic_documents", None),
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
        self._pool_lock = threading.Lock()

    def _get_pool(self):
        if self._pool is None:
            with self._pool_lock:
                if self._pool is None:  # double-checked locking
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

    def stream_granule_ids(
        self,
        collection_id: str,
        chunk_size: int,
        after: Optional[str] = None,
        before: Optional[str] = None,
        start_after_concept_id: Optional[str] = None,
    ) -> Iterator[list[tuple[str, int]]]:
        """Yield successive chunks of (concept_id, revision_id) tuples using keyset pagination.

        Opens one Oracle cursor for the full collection and streams via fetchmany()
        rather than OFFSET/FETCH, so cost is O(chunk_size) per call instead of O(n^2).

        start_after_concept_id: resume cursor — skip concepts at or before this ID.
            Pass None (or empty string) to start from the beginning.

        The Oracle connection is held for the full stream.  Callers should handle
        ORA-03113/ORA-03114 (idle timeout) by catching OperationalError and retrying
        from the last checkpoint.
        """
        provider = _provider_from_collection(collection_id)
        table = f"{provider}_GRANULES"

        use_keyset = bool(start_after_concept_id)
        sql = _STREAM_IDS_SQL.format(
            table=table,
            after_clause=_AFTER_CLAUSE if after else "",
            before_clause=_BEFORE_CLAUSE if before else "",
            keyset_clause=_KEYSET_CLAUSE if use_keyset else "",
        )
        bind: dict = {"collection_id": collection_id}
        if after:
            bind["after"] = _oracle_ts(after)
        if before:
            bind["before"] = _oracle_ts(before)
        if use_keyset:
            bind["start_after"] = start_after_concept_id

        with self._get_pool().acquire() as conn, conn.cursor() as cur:
            cur.arraysize = chunk_size
            cur.execute(sql, bind)
            while True:
                rows = cur.fetchmany(chunk_size)
                if not rows:
                    break
                yield [(row[0], row[1]) for row in rows]

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
        return list(self.stream_concept_ids_by_type(concept_type, after=after, before=before))

    def stream_concept_ids_by_type(
        self,
        concept_type: str,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> Iterator[tuple[str, int]]:
        """Yield (concept_id, revision_id) for all live concepts of the given type."""
        if concept_type == "collection":
            yield from self._stream_all_collection_ids(after=after, before=before)
            return

        table_doc = _SHARED_TYPE_TABLES.get(concept_type)
        if table_doc is None:
            raise ValueError(f"Unknown concept type: {concept_type!r}")

        table, prefix = table_doc
        yield from self._stream_concept_ids(
            table, prefix=prefix, after=after, before=before
        )

    def get_concept_by_id(self, concept_id: str) -> Optional[dict]:
        """Return {"concept-id": ..., "revision-id": ...} for a live concept, or None."""
        prefix = _parse_concept_prefix(concept_id)
        lookup = _PREFIX_TO_LOOKUP.get(prefix)
        if lookup is None:
            return None

        table_name, table_suffix = lookup

        if table_name is None:
            # Per-provider table; table_suffix is the table name suffix
            provider = _provider_from_collection(concept_id)
            table = f"{provider}{table_suffix}"
        else:
            table = table_name

        sql = _SINGLE_CONCEPT_SQL.format(table=table, extra_cond="")
        bind: dict = {"concept_id": concept_id}

        with self._get_pool().acquire() as conn, conn.cursor() as cur:
            cur.execute(sql, bind)
            row = cur.fetchone()
            if row is None:
                return None
            return {"concept-id": row[0], "revision-id": row[1]}

    # ------------------------------------------------------------------
    # Private helpers
    # ------------------------------------------------------------------

    def _stream_all_collection_ids(
        self,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> Iterator[tuple[str, int]]:
        for provider_id in self.get_all_provider_ids():
            _validate_provider_id(provider_id)
            table = f"{provider_id}_COLLECTIONS"
            yield from self._stream_concept_ids(table, prefix=None, after=after, before=before)

    def _stream_concept_ids(
        self,
        table: str,
        prefix: Optional[str] = None,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> Iterator[tuple[str, int]]:
        """Yield (concept_id, revision_id) using a two-query keyset strategy per page.

        Query 1 (_PAGE_IDS_SQL): fast DISTINCT index scan with FETCH FIRST to locate
        the page boundary — no aggregation, returns in milliseconds even for 300k rows.

        Query 2 (_SHARED_IDS_SQL): GROUP BY + HAVING aggregation bounded to
        concept_id <= page_end, so Oracle aggregates only ~page_size concepts' rows.

        Date filters (after/before) apply only to Query 2 so the page boundary cursor
        advances monotonically regardless of which revisions fall in the date range.
        """
        start_after: Optional[str] = None

        while True:
            # --- Query 1: locate page boundary (fast index scan, no aggregation) ---
            page_conds: list[str] = []
            page_bind: dict = {}
            if start_after:
                page_conds.append("concept_id > :start_after")
                page_bind["start_after"] = start_after
            if prefix is not None:
                page_conds.append("concept_id LIKE :prefix")
                page_bind["prefix"] = prefix

            page_where = ("WHERE " + " AND ".join(page_conds)) if page_conds else ""
            page_sql = _PAGE_IDS_SQL.format(
                table=table, where_clause=page_where, page_size=_BATCH_SIZE
            )
            with self._get_pool().acquire() as conn, conn.cursor() as cur:
                cur.execute(page_sql, page_bind)
                page_ids = cur.fetchall()

            if not page_ids:
                break

            page_end = page_ids[-1][0]
            is_last_page = len(page_ids) < _BATCH_SIZE

            # --- Query 2: aggregate the bounded range ---
            agg_conds: list[str] = []
            agg_bind: dict = {}
            if start_after:
                agg_conds.append("concept_id > :start_after")
                agg_bind["start_after"] = start_after
            agg_conds.append("concept_id <= :page_end")
            agg_bind["page_end"] = page_end
            if prefix is not None:
                agg_conds.append("concept_id LIKE :prefix")
                agg_bind["prefix"] = prefix
            if after:
                agg_conds.append(_AFTER_COND)
                agg_bind["after"] = _oracle_ts(after)
            if before:
                agg_conds.append(_BEFORE_COND)
                agg_bind["before"] = _oracle_ts(before)

            agg_sql = _SHARED_IDS_SQL.format(
                table=table, where_clause="WHERE " + " AND ".join(agg_conds)
            )
            with self._get_pool().acquire() as conn, conn.cursor() as cur:
                cur.execute(agg_sql, agg_bind)
                rows = cur.fetchall()

            for row in rows:
                yield (row[0], row[1])

            if is_last_page:
                break

            start_after = page_end

    def _query_concept_ids(
        self,
        table: str,
        prefix: Optional[str] = None,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> list[tuple[str, int]]:
        """Return all (concept_id, revision_id) for a table as a list."""
        return list(self._stream_concept_ids(
            table, prefix=prefix, after=after, before=before
        ))
