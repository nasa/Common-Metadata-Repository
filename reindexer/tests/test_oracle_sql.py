"""
Unit tests for OracleClient SQL generation.

Verifies that every concept type maps to the correct table name and optional
schema filter, that date clauses are injected correctly, and that
get_concept_by_id routes each concept-id prefix to the right table.

No real Oracle connection is needed — oracledb.create_pool is mocked.
"""
import sys
from unittest.mock import MagicMock, patch

import pytest

# Allow running without the native oracledb driver installed
if "oracledb" not in sys.modules:
    sys.modules["oracledb"] = MagicMock()


# ---------------------------------------------------------------------------
# Fixture
# ---------------------------------------------------------------------------

@pytest.fixture
def oracle():
    """Yields (OracleClient, mock_cursor).  create_pool is mocked out."""
    with patch("oracledb.create_pool") as mock_create_pool:
        from app.db.oracle import OracleClient
        client = OracleClient()

        pool = mock_create_pool.return_value  # == client._pool
        mock_cur = MagicMock()
        mock_conn = MagicMock()
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        pool.acquire.return_value.__enter__.return_value = mock_conn

        # fetchall: used by get_all_provider_ids and _stream_concept_ids (keyset pages).
        # fetchmany: used by stream_granule_ids (single open cursor for a full collection).
        mock_cur.fetchall.return_value = []
        mock_cur.fetchmany.return_value = []
        mock_cur.fetchone.return_value = None

        yield client, mock_cur


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------

def _last_execute(cur):
    """Return (sql, bind) from the most recent cur.execute() call."""
    args = cur.execute.call_args.args
    # Some calls pass only sql (no bind dict), e.g. _PROVIDERS_SQL
    sql = args[0]
    bind = args[1] if len(args) > 1 else {}
    return sql, bind


# ---------------------------------------------------------------------------
# get_concept_ids_by_type — table and concept_id prefix filter (all 10 types)
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("concept_type,expected_table,expected_prefix", [
    ("variable",              "cmr_variables",         None),
    ("service",               "cmr_services",          None),
    ("tool",                  "cmr_tools",             None),
    ("subscription",          "cmr_subscriptions",     None),
    ("generic",               "cmr_generic_documents", None),
    ("data-quality-summary",  "cmr_generic_documents", "DQS%"),
    ("order-option",          "cmr_generic_documents", "OO%"),
    ("grid",                  "cmr_generic_documents", "GRD%"),
    ("citation",              "cmr_generic_documents", "CIT%"),
    ("visualization",         "cmr_generic_documents", "VIS%"),
])
def test_get_concept_ids_by_type_table_and_filter(oracle, concept_type, expected_table, expected_prefix):
    client, cur = oracle

    client.get_concept_ids_by_type(concept_type)

    sql, bind = _last_execute(cur)
    assert f"METADATA_DB.{expected_table}" in sql, (
        f"type={concept_type!r}: expected METADATA_DB.{expected_table} in SQL"
    )
    if expected_prefix:
        assert "concept_id LIKE :prefix" in sql, (
            f"type={concept_type!r}: expected concept_id LIKE filter in SQL"
        )
        assert bind.get("prefix") == expected_prefix, (
            f"type={concept_type!r}: bind['prefix'] should be {expected_prefix!r}"
        )
    else:
        assert "LIKE" not in sql, (
            f"type={concept_type!r}: unexpected LIKE filter in SQL"
        )
        assert "prefix" not in bind


def test_get_concept_ids_by_type_returns_concept_revision_tuples(oracle):
    client, cur = oracle
    # Two queries are always issued for a non-empty page:
    #   call 0 — page_ids (SELECT DISTINCT concept_id → 1-tuples; 2 rows < _BATCH_SIZE → is_last_page=True)
    #   call 1 — agg (SELECT concept_id, MAX(revision_id) → 2-tuples; is_last_page causes break after)
    cur.fetchall.side_effect = [
        [("V1234-PROV",), ("V5678-PROV",)],       # page_ids query: 1-tuples (DISTINCT concept_id)
        [("V1234-PROV", 3), ("V5678-PROV", 1)],   # agg query: 2-tuples (concept_id, revision_id)
    ]

    result = client.get_concept_ids_by_type("variable")

    assert result == [("V1234-PROV", 3), ("V5678-PROV", 1)]
    assert cur.execute.call_count == 2


