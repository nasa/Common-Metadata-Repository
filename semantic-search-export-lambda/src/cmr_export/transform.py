from datetime import datetime, timezone
from math import isfinite


class RecordValidationError(ValueError):
    pass


def _text(value):
    return value.strip() if isinstance(value, str) and value.strip() else None


def _strings(value):
    values = value if isinstance(value, list) else ([value] if value is not None else [])
    return sorted(set(filter(None, (_text(item) for item in values))))


def flatten_science_keywords(source: dict) -> list[str]:
    nested = source.get("science-keywords")
    if isinstance(nested, list) and nested and isinstance(nested[0], dict):
        fields = (
            "category",
            "topic",
            "term",
            "variable-level-1",
            "variable-level-2",
            "variable-level-3",
            "detailed-variable",
        )
        paths = [
            " > ".join(filter(None, (_text(item.get(field)) for field in fields)))
            for item in nested
        ]
        return sorted(set(filter(None, paths)))
    return _strings(source.get("science-keywords-flat"))


def _date(value):
    if not isinstance(value, str) or not value.strip():
        return None
    raw = value.strip()
    try:
        parsed = datetime.fromisoformat(raw[:-1] + "+00:00" if raw.endswith("Z") else raw)
    except ValueError:
        return None
    if parsed.tzinfo is None:
        return None
    normalized = parsed.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
    return normalized


def temporal(source: dict):
    ranges = source.get("temporals") or []
    valid = [
        (_date(item.get("start-date")), _date(item.get("end-date")))
        for item in ranges
        if isinstance(item, dict)
    ]
    valid = [(start, end) for start, end in valid if start and end and start <= end]
    if not valid:
        return None
    return {"start": min(start for start, _ in valid), "end": max(end for _, end in valid)}


def spatial(source: dict):
    if source.get("mbr-crosses-antimeridian") is True:
        return None, "antimeridian"
    try:
        west, south, east, north = (
            float(source[name]) for name in ("mbr-west", "mbr-south", "mbr-east", "mbr-north")
        )
    except (KeyError, TypeError, ValueError):
        return None, "missing_or_invalid_mbr"
    if not all(map(isfinite, (west, south, east, north))) or not (
        -180 <= west < east <= 180 and -90 <= south < north <= 90
    ):
        return None, "missing_or_invalid_mbr"
    ring = [[west, south], [east, south], [east, north], [west, north], [west, south]]
    return {"type": "Polygon", "coordinates": [ring]}, None


def variable(source: dict) -> dict:
    result = {
        "concept_id": _text(source.get("concept-id")),
        "name": _text(source.get("variable-name")),
    }
    if not result["concept_id"] or not result["name"]:
        raise RecordValidationError("variable requires nonblank concept-id and variable-name")
    definition = _text(source.get("definition"))
    if definition:
        result["definition"] = definition
    measurements = _strings(source.get("measurement"))
    if measurements:
        result["measurements"] = measurements
    return result


def collection(source: dict, variables: list[dict]) -> tuple[dict, str | None]:
    result = {
        "schema_version": 1,
        "concept_id": _text(source.get("concept-id")),
        "short_name": _text(source.get("short-name")),
        "title": _text(source.get("entry-title")),
    }
    if not all(result.values()):
        raise RecordValidationError(
            "collection requires nonblank concept-id, short-name, and entry-title"
        )
    optional = {
        "summary": _text(source.get("summary")),
        "science_keywords": flatten_science_keywords(source),
        "platforms": _strings(source.get("platform-sn")),
        "instruments": _strings(source.get("instrument-sn")),
        "temporal": temporal(source),
        "variables": sorted(variables, key=lambda x: x["concept_id"]),
    }
    result.update({key: value for key, value in optional.items() if value not in (None, [], "")})
    shape, warning = spatial(source)
    if shape:
        result["spatial"] = shape
    return result, warning
