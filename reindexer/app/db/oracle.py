"""Oracle DB client using the oracledb thin driver.

SQL patterns used:
  - HAVING MAX(deleted) KEEP (DENSE_RANK LAST ORDER BY revision_id) = 0
  - REVISION_DATE filtered with TO_TIMESTAMP_TZ()
  - Provider derived from concept-id suffix: C1234-PROV → PROV
  - Tables: METADATA_DB.{PROVIDER}_GRANULES / _COLLECTIONS, or the shared
    SMALL_PROV_* tables filtered by provider_id for small providers

Per-collection granule dispatch uses stream_granule_ids_paged() (two-query keyset
paging by concept_id) rather than OFFSET/FETCH, so cost is O(page_size) not O(n^2).
"""
import logging
import re
import threading
from contextlib import contextmanager
from typing import Iterator, Optional

import oracledb

from app.config import config
from app.db.call_tracker import tracked_call

logger = logging.getLogger(__name__)

_BATCH_SIZE = 500

# ---------------------------------------------------------------------------
# Per-provider granule / collection SQL
# ---------------------------------------------------------------------------

_COLLECTIONS_SQL = """\
SELECT concept_id
FROM METADATA_DB.{table}
WHERE 1 = 1 {provider_clause}
GROUP BY concept_id
HAVING MAX(deleted) KEEP (DENSE_RANK LAST ORDER BY revision_id) = 0
ORDER BY concept_id"""

_PROVIDERS_SQL = """\
SELECT provider_id, small
FROM METADATA_DB.providers
ORDER BY provider_id"""

# Providers flagged small share these tables, which carry a provider_id column
# (metadata-db's get-table-name).
_SMALL_PROVIDER_TABLE_PREFIX = "SMALL_PROV"

# Used inline in granule SQL (must include the AND prefix). Literal, not bound:
# bind-variable peeking on these was confirmed to cause Oracle to reuse a cached
# plan from an earlier collection's selectivity for wildly different collections,
# hanging indefinitely on some while the identical query with literal values ran
# in seconds. after/before are ISO8601-validated at the API boundary before
# _oracle_ts, and collection_id is regex-validated or DB-sourced — safe to embed.
_AFTER_CLAUSE  = "AND REVISION_DATE >= TO_TIMESTAMP_TZ('{after}',  'YYYY-MM-DD\"T\"HH24:MI:SS TZH:TZM')"
_BEFORE_CLAUSE = "AND REVISION_DATE <= TO_TIMESTAMP_TZ('{before}', 'YYYY-MM-DD\"T\"HH24:MI:SS TZH:TZM')"

# ---------------------------------------------------------------------------
# Id-range granule scan (bootstrap-style)
# ---------------------------------------------------------------------------

# No index covers PARENT_COLLECTION_ID + REVISION_DATE together on *_GRANULES, so
# these bound work by a plain `id` range and date-filter only within that bound —
# mirrors bootstrap's find-batch-starting-id-between-date-times.
#
# Unlike collection_id/after/before above, start_id/end_id/min_id ARE bound:
# every call has the same shape regardless of id, so plan reuse helps here.

_FIND_NEXT_ID_SQL = """\
SELECT MIN(id)
FROM METADATA_DB.{table}
WHERE id >= :min_id
{provider_clause}
{after_clause}"""

# deleted=0 is per-row here, not aggregated per-concept_id like every other query
# in this module — a concept created and deleted within the same scan window can
# have its live revision dispatched before its tombstone is ever seen, unless
# tombstones are included.
_FETCH_ID_RANGE_CHUNK_SQL = """\
SELECT concept_id, revision_id, deleted
FROM METADATA_DB.{table}
WHERE id >= :start_id
  AND id < :end_id
{live_only_clause}
{provider_clause}
{after_clause}
{before_clause}"""

# ---------------------------------------------------------------------------
# Per-collection granule scan
# ---------------------------------------------------------------------------

# Same two-query shape as _stream_concept_ids below: an index-only scan for the
# page boundary, then an aggregation bounded to that page, which alone applies
# the date/tombstone filters. Paging and aggregating by the SAME key (concept_id)
# is load-bearing — concept_id <= page_end takes every revision of the boundary
# concept, so the HAVING tombstone check always sees a concept's history whole.
# collection_id is a literal, not bound — same reasoning as _AFTER_CLAUSE.

_COLLECTION_KEYSET_CLAUSE = "AND concept_id > :start_after"

# No DISTINCT, and ordered exactly like *_GRANULES_PCR, so Oracle stops after
# page_size index entries instead of sorting the whole collection.
_COLLECTION_PAGE_IDS_SQL = """\
SELECT concept_id
FROM METADATA_DB.{table}
WHERE parent_collection_id = '{collection_id}'
{keyset_clause}
ORDER BY concept_id, revision_id
FETCH FIRST {page_size} ROWS ONLY"""

