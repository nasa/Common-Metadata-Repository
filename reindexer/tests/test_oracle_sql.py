"""OracleClient SQL: table routing, date and keyset clauses, and the scan query shapes
that keep them on their indexes. oracledb.create_pool is mocked."""
import sys
from unittest.mock import MagicMock, patch

import pytest

# Allow running without the native oracledb driver installed
if "oracledb" not in sys.modules:
    sys.modules["oracledb"] = MagicMock()


@pytest.fixture
def oracle():
    """Yields (OracleClient, mock_cursor)."""
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
        # Per-provider tables, without a providers lookup; small-provider tests opt out.
        client.is_small_provider = lambda provider_id: False

        yield client, mock_cur


def _last_execute(cur):
    args = cur.execute.call_args.args
    return args[0], (args[1] if len(args) > 1 else {})


# ---------------------------------------------------------------------------
# stream_concept_ids_by_type — shared tables
# ---------------------------------------------------------------------------

def test_every_route_type_has_a_table():
    from app.db.oracle import _SHARED_TYPE_TABLES
    from app.routers.reindex import ROUTE_TO_INTERNAL_TYPE
    assert all(t in _SHARED_TYPE_TABLES or t == "collection" for t in ROUTE_TO_INTERNAL_TYPE.values())


@pytest.mark.parametrize("concept_type, table, prefix", [
    ("variable",             "cmr_variables",         None),
    ("service",              "cmr_services",          None),
    ("tool",                 "cmr_tools",             None),
    ("subscription",         "cmr_subscriptions",     None),
    ("generic",              "cmr_generic_documents", None),
    ("data-quality-summary", "cmr_generic_documents", "DQS%"),
    ("order-option",         "cmr_generic_documents", "OO%"),
    ("grid",                 "cmr_generic_documents", "GRD%"),
    ("citation",             "cmr_generic_documents", "CIT%"),
    ("visualization",        "cmr_generic_documents", "VIS%"),
])
def test_shared_type_table_and_prefix(oracle, concept_type, table, prefix):
    client, cur = oracle
    list(client.stream_concept_ids_by_type(concept_type))
    sql, bind = _last_execute(cur)
    assert f"METADATA_DB.{table}" in sql
    assert ("concept_id LIKE :prefix" in sql) is bool(prefix)
    assert bind.get("prefix") == prefix


@pytest.mark.parametrize("after, before", [
    ("2024-01-01T00:00:00Z", None),
    (None, "2024-12-31T23:59:59Z"),
    ("2024-01-01T00:00:00Z", "2024-12-31T23:59:59Z"),
])
def test_date_filters_apply_to_the_aggregation_only(oracle, after, before):
    """Keeping dates out of the page query keeps its keyset cursor monotonic."""
    client, cur = oracle
    cur.fetchall.side_effect = [[("DQS1-PROV",)], []]
    list(client.stream_concept_ids_by_type("data-quality-summary", after=after, before=before))
    (page_sql, _), (agg_sql, agg_bind) = [c.args[:2] for c in cur.execute.call_args_list]
    assert "REVISION_DATE" not in page_sql
    assert "concept_id LIKE :prefix" in agg_sql
    assert ("REVISION_DATE >=" in agg_sql) is bool(after)
    assert ("REVISION_DATE <=" in agg_sql) is bool(before)
    assert agg_bind.get("after") == (after and after.replace("Z", " +00:00"))
    assert agg_bind.get("before") == (before and before.replace("Z", " +00:00"))


def test_collection_type_queries_each_table_once(oracle):
    """Small providers share SMALL_PROV_COLLECTIONS, streamed once for all of them."""
    client, cur = oracle
    del client.is_small_provider
    cur.fetchall.side_effect = [
        [("PROV_A", 0), ("SMALL_A", 1), ("SMALL_B", 1)],  # get_all_provider_ids
        [("C1-PROV_A",)], [("C1-PROV_A", 1)],             # PROV_A page, agg
        [("C2-SMALL_A",)], [("C2-SMALL_A", 2)],           # SMALL_PROV page, agg
    ]
    assert list(client.stream_concept_ids_by_type("collection")) == [("C1-PROV_A", 1), ("C2-SMALL_A", 2)]
    sqls = [c.args[0] for c in cur.execute.call_args_list]
    assert len(sqls) == 5
    assert "PROV_A_COLLECTIONS" in sqls[1]
    assert "SMALL_PROV_COLLECTIONS" in sqls[3]


