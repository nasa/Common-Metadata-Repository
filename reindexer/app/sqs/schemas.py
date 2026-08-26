"""Work item schemas for the cmr-reindexer-jobs intermediate queue.

Ticket 5 canonical schema:
  collection:   {"request_id": "...", "type": "collection",   "collection_id": "C1234-PROV", "after": null, "before": null}
  granule-page: {"request_id": "...", "type": "granule-page", "collection_id": "C1234-PROV", "offset": 0, "limit": 10000, "after": null, "before": null}
"""
import json
from dataclasses import dataclass
from typing import Optional, Union


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


@dataclass
class GranulePageWorkItem:
    request_id: str
    collection_id: str
    offset: int
    limit: int
    after: Optional[str] = None
    before: Optional[str] = None
    type: str = "granule-page"

    def to_json(self) -> str:
        return json.dumps({
            "request_id": self.request_id,
            "type": self.type,
            "collection_id": self.collection_id,
            "offset": self.offset,
            "limit": self.limit,
            "after": self.after,
            "before": self.before,
        })


WorkItem = Union[CollectionWorkItem, GranulePageWorkItem]


def parse_work_item(body: str) -> WorkItem:
    data = json.loads(body)
    t = data.get("type")
    if t == "collection":
        return CollectionWorkItem(
            request_id=data["request_id"],
            collection_id=data["collection_id"],
            after=data.get("after"),
            before=data.get("before"),
        )
    if t == "granule-page":
        return GranulePageWorkItem(
            request_id=data["request_id"],
            collection_id=data["collection_id"],
            offset=data["offset"],
            limit=data["limit"],
            after=data.get("after"),
            before=data.get("before"),
        )
    raise ValueError(f"Unknown work item type: {t!r}")