# deleted IN (0, 1) is always true, but it lets Oracle seek *_GRANULES_PDCR
# (parent_collection_id, deleted, concept_id, ...) by the page's concept_id range.
_COLLECTION_CHUNK_SQL = """\
SELECT concept_id, MAX(revision_id) AS revision_id,
       MAX(deleted) KEEP (DENSE_RANK LAST ORDER BY revision_id) AS deleted
FROM METADATA_DB.{table}
WHERE parent_collection_id = '{collection_id}'
  AND deleted IN (0, 1)
  AND concept_id <= :page_end
{keyset_clause}
{after_clause}
{before_clause}
GROUP BY concept_id
{live_only_clause}
ORDER BY concept_id"""

_LIVE_ONLY_HAVING = "HAVING MAX(deleted) KEEP (DENSE_RANK LAST ORDER BY revision_id) = 0"

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
_PROVIDER_ID_RE = re.compile(r'^[A-Z0-9_]+\Z')


def _validate_provider_id(provider_id: str) -> None:
    if not _PROVIDER_ID_RE.match(provider_id):
        raise ValueError(f"Invalid provider ID for Oracle table name: {provider_id!r}")


def _oracle_ts(value: str) -> str:
    """Convert ISO8601 Z-suffix to a string Oracle TO_TIMESTAMP_TZ accepts with TZH:TZM.

    Oracle's TZH:TZM expects a numeric offset (+HH:MM), not the letter Z.
    The format mask includes a space before TZH:TZM, so we replace Z with ' +00:00'.
    """
    return value.replace("Z", " +00:00")