def test_full_page_with_sparse_aggregation_advances_past_the_page(oracle):
    """The next page starts after the page query's last concept, not the last live one."""
    from app.db.oracle import _BATCH_SIZE
    client, cur = oracle
    full_page_ids = [(f"V{i:04d}-PROV",) for i in range(_BATCH_SIZE)]
    sparse_agg = [("V0010-PROV", 2), ("V0200-PROV", 5)]
    cur.fetchall.side_effect = [full_page_ids, sparse_agg, []]
    assert list(client.stream_concept_ids_by_type("variable")) == sparse_agg
    assert cur.execute.call_count == 3
    second_page_sql, second_page_bind = cur.execute.call_args_list[2].args[:2]
    assert "concept_id > :start_after" in second_page_sql
    assert second_page_bind["start_after"] == f"V{_BATCH_SIZE - 1:04d}-PROV"


# ---------------------------------------------------------------------------
# get_concept_by_id — prefix → table routing
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("concept_id, table", [
    ("C99-MY_PROV_01",       "MY_PROV_01_COLLECTIONS"),
    ("G1234567890-MYPROV",   "MYPROV_GRANULES"),
    ("V1234567890-MYPROV",   "cmr_variables"),
    ("S1234567890-MYPROV",   "cmr_services"),
    ("TL1234567890-MYPROV",  "cmr_tools"),
    ("SUB1234567890-MYPROV", "cmr_subscriptions"),
    ("DQS1234567890-MYPROV", "cmr_generic_documents"),
    ("OO1234567890-MYPROV",  "cmr_generic_documents"),
    ("GRD1234567890-MYPROV", "cmr_generic_documents"),  # not a granule
    ("CIT1234567890-MYPROV", "cmr_generic_documents"),
    ("VIS1234567890-MYPROV", "cmr_generic_documents"),
])
def test_get_concept_by_id_table(oracle, concept_id, table):
    client, cur = oracle
    cur.fetchone.return_value = (concept_id, 5)
    assert client.get_concept_by_id(concept_id) == {"concept-id": concept_id, "revision-id": 5}
    sql, bind = _last_execute(cur)
    assert f"METADATA_DB.{table}" in sql
    assert bind["concept_id"] == concept_id


def test_get_concept_by_id_not_found_returns_none(oracle):
    client, cur = oracle
    assert client.get_concept_by_id("V1234-PROV") is None


def test_get_concept_by_id_unknown_prefix_returns_none_without_querying(oracle):
    client, cur = oracle
    assert client.get_concept_by_id("BADPREFIX1234-PROV") is None
    cur.execute.assert_not_called()


@pytest.mark.parametrize("provider_id", ["PROV\n", "P'--"])
def test_invalid_provider_id_rejected_before_any_query(oracle, provider_id):
    """provider_id becomes part of a table name, so it is validated, never bound."""
    client, cur = oracle
    with pytest.raises(ValueError, match="Invalid provider ID"):
        client.fetch_granule_id_range_chunk(provider_id, 0, 100)
    cur.execute.assert_not_called()


# ---------------------------------------------------------------------------
# find_next_granule_id_in_range
# ---------------------------------------------------------------------------

class TestFindNextGranuleIdInRange:

    def test_sql_shape(self, oracle):
        client, cur = oracle
        cur.fetchone.return_value = (42,)
        client.find_next_granule_id_in_range("MYPROV", 12345)
        sql, bind = _last_execute(cur)
        assert "METADATA_DB.MYPROV_GRANULES" in sql
        assert "deleted" not in sql
        assert "REVISION_DATE" not in sql
        assert "12345" not in sql
        assert bind == {"min_id": 12345}

    @pytest.mark.parametrize("row, expected", [((999,), 999), ((None,), None)])
    def test_returns_found_id_or_none(self, oracle, row, expected):
        client, cur = oracle
        cur.fetchone.return_value = row
        assert client.find_next_granule_id_in_range("MYPROV", 0) == expected

    def test_after_embedded_as_literal(self, oracle):
        client, cur = oracle
        cur.fetchone.return_value = (1,)
        client.find_next_granule_id_in_range("MYPROV", 0, after="2024-01-01T00:00:00Z")
        sql, bind = _last_execute(cur)
        assert "REVISION_DATE >= TO_TIMESTAMP_TZ('2024-01-01T00:00:00 +00:00'" in sql
        assert bind == {"min_id": 0}


