import hashlib

from .models import Collection, Variable


def passage_id(collection_id: str, variable_id: str | None = None) -> str:
    value = f"{collection_id}|{variable_id or 'collection'}"
    return hashlib.sha256(value.encode()).hexdigest()


def _join(values: list[str]) -> str:
    return "; ".join(values)


def _base(collection: Collection) -> dict:
    result = {
        "collection_id": collection.concept_id,
        "short_name": collection.short_name,
        "title": collection.title,
        "summary": collection.summary,
        "science_keywords": collection.science_keywords,
        "platforms": collection.platforms,
        "instruments": collection.instruments,
    }
    if collection.temporal:
        result["temporal"] = {
            "gte": collection.temporal.start.isoformat(),
            "lte": collection.temporal.end.isoformat(),
        }
    if collection.spatial:
        result["spatial"] = collection.spatial.model_dump()
    return result


def build_passages(collection: Collection) -> list[dict]:
    base = _base(collection)
    collection_text = " | ".join(filter(None, [
        collection.short_name, collection.title, collection.summary,
        _join(collection.science_keywords), _join(collection.platforms),
        _join(collection.instruments),
    ]))
    passages = [{**base, "passage_id": passage_id(collection.concept_id),
                 "passage_type": "collection", "passage_text": collection_text}]
    for variable in collection.variables:
        passages.append(_variable_passage(base, collection, variable))
    return passages


def _variable_passage(base: dict, collection: Collection, variable: Variable) -> dict:
    text = " | ".join(filter(None, [
        collection.short_name, collection.title, "Variable", variable.name,
        variable.long_name, variable.definition, _join(variable.measurements), _join(variable.units),
    ]))
    return {**base, "passage_id": passage_id(collection.concept_id, variable.concept_id),
            "passage_type": "variable", "variable_id": variable.concept_id,
            "variable_name": variable.name, "variable_long_name": variable.long_name,
            "variable_definition": variable.definition, "measurements": variable.measurements,
            "units": variable.units, "passage_text": text}

