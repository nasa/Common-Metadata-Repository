"""
Unit tests for OracleClient SQL generation.

Verifies that every concept type maps to the correct table name and optional
document_name filter, that date clauses are injected correctly, and that
get_concept_by_id routes each concept-id prefix to the right table.

No real Oracle connection is needed — oracledb.create_pool is mocked.

Run with:
    cd reindexer
    PYTHONPATH=. python -m pytest tests/test_oracle_sql.py -v
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

        mock_cur.fetchall.return_value = []
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
# get_concept_ids_by_type — table and document_name filter (all 10 types)
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("concept_type,expected_table,expected_doc_name", [
    ("variable",              "cmr_variables",         None),
    ("service",               "cmr_services",          None),
    ("tool",                  "cmr_tools",             None),
    ("subscription",          "cmr_subscriptions",     None),
    ("generic",               "cmr_generic_documents", None),
    ("data-quality-summary",  "cmr_generic_documents", "Data Quality Summary"),
    ("order-option",          "cmr_generic_documents", "Order Option"),
    ("grid",                  "cmr_generic_documents", "Grid"),
    ("citation",              "cmr_generic_documents", "Citation"),
    ("visualization",         "cmr_generic_documents", "Visualization"),
])
def test_get_concept_ids_by_type_table_and_filter(oracle, concept_type, expected_table, expected_doc_name):
    client, cur = oracle

    client.get_concept_ids_by_type(concept_type)

    sql, bind = _last_execute(cur)
    assert f"METADATA_DB.{expected_table}" in sql, (
        f"type={concept_type!r}: expected METADATA_DB.{expected_table} in SQL"
    )
    if expected_doc_name:
        assert "document_name = :document_name" in sql, (
            f"type={concept_type!r}: expected document_name filter in SQL"
        )
        assert bind.get("document_name") == expected_doc_name, (
            f"type={concept_type!r}: bind['document_name'] should be {expected_doc_name!r}"
        )
    else:
        assert "document_name" not in sql, (
            f"type={concept_type!r}: unexpected document_name filter in SQL"
        )
        assert "document_name" not in bind


def test_get_concept_ids_by_type_returns_concept_revision_tuples(oracle):
    client, cur = oracle
    cur.fetchall.return_value = [("V1234-PROV", 3), ("V5678-PROV", 1)]

    result = client.get_concept_ids_by_type("variable")

    assert result == [("V1234-PROV", 3), ("V5678-PROV", 1)]


def test_get_concept_ids_by_type_unknown_type_raises_value_error(oracle):
    client, _ = oracle
    with pytest.raises(ValueError, match="Unknown concept type"):
        client.get_concept_ids_by_type("does-not-exist")


# ---------------------------------------------------------------------------
# get_concept_ids_by_type — date clause injection
# ---------------------------------------------------------------------------

def test_after_injects_revision_date_ge_clause(oracle):
    client, cur = oracle

    client.get_concept_ids_by_type("variable", after="2024-01-01T00:00:00Z")

    sql, bind = _last_execute(cur)
    assert "REVISION_DATE >=" in sql
    assert "TO_TIMESTAMP_TZ(:after" in sql
    assert bind["after"] == "2024-01-01T00:00:00 +00:00"
    assert "REVISION_DATE <=" not in sql


def test_before_injects_revision_date_le_clause(oracle):
    client, cur = oracle

    client.get_concept_ids_by_type("variable", before="2024-12-31T23:59:59Z")

    sql, bind = _last_execute(cur)
    assert "REVISION_DATE <=" in sql
    assert "TO_TIMESTAMP_TZ(:before" in sql
    assert bind["before"] == "2024-12-31T23:59:59 +00:00"
    assert "REVISION_DATE >=" not in sql


def test_after_and_before_both_injected(oracle):
    client, cur = oracle

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


def test_generic_subtype_with_doc_filter_and_after_coexist(oracle):
    """document_name = :document_name AND REVISION_DATE >= must both appear in WHERE."""
    client, cur = oracle

    client.get_concept_ids_by_type("data-quality-summary", after="2024-03-01T00:00:00Z")

    sql, bind = _last_execute(cur)
    assert "METADATA_DB.cmr_generic_documents" in sql
    assert "document_name = :document_name" in sql
    assert "REVISION_DATE >=" in sql
    assert bind["document_name"] == "Data Quality Summary"
    assert bind["after"] == "2024-03-01T00:00:00 +00:00"


def test_generic_no_subtype_filter_with_after(oracle):
    """'generics' queries cmr_generic_documents with no document_name filter."""
    client, cur = oracle

    client.get_concept_ids_by_type("generic", after="2024-01-01T00:00:00Z")

    sql, bind = _last_execute(cur)
    assert "METADATA_DB.cmr_generic_documents" in sql
    assert "document_name" not in sql
    assert "REVISION_DATE >=" in sql
    assert "document_name" not in bind
    assert bind["after"] == "2024-01-01T00:00:00 +00:00"


# ---------------------------------------------------------------------------
# get_concept_ids_by_type — collection (per-provider tables)
# ---------------------------------------------------------------------------

def test_collection_type_queries_each_provider_table(oracle):
    client, cur = oracle
    cur.fetchall.side_effect = [
        [("PROV_A",), ("PROV_B",)],   # get_all_provider_ids
        [("C1-PROV_A", 1)],            # PROV_A_COLLECTIONS
        [("C2-PROV_B", 2)],            # PROV_B_COLLECTIONS
    ]

    result = client.get_concept_ids_by_type("collection")

    assert result == [("C1-PROV_A", 1), ("C2-PROV_B", 2)]
    assert cur.execute.call_count == 3

    executed_sqls = [c.args[0] for c in cur.execute.call_args_list]
    assert any("PROV_A_COLLECTIONS" in s for s in executed_sqls), "missing PROV_A_COLLECTIONS query"
    assert any("PROV_B_COLLECTIONS" in s for s in executed_sqls), "missing PROV_B_COLLECTIONS query"


def test_collection_type_with_after_passes_bind_to_each_provider(oracle):
    client, cur = oracle
    cur.fetchall.side_effect = [
        [("PROV_X",)],          # get_all_provider_ids
        [("C1-PROV_X", 1)],     # PROV_X_COLLECTIONS
    ]

    client.get_concept_ids_by_type("collection", after="2024-06-01T00:00:00Z")

    # The collection query (second call) should include after bind
    coll_call = cur.execute.call_args_list[1]
    coll_sql = coll_call.args[0]
    coll_bind = coll_call.args[1]
    assert "PROV_X_COLLECTIONS" in coll_sql
    assert "REVISION_DATE >=" in coll_sql
    assert coll_bind["after"] == "2024-06-01T00:00:00 +00:00"


def test_collection_type_no_providers_returns_empty(oracle):
    client, cur = oracle
    cur.fetchall.side_effect = [[]]  # no providers

    result = client.get_concept_ids_by_type("collection")

    assert result == []
    assert cur.execute.call_count == 1  # only the providers query, no collection queries


# ---------------------------------------------------------------------------
# get_concept_by_id — prefix → table routing (all 11 prefixes)
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("concept_id,expected_table,expected_doc_name", [
    ("C1234567890-MYPROV",    "MYPROV_COLLECTIONS",   None),
    ("G1234567890-MYPROV",    "MYPROV_GRANULES",      None),
    ("V1234567890-MYPROV",    "cmr_variables",        None),
    ("S1234567890-MYPROV",    "cmr_services",         None),
    ("TL1234567890-MYPROV",   "cmr_tools",            None),
    ("SUB1234567890-MYPROV",  "cmr_subscriptions",    None),
    ("DQS1234567890-MYPROV",  "cmr_generic_documents", "Data Quality Summary"),
    ("OO1234567890-MYPROV",   "cmr_generic_documents", "Order Option"),
    ("GRD1234567890-MYPROV",  "cmr_generic_documents", "Grid"),
    ("CIT1234567890-MYPROV",  "cmr_generic_documents", "Citation"),
    ("VIS1234567890-MYPROV",  "cmr_generic_documents", "Visualization"),
])
def test_get_concept_by_id_correct_table_and_filter(oracle, concept_id, expected_table, expected_doc_name):
    client, cur = oracle
    cur.fetchone.return_value = (concept_id, 5)

    result = client.get_concept_by_id(concept_id)

    assert result == {"concept-id": concept_id, "revision-id": 5}

    sql, bind = _last_execute(cur)
    assert f"METADATA_DB.{expected_table}" in sql, (
        f"concept_id={concept_id!r}: expected METADATA_DB.{expected_table} in SQL"
    )
    assert bind["concept_id"] == concept_id

    if expected_doc_name:
        assert "AND document_name = :document_name" in sql, (
            f"concept_id={concept_id!r}: expected document_name filter in SQL"
        )
        assert bind["document_name"] == expected_doc_name
    else:
        assert "document_name" not in bind, (
            f"concept_id={concept_id!r}: unexpected document_name in bind"
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