# ---------------------------------------------------------------------------
# fetch_granule_id_range_chunk
# ---------------------------------------------------------------------------

def test_fetch_granule_id_range_chunk_sql_shape(oracle):
    client, cur = oracle
    client.fetch_granule_id_range_chunk("MYPROV", 500, 1000, after="2024-01-01T00:00:00Z")
    sql, bind = _last_execute(cur)
    assert "METADATA_DB.MYPROV_GRANULES" in sql
    assert "id >= :start_id" in sql
    assert "id < :end_id" in sql
    assert "deleted = 0" not in sql  # tombstones are dispatched as deletes
    assert "GROUP BY" not in sql
    assert "2024-01-01T00:00:00 +00:00" in sql
    assert bind == {"start_id": 500, "end_id": 1000}


# ---------------------------------------------------------------------------
# stream_granule_ids_paged
# ---------------------------------------------------------------------------

class TestStreamGranuleIdsPaged:

    def test_page_query_shape(self, oracle):
        client, cur = oracle
        result = list(client.stream_granule_ids_paged(
            "C1-MYPROV", chunk_size="500", after="2024-01-01T00:00:00Z",
        ))
        assert result == []
        assert cur.execute.call_count == 1  # no aggregation query for an empty page
        sql, bind = _last_execute(cur)
        assert "METADATA_DB.MYPROV_GRANULES" in sql
        assert "'C1-MYPROV'" in sql
        assert "DISTINCT" not in sql  # would hash+sort the whole collection every page
        assert "ORDER BY concept_id, revision_id" in sql  # *_PCR index order → stops at the page
        assert "FETCH FIRST 500 ROWS ONLY" in sql
        assert "REVISION_DATE" not in sql
        assert "start_after" not in sql
        assert bind == {}

    def test_aggregation_query_shape(self, oracle):
        client, cur = oracle
        cur.fetchall.side_effect = [
            [("G1-PROV",), ("G2-PROV",), ("G3-PROV",)],  # page (short → last page)
            [("G1-PROV", 2), ("G2-PROV", 1)],            # aggregation
        ]
        result = list(client.stream_granule_ids_paged(
            "C1-PROV", chunk_size=500, after="2024-01-01T00:00:00Z", before="2024-06-30T23:59:59Z",
        ))
        assert result == [("G3-PROV", [("G1-PROV", 2), ("G2-PROV", 1)])]
        agg_sql, agg_bind = cur.execute.call_args_list[1].args[:2]
        assert "'C1-PROV'" in agg_sql
        assert "concept_id <= :page_end" in agg_sql
        assert "deleted IN (0, 1)" in agg_sql  # lets *_PDCR seek by concept_id range
        assert "GROUP BY concept_id" in agg_sql
        assert "HAVING" not in agg_sql  # a latest tombstone is returned, flagged deleted
        assert "2024-01-01T00:00:00 +00:00" in agg_sql
        assert "2024-06-30T23:59:59 +00:00" in agg_sql
        assert agg_bind == {"page_end": "G3-PROV"}

    def test_resume_cursor_applied_to_both_queries(self, oracle):
        client, cur = oracle
        cur.fetchall.side_effect = [[("G0101-PROV",), ("G0102-PROV",)], [("G0101-PROV", 1)]]
        list(client.stream_granule_ids_paged("C1-PROV", chunk_size=500, start_after_concept_id="G0100-PROV"))
        (page_sql, page_bind), (agg_sql, agg_bind) = [c.args[:2] for c in cur.execute.call_args_list]
        assert "concept_id > :start_after" in page_sql
        assert "concept_id > :start_after" in agg_sql
        assert page_bind == {"start_after": "G0100-PROV"}
        assert agg_bind == {"start_after": "G0100-PROV", "page_end": "G0102-PROV"}

    def test_page_ending_mid_concept_resumes_after_that_concept(self, oracle):
        """The row limit can land partway through a concept's revisions; the
        aggregation still covers all of them (concept_id <= page_end)."""
        client, cur = oracle
        cur.fetchall.side_effect = [
            [("G1-PROV",), ("G1-PROV",)],  # page 1: full at chunk_size=2, both rows G1
            [("G1-PROV", 3)],              # agg 1
            [],                            # page 2: empty → stop
        ]
        result = list(client.stream_granule_ids_paged("C1-PROV", chunk_size=2))
        assert result == [("G1-PROV", [("G1-PROV", 3)])]
        assert cur.execute.call_args_list[2].args[1] == {"start_after": "G1-PROV"}

    def test_yields_every_page_including_filtered_ones(self, oracle):
        """A fully date-filtered page still yields (page_end, []) so the caller can
        save its cursor and check cancellation; the next page starts after its page_end."""
        client, cur = oracle
        cur.fetchall.side_effect = [
            [("G1-PROV",), ("G2-PROV",)],  # page 1 (full at chunk_size=2)
            [],                            # agg 1: all filtered out
            [("G3-PROV",)],                # page 2 (short → last page)
            [("G3-PROV", 4)],              # agg 2
        ]
        result = list(client.stream_granule_ids_paged("C1-PROV", chunk_size=2))
        assert result == [("G2-PROV", []), ("G3-PROV", [("G3-PROV", 4)])]
        assert cur.execute.call_args_list[2].args[1] == {"start_after": "G2-PROV"}


