"""Unit tests for StubOracleClient — locks in documented, intentional behavior so a
future change to the stub can't silently drift without a test noticing.
"""
from app.db.stub import StubOracleClient


class TestGetCollectionIdsForProvider:

    def test_after_before_are_ignored(self):
        """The stub has no notion of revision dates in its fake data (see class
        docstring) — after/before are accepted for interface parity with OracleClient
        but never filter anything. If this ever changes, tests elsewhere that rely on
        the stub returning everything regardless of date args would need updating too."""
        client = StubOracleClient()
        unfiltered = client.get_collection_ids_for_provider("PROV_A")
        filtered = client.get_collection_ids_for_provider(
            "PROV_A", after="2099-01-01T00:00:00Z", before="2099-01-02T00:00:00Z"
        )
        assert filtered == unfiltered
        assert filtered == ["C1000000001-PROV_A", "C1000000002-PROV_A"]

    def test_unknown_provider_returns_empty_regardless_of_dates(self):
        client = StubOracleClient()
        assert client.get_collection_ids_for_provider("NOPE", after="2024-01-01T00:00:00Z") == []
