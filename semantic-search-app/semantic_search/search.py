import time
from datetime import datetime


LEXICAL_FIELDS = ["short_name^5", "title^4", "variable_name^4", "science_keywords^3",
                  "instruments^2", "summary", "passage_text"]


def parse_temporal(value: str | None):
    if not value:
        return None
    parts = value.split(",")
    if len(parts) != 2 or not all(parts):
        raise ValueError("temporal must contain start,end")
    try:
        start, end = (datetime.fromisoformat(part.replace("Z", "+00:00")) for part in parts)
    except ValueError as error:
        raise ValueError("temporal bounds must be ISO-8601 dates") from error
    if start.tzinfo is None or end.tzinfo is None or start > end:
        raise ValueError("temporal must be an ordered UTC interval")
    return {"range": {"temporal": {"gte": parts[0], "lte": parts[1], "relation": "intersects"}}}


def parse_bounding_box(value: str | None):
    if not value:
        return None
    try:
        west, south, east, north = (float(v) for v in value.split(","))
    except ValueError as error:
        raise ValueError("bounding_box must contain west,south,east,north") from error
    if not (-180 <= west <= 180 and -180 <= east <= 180 and -90 <= south < north <= 90 and west < east):
        raise ValueError("bounding_box is invalid or crosses the antimeridian")
    shape = {"type": "envelope", "coordinates": [[west, north], [east, south]]}
    return {"geo_shape": {"spatial": {"shape": shape, "relation": "intersects"}}}


def deduplicate(hits):
    result, seen = [], set()
    for hit in hits:
        collection_id = hit["_source"]["collection_id"]
        if collection_id not in seen:
            seen.add(collection_id)
            result.append(hit)
    return result


def rrf(lexical, semantic, constant=60):
    scores, evidence = {}, {}
    for source, hits in (("lexical", lexical), ("semantic", semantic)):
        for rank, hit in enumerate(deduplicate(hits), 1):
            cid = hit["_source"]["collection_id"]
            scores[cid] = scores.get(cid, 0) + 1 / (constant + rank)
            evidence.setdefault(cid, {})[source] = hit
    return sorted(scores, key=scores.get, reverse=True), scores, evidence


def _evidence(source, hit):
    doc = hit["_source"]
    highlights = hit.get("highlight", {}).get("passage_text")
    return {"source": source, "type": doc["passage_type"],
            "variable_concept_id": doc.get("variable_id"), "variable_name": doc.get("variable_name"),
            "snippet": (highlights or [doc["passage_text"]])[0]}


async def search(es, embedder, alias, query, mode, page_size, temporal=None, bounding_box=None):
    started = time.perf_counter()
    filters = [item for item in (parse_temporal(temporal), parse_bounding_box(bounding_box)) if item]
    lexical_body = {"size": 200, "query": {"bool": {"must": [{"multi_match": {"query": query, "fields": LEXICAL_FIELDS}}], "filter": filters}},
                    "highlight": {"fields": {"passage_text": {"fragment_size": 240}}}}
    semantic_body = None
    if mode != "lexical":
        vector, _ = await embedder.embed(query)
        semantic_body = {"size": 200, "knn": {"field": "embedding", "query_vector": vector,
                         "k": 50, "num_candidates": 200, "filter": filters}}
    if mode == "hybrid":
        response = await es.msearch(index=alias, searches=[{}, lexical_body, {}, semantic_body])
        lexical_hits, semantic_hits = (r["hits"]["hits"] for r in response["responses"])
    elif mode == "lexical":
        lexical_hits = (await es.search(index=alias, **lexical_body))["hits"]["hits"]
        semantic_hits = []
    else:
        semantic_hits = (await es.search(index=alias, **semantic_body))["hits"]["hits"]
        lexical_hits = []
    ordered, scores, found = rrf(lexical_hits, semantic_hits)
    entries = []
    for rank, cid in enumerate(ordered[:page_size], 1):
        hits = found[cid]
        strongest = next(iter(hits.values()))["_source"]
        entries.append({"rank": rank, "score": scores[cid], "concept_id": cid,
                        "short_name": strongest["short_name"], "title": strongest["title"],
                        "evidence": [_evidence(source, hit) for source, hit in hits.items()]})
    return {"query": query, "mode": mode, "returned": len(entries),
            "candidate_collections": len(ordered), "took_ms": round((time.perf_counter()-started)*1000),
            "entries": entries}
