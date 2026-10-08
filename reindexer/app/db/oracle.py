"""Oracle client (oracledb thin driver) for METADATA_DB.

Granules and collections live in per-provider tables ({PROVIDER}_GRANULES /
_COLLECTIONS), or the shared SMALL_PROV_* tables filtered by provider_id for small
providers; other concept types have one shared table each. All paging is keyset
(by id or concept_id).
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

_PROVIDERS_SQL = """\
SELECT provider_id, small
FROM METADATA_DB.providers
ORDER BY provider_id"""

# Providers flagged small share these tables, which carry a provider_id column
# (metadata-db's get-table-name).
_SMALL_PROVIDER_TABLE_PREFIX = "SMALL_PROV"

# Literals, not binds: bind peeking would reuse one plan across collections and date
# ranges of very different selectivity. after/before and collection_id are validated
# at the API, so embedding them is safe.
_AFTER_CLAUSE  = "AND REVISION_DATE >= TO_TIMESTAMP_TZ('{after}',  'YYYY-MM-DD\"T\"HH24:MI:SS TZH:TZM')"
_BEFORE_CLAUSE = "AND REVISION_DATE <= TO_TIMESTAMP_TZ('{before}', 'YYYY-MM-DD\"T\"HH24:MI:SS TZH:TZM')"

# ---------------------------------------------------------------------------
# Id-range granule scan (bootstrap-style)
# ---------------------------------------------------------------------------

# Bounded by an `id` (PK) range and date-filtered within it, like bootstrap's
# find-batch-starting-id-between-date-times. The ids are bound: every window has the
# same selectivity, so plan reuse helps.

_FIND_NEXT_ID_SQL = """\
SELECT MIN(id)
FROM METADATA_DB.{table}
WHERE id >= :min_id
{provider_clause}
{after_clause}
{before_clause}"""

# Every revision row, tombstones included; the scanner keeps the latest per concept.
_FETCH_ID_RANGE_CHUNK_SQL = """\
SELECT concept_id, revision_id, deleted
FROM METADATA_DB.{table}
WHERE id >= :start_id
  AND id < :end_id
{provider_clause}
{after_clause}
{before_clause}"""

# ---------------------------------------------------------------------------
# Per-collection granule scan
# ---------------------------------------------------------------------------

# Same two-query shape as _stream_concept_ids: an index-only scan for the page
# boundary, then an aggregation bounded to that page, which alone applies the date
# filters. Paging and aggregating by the same key (concept_id) means the boundary
# concept's latest revision is always seen. collection_id is a literal, like _AFTER_CLAUSE.

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
ORDER BY concept_id"""

# ---------------------------------------------------------------------------
# Shared / generic concept type SQL
# ---------------------------------------------------------------------------

# Joined by AND, so no leading AND.
_AFTER_COND  = "REVISION_DATE >= TO_TIMESTAMP_TZ(:after,  'YYYY-MM-DD\"T\"HH24:MI:SS TZH:TZM')"
_BEFORE_COND = "REVISION_DATE <= TO_TIMESTAMP_TZ(:before, 'YYYY-MM-DD\"T\"HH24:MI:SS TZH:TZM')"

# Two queries per page: an index scan for the page boundary, then an aggregation
# bounded to it (concept_id <= page_end). One query would GROUP BY the whole table
# before FETCH FIRST could apply.
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

# Internal concept type → (table, concept_id LIKE prefix). Generic subtypes are told
# apart by concept_id prefix, which uses the concept_id index; `schema` is unindexed.
# "generic" (no prefix) covers every subtype.
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

# concept-id prefix → (shared table, or None with the per-provider table suffix)
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
    """ISO8601 'Z' → ' +00:00', the form the 'TZH:TZM' format mask expects."""
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
        self._providers: dict[str, bool] = {}  # provider_id → small

    def _get_pool(self):
        if self._pool is None:
            with self._pool_lock:
                if self._pool is None:
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
        """No call timeout; tracked_call logs a call that hangs instead."""
        with tracked_call(), self._get_pool().acquire() as conn:
            with conn.cursor() as cur:
                yield cur

    def get_all_provider_ids(self) -> list[str]:
        with self._acquire_cursor() as cur:
            cur.execute(_PROVIDERS_SQL)
            rows = cur.fetchall()
        self._providers = {provider_id: bool(small) for provider_id, small in rows}
        return list(self._providers)

    def is_small_provider(self, provider_id: str) -> bool:
        if provider_id not in self._providers:
            self.get_all_provider_ids()  # also picks up providers added since the last load
        return self._providers.get(provider_id, False)

    def _table(self, provider_id: str, suffix: str) -> tuple[str, str]:
        """(table, provider_clause). The clause is only needed where rows aren't already
        scoped by a collection or concept id."""
        _validate_provider_id(provider_id)
        if self.is_small_provider(provider_id):
            return f"{_SMALL_PROVIDER_TABLE_PREFIX}{suffix}", f"AND provider_id = '{provider_id}'"
        return f"{provider_id}{suffix}", ""

    def find_next_granule_id_in_range(
        self,
        provider_id: str,
        min_id: int,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> Optional[int]:
        """Smallest granule id >= min_id revised within [after, before], as bootstrap's
        dated probe does. `before` only applies with `after`: every run has a `before`,
        and an undated probe must stay a single PK index lookup."""
        table, provider_clause = self._table(provider_id, "_GRANULES")
        sql = _FIND_NEXT_ID_SQL.format(
            table=table, provider_clause=provider_clause, **_date_clauses(after, before if after else None),
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
    ) -> list[tuple[str, int, int]]:
        """(concept_id, revision_id, deleted) for every row in [start_id, end_id)."""
        table, provider_clause = self._table(provider_id, "_GRANULES")
        sql = _FETCH_ID_RANGE_CHUNK_SQL.format(
            table=table, provider_clause=provider_clause, **_date_clauses(after, before),
        )
        with self._acquire_cursor() as cur:
            cur.execute(sql, {"start_id": start_id, "end_id": end_id})
            return cur.fetchall()

    def stream_granule_ids_paged(
        self,
        collection_id: str,
        chunk_size: int,
        after: Optional[str] = None,
        before: Optional[str] = None,
        start_after_concept_id: Optional[str] = None,
    ) -> Iterator[tuple[str, list[tuple[str, int, int]]]]:
        """Yield (page_end, rows) per page, resuming after start_after_concept_id. rows are
        (concept_id, revision_id, deleted) at each concept's latest revision in range. A
        page the date filter empties is still yielded, so the caller can save its cursor
        and check cancellation."""
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
                table=table, collection_id=collection_id, keyset_clause=keyset_clause, **date_clauses,
            )
            with self._acquire_cursor() as cur:
                cur.execute(chunk_sql, {**keyset_bind, "page_end": page_end})
                rows = cur.fetchall()

            yield page_end, rows

            if is_last_page:
                break

            start_after = page_end

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
        """Date filters apply only to the aggregation, so the page boundary advances
        regardless of which revisions are in range."""
        start_after: Optional[str] = None

        while True:
            # Page boundary
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

            # Aggregate the page
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