def test_get_concept_ids_by_type_unknown_type_raises_value_error(oracle):
    client, _ = oracle
    with pytest.raises(ValueError, match="Unknown concept type"):
        client.get_concept_ids_by_type("does-not-exist")


# ---------------------------------------------------------------------------
# get_concept_ids_by_type — date clause injection
# ---------------------------------------------------------------------------

def test_after_injects_revision_date_ge_clause(oracle):
    client, cur = oracle
    # page_ids query (call 0) returns one boundary so the agg query (call 1) executes.
    cur.fetchall.side_effect = [[("V1-PROV",)], []]

    client.get_concept_ids_by_type("variable", after="2024-01-01T00:00:00Z")

    # Date filters live in the aggregation query (second execute call).
    sql, bind = _last_execute(cur)
    assert "REVISION_DATE >=" in sql
    assert "TO_TIMESTAMP_TZ(:after" in sql
    assert bind["after"] == "2024-01-01T00:00:00 +00:00"
    assert "REVISION_DATE <=" not in sql


def test_before_injects_revision_date_le_clause(oracle):
    client, cur = oracle
    cur.fetchall.side_effect = [[("V1-PROV",)], []]

    client.get_concept_ids_by_type("variable", before="2024-12-31T23:59:59Z")

    sql, bind = _last_execute(cur)
    assert "REVISION_DATE <=" in sql
    assert "TO_TIMESTAMP_TZ(:before" in sql
    assert bind["before"] == "2024-12-31T23:59:59 +00:00"
    assert "REVISION_DATE >=" not in sql


def test_after_and_before_both_injected(oracle):
    client, cur = oracle
    cur.fetchall.side_effect = [[("S1-PROV",)], []]

    client.get_concept_ids_by_type(
        "service",
        after="2024-01-01T00:00:00Z",
        before="2024-06-30T23:59:59Z",
    )

    sql, bind = _last_execute(cur)
    assert "REVISION_DATE >=" in sql
    assert "REVISION_DATE <=" in sql
    assert bind["after"] == "2024-01-01T00:00:00 +00:00"
    assert bind["before"] == "2024-06-30T23:59:59 +00:00"


def test_no_dates_produces_no_where_clause(oracle):
    client, cur = oracle

    client.get_concept_ids_by_type("tool")

    sql, bind = _last_execute(cur)
    assert "WHERE" not in sql
    assert "REVISION_DATE" not in sql
    assert not bind


def test_generic_subtype_with_prefix_filter_and_after_coexist(oracle):
    """concept_id LIKE :prefix AND REVISION_DATE >= must both appear in the agg query."""
    client, cur = oracle
    cur.fetchall.side_effect = [[("DQS1-PROV",)], []]

    client.get_concept_ids_by_type("data-quality-summary", after="2024-03-01T00:00:00Z")

    sql, bind = _last_execute(cur)
    assert "METADATA_DB.cmr_generic_documents" in sql
    assert "concept_id LIKE :prefix" in sql
    assert "REVISION_DATE >=" in sql
    assert bind["prefix"] == "DQS%"
    assert bind["after"] == "2024-03-01T00:00:00 +00:00"


def test_generic_no_subtype_filter_with_after(oracle):
    """'generics' agg query has no prefix filter but does carry the date condition."""
    client, cur = oracle
    cur.fetchall.side_effect = [[("GEN1-PROV",)], []]

    client.get_concept_ids_by_type("generic", after="2024-01-01T00:00:00Z")

    sql, bind = _last_execute(cur)
    assert "METADATA_DB.cmr_generic_documents" in sql
    assert "LIKE" not in sql
    assert "REVISION_DATE >=" in sql
    assert "prefix" not in bind
    assert bind["after"] == "2024-01-01T00:00:00 +00:00"


