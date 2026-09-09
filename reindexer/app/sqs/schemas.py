"""Work item schemas for the cmr-reindexer collection queue.

Canonical schema:
  collection: {"request_id": "...", "type": "collection", "collection_id": "C1234-PROV", "after": null, "before": null}
"""
import json
from dataclasses import dataclass
from typing import Optional


@dataclass
class CollectionWorkItem:
    request_id: str
    collection_id: str
    after: Optional[str] = None
    before: Optional[str] = None
    type: str = "collection"

    def to_json(self) -> str:
        return json.dumps({
            "request_id": self.request_id,
            "type": self.type,
            "collection_id": self.collection_id,
            "after": self.after,
            "before": self.before,
        })


def parse_work_item(body: str) -> CollectionWorkItem:
    data = json.loads(body)
    t = data.get("type")
    if t == "collection":
        return CollectionWorkItem(
            request_id=data["request_id"],
            collection_id=data["collection_id"],
            after=data.get("after"),
            before=data.get("before"),
        )
    raise ValueError(f"Unknown work item type: {t!r}")