def test_call_tracked_only_while_in_flight(oracle):
    """What lets a hung call be logged while it hangs."""
    import app.db.call_tracker as tracker
    client, cur = oracle
    in_flight = []
    cur.execute.side_effect = lambda *a: in_flight.append(dict(tracker._in_flight))
    client.get_all_provider_ids()
    assert [list(calls.values())[0][0] for calls in in_flight] == ["MainThread"]
    assert tracker._in_flight == {}


# ---------------------------------------------------------------------------
# Small providers — shared SMALL_PROV_* tables
# ---------------------------------------------------------------------------

class TestSmallProviders:

    @pytest.fixture
    def small(self, oracle):
        client, cur = oracle
        del client.is_small_provider
        client._providers = {"SMALLP": True}
        return client, cur

    def test_provider_list_reads_providers_table(self, oracle):
        client, cur = oracle
        cur.fetchall.return_value = [("NORMAL", 0), ("SMALLP", 1)]
        assert client.get_all_provider_ids() == ["NORMAL", "SMALLP"]
        sql = cur.execute.call_args.args[0]
        assert "METADATA_DB.providers" in sql
        assert client._providers == {"NORMAL": False, "SMALLP": True}

    def test_unknown_provider_reloads_the_list(self, small):
        client, cur = small
        cur.fetchall.return_value = [("SMALLP", 1), ("NEWP", 1)]
        client.fetch_granule_id_range_chunk("NEWP", 0, 10)
        assert "SMALL_PROV_GRANULES" in cur.execute.call_args.args[0]

    def test_provider_wide_queries_filter_by_provider(self, small):
        client, cur = small
        client.find_next_granule_id_in_range("SMALLP", 0)
        client.fetch_granule_id_range_chunk("SMALLP", 0, 10)
        sqls = [c.args[0] for c in cur.execute.call_args_list]
        assert all("SMALL_PROV_GRANULES" in s for s in sqls)
        assert all("provider_id = 'SMALLP'" in s for s in sqls)

    def test_collection_and_concept_queries_use_shared_table_unfiltered(self, small):
        """Already scoped by collection or concept id."""
        client, cur = small
        cur.fetchall.side_effect = [[("G1-SMALLP",)], [("G1-SMALLP", 1)]]
        list(client.stream_granule_ids_paged("C1-SMALLP", chunk_size=10))
        client.get_concept_by_id("G1-SMALLP")
        sqls = [c.args[0] for c in cur.execute.call_args_list]
        assert all("SMALL_PROV_GRANULES" in s and "provider_id =" not in s for s in sqls)