# ---------------------------------------------------------------------------
# get_concept_ids_by_type — collection (per-provider tables)
# ---------------------------------------------------------------------------

def test_collection_type_queries_each_provider_table(oracle):
    client, cur = oracle
    # Two queries per provider (page_ids + agg); each returns 1 row → last page.
    cur.fetchall.side_effect = [
        [("PROV_A",), ("PROV_B",)],  # get_all_provider_ids
        [("C1-PROV_A",)],             # PROV_A page_ids (boundary)
        [("C1-PROV_A", 1)],           # PROV_A agg
        [("C2-PROV_B",)],             # PROV_B page_ids (boundary)
        [("C2-PROV_B", 2)],           # PROV_B agg
    ]

    result = client.get_concept_ids_by_type("collection")

    assert result == [("C1-PROV_A", 1), ("C2-PROV_B", 2)]
    assert cur.execute.call_count == 5

    executed_sqls = [c.args[0] for c in cur.execute.call_args_list]
    assert any("PROV_A_COLLECTIONS" in s for s in executed_sqls), "missing PROV_A_COLLECTIONS query"
    assert any("PROV_B_COLLECTIONS" in s for s in executed_sqls), "missing PROV_B_COLLECTIONS query"


def test_collection_type_with_after_passes_bind_to_each_provider(oracle):
    client, cur = oracle
    # providers + PROV_X page_ids + PROV_X agg
    cur.fetchall.side_effect = [[("PROV_X",)], [("C1-PROV_X",)], [("C1-PROV_X", 1)]]

    client.get_concept_ids_by_type("collection", after="2024-06-01T00:00:00Z")

    # Agg query is the third execute call (index 2); it carries the date bind.
    agg_call = cur.execute.call_args_list[2]
    agg_sql = agg_call.args[0]
    agg_bind = agg_call.args[1]
    assert "PROV_X_COLLECTIONS" in agg_sql
    assert "REVISION_DATE >=" in agg_sql
    assert agg_bind["after"] == "2024-06-01T00:00:00 +00:00"


def test_collection_type_no_providers_returns_empty(oracle):
    client, cur = oracle
    # fetchall returns [] for providers → no collection queries issued
    result = client.get_concept_ids_by_type("collection")

    assert result == []
    assert cur.execute.call_count == 1


def test_stream_concept_ids_issues_second_query_with_keyset_when_page_is_full(oracle):
    """Full first page triggers a second page_ids query using concept_id > last boundary."""
    from app.db.oracle import _BATCH_SIZE
    client, cur = oracle
    # page_ids returns concept_ids only; agg returns (concept_id, revision_id) tuples.
    full_page_ids = [(f"V{i:04d}-PROV",) for i in range(_BATCH_SIZE)]
    full_page_agg = [(f"V{i:04d}-PROV", i) for i in range(_BATCH_SIZE)]
    # page_ids(1) → full → agg(1) → results; page_ids(2) → empty → stop
    cur.fetchall.side_effect = [full_page_ids, full_page_agg, []]

    result = client.get_concept_ids_by_type("variable")

    assert len(result) == _BATCH_SIZE
    assert cur.execute.call_count == 3

    # Third execute call is the second-page page_ids query; it must carry the keyset.
    second_page_sql, second_page_bind = cur.execute.call_args_list[2].args[:2]
    assert "concept_id > :start_after" in second_page_sql
    assert second_page_bind["start_after"] == f"V{_BATCH_SIZE - 1:04d}-PROV"


