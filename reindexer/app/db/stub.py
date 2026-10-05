from typing import Iterator, Optional


class StubOracleClient:
    """Hardcoded fake data so the rest of the service can run without a real Oracle connection."""

    _providers = ["PROV_A", "PROV_B", "PROV_C"]

    _collections: dict[str, list[str]] = {
        "PROV_A": ["C1000000001-PROV_A", "C1000000002-PROV_A"],
        "PROV_B": ["C1000000003-PROV_B"],
        "PROV_C": ["C1000000004-PROV_C", "C1000000005-PROV_C", "C1000000006-PROV_C"],
    }

    _granule_counts: dict[str, int] = {
        "C1000000001-PROV_A": 25000,
        "C1000000002-PROV_A": 5000,
        "C1000000003-PROV_B": 42000,
        "C1000000004-PROV_C": 1000,
        "C1000000005-PROV_C": 1000,
        "C1000000006-PROV_C": 15000,
    }

    _concept_type_ids: dict[str, list[tuple[str, int]]] = {
        "collection":           [("C1000000001-PROV_A", 1), ("C1000000002-PROV_A", 2), ("C1000000003-PROV_B", 1)],
        "variable":             [("V1000000001-PROV_A", 1), ("V1000000002-PROV_B", 3)],
        "service":              [("S1000000001-PROV_A", 1)],
        "tool":                 [("TL1000000001-PROV_A", 2)],
        "subscription":         [("SUB1000000001-PROV_A", 1)],
        "generic":              [("DQS1000000001-PROV_A", 1), ("OO1000000001-PROV_A", 1)],
        "data-quality-summary": [("DQS1000000001-PROV_A", 1)],
        "order-option":         [("OO1000000001-PROV_A", 1)],
        "grid":                 [("GRD1000000001-PROV_A", 1)],
        "citation":             [("CIT1000000001-PROV_A", 1)],
        "visualization":        [("VIS1000000001-PROV_A", 1)],
    }

    def get_all_provider_ids(self) -> list[str]:
        return list(self._providers)

    def get_collection_ids_for_provider(self, provider_id: str) -> list[str]:
        return list(self._collections.get(provider_id, []))

    def find_next_granule_id_in_range(
        self,
        provider_id: str,
        min_id: int,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> Optional[int]:
        """Fake provider-wide id space: a dense range [0, total) with no gaps —
        good enough to exercise the id-range dispatch strategy locally."""
        total = sum(self._granule_counts.get(cid, 0) for cid in self._collections.get(provider_id, []))
        return min_id if min_id < total else None

    def fetch_granule_id_range_chunk(
        self,
        provider_id: str,
        start_id: int,
        end_id: int,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> list[tuple[str, int]]:
        total = sum(self._granule_counts.get(cid, 0) for cid in self._collections.get(provider_id, []))
        return [
            (f"G{1000000000 + i}-{provider_id}", 1)
            for i in range(max(0, start_id), min(end_id, total))
        ]

    def stream_granule_ids_paged(
        self,
        collection_id: str,
        chunk_size: int,
        after: Optional[str] = None,
        before: Optional[str] = None,
        start_after_concept_id: Optional[str] = None,
    ) -> Iterator[tuple[str, list[tuple[str, int]]]]:
        """Yield (page_end, chunk) pages of fake granule IDs, respecting keyset resume
        and chunk_size. Ignores after/before, so chunks are never empty here.

        start_after_concept_id mirrors the Oracle keyset cursor: only IDs that sort
        after that value are returned.  The stub uses a sequential integer suffix so
        lexicographic order matches the generation order.
        """
        count = self._granule_counts.get(collection_id, 0)
        provider = collection_id.split("-", 1)[1] if "-" in collection_id else "UNKNOWN"

        all_ids = [(f"G{1000000000 + i}-{provider}", 1) for i in range(count)]
        if start_after_concept_id:
            all_ids = [(cid, rev) for cid, rev in all_ids if cid > start_after_concept_id]

        for i in range(0, len(all_ids), chunk_size):
            chunk = all_ids[i:i + chunk_size]
            yield chunk[-1][0], chunk

    def get_concept_ids_by_type(
        self,
        concept_type: str,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> list[tuple[str, int]]:
        return list(self._concept_type_ids.get(concept_type, []))

    def get_concept_by_id(self, concept_id: str) -> Optional[dict]:
        return {"concept-id": concept_id, "revision-id": 1}


db_client = StubOracleClient()
