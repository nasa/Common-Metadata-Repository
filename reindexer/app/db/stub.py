from typing import Optional


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

    def get_granule_count(
        self,
        collection_id: str,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> int:
        return self._granule_counts.get(collection_id, 0)

    def get_granule_ids(
        self,
        collection_id: str,
        offset: int,
        limit: int,
        after: Optional[str] = None,
        before: Optional[str] = None,
    ) -> list[tuple[str, int]]:
        count = self._granule_counts.get(collection_id, 0)
        provider = collection_id.split("-", 1)[1] if "-" in collection_id else "UNKNOWN"
        end = min(offset + limit, count)
        return [(f"G{1000000000 + i}-{provider}", 1) for i in range(offset, end)]

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