# ---------------------------------------------------------------------------
# get_concept_by_id — prefix → table routing (all 11 prefixes)
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("concept_id,expected_table", [
    ("C1234567890-MYPROV",    "MYPROV_COLLECTIONS"),
    ("G1234567890-MYPROV",    "MYPROV_GRANULES"),
    ("V1234567890-MYPROV",    "cmr_variables"),
    ("S1234567890-MYPROV",    "cmr_services"),
    ("TL1234567890-MYPROV",   "cmr_tools"),
    ("SUB1234567890-MYPROV",  "cmr_subscriptions"),
    ("DQS1234567890-MYPROV",  "cmr_generic_documents"),
    ("OO1234567890-MYPROV",   "cmr_generic_documents"),
    ("GRD1234567890-MYPROV",  "cmr_generic_documents"),
    ("CIT1234567890-MYPROV",  "cmr_generic_documents"),
    ("VIS1234567890-MYPROV",  "cmr_generic_documents"),
])
def test_get_concept_by_id_correct_table_and_filter(oracle, concept_id, expected_table):
    client, cur = oracle
    cur.fetchone.return_value = (concept_id, 5)

    result = client.get_concept_by_id(concept_id)

    assert result == {"concept-id": concept_id, "revision-id": 5}

    sql, bind = _last_execute(cur)
    assert f"METADATA_DB.{expected_table}" in sql, (
        f"concept_id={concept_id!r}: expected METADATA_DB.{expected_table} in SQL"
    )
    assert bind["concept_id"] == concept_id
    assert "document_name" not in bind, (
        f"concept_id={concept_id!r}: document_name filter is redundant for single-concept lookup"
    )


def test_get_concept_by_id_not_found_in_db_returns_none(oracle):
    client, cur = oracle
    cur.fetchone.return_value = None

    result = client.get_concept_by_id("V1234-PROV")

    assert result is None


def test_get_concept_by_id_unknown_prefix_returns_none_without_any_db_call(oracle):
    client, cur = oracle

    result = client.get_concept_by_id("BADPREFIX1234-PROV")

    assert result is None
    cur.execute.assert_not_called()


def test_get_concept_by_id_provider_with_underscores_extracted_correctly(oracle):
    """Provider like MY_PROV_01 must appear in full in the table name."""
    client, cur = oracle
    cur.fetchone.return_value = ("C99-MY_PROV_01", 1)

    client.get_concept_by_id("C99-MY_PROV_01")

    sql, _ = _last_execute(cur)
    assert "METADATA_DB.MY_PROV_01_COLLECTIONS" in sql


def test_get_concept_by_id_granule_goes_to_granules_table(oracle):
    client, cur = oracle
    cur.fetchone.return_value = ("G55-PROV", 2)

    client.get_concept_by_id("G55-PROV")

    sql, _ = _last_execute(cur)
    assert "PROV_GRANULES" in sql
    assert "PROV_COLLECTIONS" not in sql


# ---------------------------------------------------------------------------
# get_collection_ids_for_provider — direct SQL verification
# ---------------------------------------------------------------------------

def test_get_collection_ids_for_provider_queries_correct_table(oracle):
    client, cur = oracle
    cur.fetchall.return_value = [("C1-MYPROV",), ("C2-MYPROV",)]

    result = client.get_collection_ids_for_provider("MYPROV")

    assert result == ["C1-MYPROV", "C2-MYPROV"]
    sql, bind = _last_execute(cur)
    assert "METADATA_DB.MYPROV_COLLECTIONS" in sql
    # Collections SQL has no per-row date clause; keyset and PARENT_COLLECTION_ID are absent
    assert "REVISION_DATE" not in sql
    assert "PARENT_COLLECTION_ID" not in sql


def test_get_collection_ids_for_provider_rejects_invalid_provider_id(oracle):
    client, _ = oracle
    with pytest.raises(ValueError, match="Invalid provider ID"):
        client.get_collection_ids_for_provider("bad-provider!")


# ---------------------------------------------------------------------------
# stream_granule_ids — SQL and keyset clause verification
# ---------------------------------------------------------------------------

def _drain_stream(gen):
    """Exhaust the stream_granule_ids generator so cursor.execute() is called."""
    return list(gen)


def test_stream_granule_ids_queries_correct_granule_table(oracle):
    client, cur = oracle
    cur.fetchmany.return_value = []

    _drain_stream(client.stream_granule_ids("C1234-PROV", chunk_size=500))

    sql, bind = _last_execute(cur)
    assert "METADATA_DB.PROV_GRANULES" in sql
    assert bind["collection_id"] == "C1234-PROV"