def _date_clauses(after: Optional[str], before: Optional[str]) -> dict:
    """after_clause/before_clause format args for granule SQL; empty when unset."""
    return {
        "after_clause": _AFTER_CLAUSE.format(after=_oracle_ts(after)) if after else "",
        "before_clause": _BEFORE_CLAUSE.format(before=_oracle_ts(before)) if before else "",
    }


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
        self._providers: dict[str, bool] = {}  # provider_id → small; see _table

    def _get_pool(self):
        if self._pool is None:
            with self._pool_lock:
                if self._pool is None:  # double-checked locking
                    dsn = f"{config.db_host}:{config.db_port}/{config.db_service}"
                    self._pool = oracledb.create_pool(
                        user=config.db_user,
                        password=config.db_password,
                        dsn=dsn,
                        min=config.oracle_pool_min,
                        max=config.oracle_pool_max,
                        increment=config.oracle_pool_increment,
                    )
                    logger.info({"event": "oracle_pool_created"})
        return self._pool

    @contextmanager
    def _acquire_cursor(self):
        """Acquire a pooled connection and yield a cursor. Sets no call timeout;
        tracked_call flags a call that hangs instead."""
        with tracked_call(), self._get_pool().acquire() as conn:
            with conn.cursor() as cur:
                yield cur

    # ------------------------------------------------------------------
    # Provider / collection / granule (per-provider tables)
    # ------------------------------------------------------------------

    def get_all_provider_ids(self) -> list[str]:
        with self._acquire_cursor() as cur:
            cur.execute(_PROVIDERS_SQL)
            rows = cur.fetchall()
        self._providers = {provider_id: bool(small) for provider_id, small in rows}
        return list(self._providers)

    def _is_small(self, provider_id: str) -> bool:
        if provider_id not in self._providers:
            self.get_all_provider_ids()  # first use, or a provider added since the last load
        return self._providers.get(provider_id, False)

    def _table(self, provider_id: str, suffix: str) -> tuple[str, str]:
        """(table, provider_clause) for a per-provider concept table. The clause is only
        needed where rows aren't already scoped by a collection or concept id."""
        _validate_provider_id(provider_id)
        if self._is_small(provider_id):
            return f"{_SMALL_PROVIDER_TABLE_PREFIX}{suffix}", f"AND provider_id = '{provider_id}'"
        return f"{provider_id}{suffix}", ""

    def get_collection_ids_for_provider(self, provider_id: str) -> list[str]:
        table, provider_clause = self._table(provider_id, "_COLLECTIONS")
        sql = _COLLECTIONS_SQL.format(table=table, provider_clause=provider_clause)
        with self._acquire_cursor() as cur:
            cur.arraysize = _BATCH_SIZE
            cur.execute(sql)
            return [row[0] for row in cur.fetchall()]

    # ------------------------------------------------------------------
    # Id-range granule scan (per-provider, bootstrap-style)
    # ------------------------------------------------------------------

    def find_next_granule_id_in_range(
        self,
        provider_id: str,
        min_id: int,
        after: Optional[str] = None,
    ) -> Optional[int]:
        """Return the smallest granule `id` >= min_id revised at or after `after`,
        or None if there is none. No `before`: the window query applies it, and
        without a date or provider filter this is a single PK index lookup."""
        table, provider_clause = self._table(provider_id, "_GRANULES")
        sql = _FIND_NEXT_ID_SQL.format(
            table=table, provider_clause=provider_clause, after_clause=_date_clauses(after, None)["after_clause"],
        )
        with self._acquire_cursor() as cur:
            cur.execute(sql, {"min_id": min_id})
            row = cur.fetchone()
            return int(row[0]) if row and row[0] is not None else None

    def fetch_granule_id_range_chunk(
        self,
        provider_id: str,
        start_id: int,
        end_id: int,
        after: Optional[str] = None,
        before: Optional[str] = None,
        include_deleted: bool = False,
    ) -> list[tuple[str, int, int]]:
        """Return (concept_id, revision_id, deleted) for granule rows in the half-open
        id range [start_id, end_id) — matches bootstrap's convention, avoiding
        boundary duplicates when successive windows chain together. Tombstone rows
        only with include_deleted.
        """
        table, provider_clause = self._table(provider_id, "_GRANULES")
        sql = _FETCH_ID_RANGE_CHUNK_SQL.format(
            table=table, provider_clause=provider_clause,
            live_only_clause="" if include_deleted else "AND deleted = 0",
            **_date_clauses(after, before),
        )
        with self._acquire_cursor() as cur:
            cur.execute(sql, {"start_id": start_id, "end_id": end_id})
            return cur.fetchall()

    # ------------------------------------------------------------------
    # Per-collection granule scan
    # ------------------------------------------------------------------

    def stream_granule_ids_paged(
        self,
        collection_id: str,
        chunk_size: int,
        after: Optional[str] = None,
        before: Optional[str] = None,
        start_after_concept_id: Optional[str] = None,
        include_deleted: bool = False,
    ) -> Iterator[tuple[str, list[tuple[str, int, int]]]]:
        """Yield (page_end, chunk) per page for one collection, in ascending
        concept_id order. chunk is the page's (concept_id, revision_id, deleted) rows
        for each concept's latest revision matching after/before; live ones only
        unless include_deleted. It may be empty when the date filter excludes the
        whole page — still yielded, so the caller gets a turn to check
        cancellation and can checkpoint on page_end.

        start_after_concept_id: resume cursor — skip concepts at or before this
        ID (typically a previous page_end). None (or empty) starts from the
        beginning.
        """
        table, _ = self._table(_provider_from_collection(collection_id), "_GRANULES")
        page_size = int(chunk_size)  # interpolated into FETCH FIRST, so force an int
        date_clauses = _date_clauses(after, before)
        start_after = start_after_concept_id

        while True:
            keyset_clause = _COLLECTION_KEYSET_CLAUSE if start_after else ""
            keyset_bind = {"start_after": start_after} if start_after else {}

            page_sql = _COLLECTION_PAGE_IDS_SQL.format(
                table=table, collection_id=collection_id,
                keyset_clause=keyset_clause, page_size=page_size,
            )
            with self._acquire_cursor() as cur:
                cur.execute(page_sql, keyset_bind)
                page_rows = cur.fetchall()

            if not page_rows:
                break

            page_end = page_rows[-1][0]
            is_last_page = len(page_rows) < page_size

            chunk_sql = _COLLECTION_CHUNK_SQL.format(
                table=table, collection_id=collection_id, keyset_clause=keyset_clause,
                live_only_clause="" if include_deleted else _LIVE_ONLY_HAVING, **date_clauses,
            )
            with self._acquire_cursor() as cur:
                cur.execute(chunk_sql, {**keyset_bind, "page_end": page_end})
                rows = cur.fetchall()

            yield page_end, rows

            if is_last_page:
                break

            start_after = page_end

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
            table, _ = self._table(_provider_from_collection(concept_id), table_suffix)
        else:
            table = table_name

        sql = _SINGLE_CONCEPT_SQL.format(table=table, extra_cond="")
        bind: dict = {"concept_id": concept_id}

        with self._acquire_cursor() as cur:
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
        # Small providers share one table, streamed once for all of them.
        tables = dict.fromkeys(self._table(p, "_COLLECTIONS")[0] for p in self.get_all_provider_ids())
        for table in tables:
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
            with self._acquire_cursor() as cur:
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
            with self._acquire_cursor() as cur:
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