def test_stream_granule_ids_filters_by_collection_id(oracle):
    client, cur = oracle
    cur.fetchmany.return_value = []

    _drain_stream(client.stream_granule_ids("C99-TESTPROV", chunk_size=500))

    sql, bind = _last_execute(cur)
    assert "PARENT_COLLECTION_ID = :collection_id" in sql
    assert bind["collection_id"] == "C99-TESTPROV"


def test_stream_granule_ids_keyset_clause_present_when_start_after_given(oracle):
    client, cur = oracle
    cur.fetchmany.return_value = []

    _drain_stream(client.stream_granule_ids(
        "C1234-PROV", chunk_size=500, start_after_concept_id="G100-PROV"
    ))

    sql, bind = _last_execute(cur)
    assert "concept_id > :start_after" in sql
    assert bind["start_after"] == "G100-PROV"


def test_stream_granule_ids_no_keyset_clause_when_start_after_is_none(oracle):
    client, cur = oracle
    cur.fetchmany.return_value = []

    _drain_stream(client.stream_granule_ids("C1234-PROV", chunk_size=500))

    sql, _ = _last_execute(cur)
    assert "start_after" not in sql


def test_stream_granule_ids_after_injects_revision_date_ge(oracle):
    client, cur = oracle
    cur.fetchmany.return_value = []

    _drain_stream(client.stream_granule_ids(
        "C1-PROV", chunk_size=500, after="2024-01-01T00:00:00Z"
    ))

    sql, bind = _last_execute(cur)
    assert "REVISION_DATE >=" in sql
    assert bind["after"] == "2024-01-01T00:00:00 +00:00"


def test_stream_granule_ids_before_injects_revision_date_le(oracle):
    client, cur = oracle
    cur.fetchmany.return_value = []

    _drain_stream(client.stream_granule_ids(
        "C1-PROV", chunk_size=500, before="2024-12-31T23:59:59Z"
    ))

    sql, bind = _last_execute(cur)
    assert "REVISION_DATE <=" in sql
    assert bind["before"] == "2024-12-31T23:59:59 +00:00"


def test_stream_concept_ids_full_page_with_sparse_agg_advances_keyset(oracle):
    """Full page_ids triggers a second page even when the agg returns fewer rows.

    When page_ids returns exactly _BATCH_SIZE concept_ids (is_last_page=False)
    but the HAVING filter deletes most of them, the keyset cursor must advance
    to the last boundary from page_ids (page_end), not the last agg result.
    """
    from app.db.oracle import _BATCH_SIZE
    client, cur = oracle

    full_page_ids = [(f"V{i:04d}-PROV",) for i in range(_BATCH_SIZE)]
    # Only 3 of the _BATCH_SIZE concepts survived the HAVING filter
    sparse_agg = [("V0010-PROV", 2), ("V0200-PROV", 5), ("V0499-PROV", 1)]
    # Second page_ids returns empty → stop
    cur.fetchall.side_effect = [full_page_ids, sparse_agg, []]

    result = client.get_concept_ids_by_type("variable")

    # Result contains only the live concepts from the agg query, not all page_ids rows
    assert result == sparse_agg
    # Three execute calls: page_ids(1), agg(1), page_ids(2)
    assert cur.execute.call_count == 3

    # The second page_ids call must use page_end = page_ids[-1][0] (the full-page boundary),
    # not the last concept_id from the sparse agg result
    second_page_sql, second_page_bind = cur.execute.call_args_list[2].args[:2]
    assert "concept_id > :start_after" in second_page_sql
    assert second_page_bind["start_after"] == f"V{_BATCH_SIZE - 1:04d}-PROV"


def test_stream_granule_ids_yields_multiple_chunks(oracle):
    """stream_granule_ids yields one list per fetchmany batch, not individual rows."""
    client, cur = oracle
    cur.fetchmany.side_effect = [
        [("G1-PROV", 1), ("G2-PROV", 2)],
        [("G3-PROV", 3)],
        [],
    ]

    chunks = _drain_stream(client.stream_granule_ids("C1234-PROV", chunk_size=2))

    assert len(chunks) == 2
    assert chunks[0] == [("G1-PROV", 1), ("G2-PROV", 2)]
    assert chunks[1] == [("G3-PROV", 3)]
