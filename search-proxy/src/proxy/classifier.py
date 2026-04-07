from typing import Any, Dict, List, Optional

EXPRESS = "express"
STANDARD = "standard"
HEAVY = "heavy"


# Non-spatial heavy signals

HEAVY_PARAMS = {
    "include_facets",
    "online_only",
    "cloud_cover",
}

HEAVY_PREFIXES = (
    "temporal_facet[",
    "cycle[",
    "passes[",
    "options[readable_granule_name][pattern]",
)

# Non-spatial standard signals
STANDARD_PARAMS = {
    "temporal",
    "temporal[]",
    "updated_since",
    "revision_date",
    "orbit_number",
}

# Spatial geometry thresholds

HEAVY_POLYGON_VERTEX_THRESHOLD = 20
HEAVY_BBOX_AREA_THRESHOLD = 5000  # square degrees
HEAVY_MULTI_BBOX_COUNT_THRESHOLD = 2


def _count_polygon_vertices(polygon_value: str) -> int:
    """Count vertices in a polygon coordinate string.
    Format: 'lon1,lat1,lon2,lat2,...,lonN,latN' (closing vertex may repeat first)."""
    ordinates = polygon_value.strip().strip('"').split(",")
    coords_per_vertex = 2  # lon, lat
    return len(ordinates) // coords_per_vertex


def _compute_bbox_area(bbox_value: str) -> float:
    """Approximate area in square degrees for a bounding box.
    Format: 'west,south,east,north'.

    This is deliberately naive — no antimeridian handling, no spherical correction.
    Edge cases (antimeridian crossing, duplicate closing points, backwards winding)
    will overestimate area, pushing toward HEAVY. That's the conservative direction;
    the semaphore timeout is the real safety net for misclassification.

    On parse failure, returns a value above the heavy threshold so malformed spatial
    params are treated conservatively rather than getting the express lane."""
    try:
        parts = bbox_value.strip().strip('"').split(",")
        west, south, east, north = (float(p) for p in parts[:4])
        return abs(east - west) * abs(north - south)
    except (ValueError, IndexError):
        return HEAVY_BBOX_AREA_THRESHOLD + 1


def _parse_multi_value(value: Any) -> List[str]:
    """Parse a param value that may be a list or a single string."""
    if isinstance(value, list):
        return value
    return [str(value)]


def _is_shapefile_request(params: Dict[str, Any], content_type: str = "") -> bool:
    """Detect shapefile uploads (multipart form with shapefile)."""
    return "multipart/form-data" in content_type and any(
        k in params for k in ("shapefile", "file")
    )


def _classify_spatial(params: Dict[str, Any]) -> Optional[str]:
    """Classify spatial queries using computed geometric properties.
    Returns None if no spatial params are present."""
    param_keys = set(params.keys())

    has_spatial = False

    # polygon[] (multi-polygon): always heavy
    if "polygon[]" in param_keys:
        return HEAVY

    # Single polygon: check vertex count
    if "polygon" in param_keys:
        has_spatial = True
        for poly_val in _parse_multi_value(params["polygon"]):
            vertices = _count_polygon_vertices(poly_val)
            if vertices > HEAVY_POLYGON_VERTEX_THRESHOLD:
                return HEAVY

    # bounding_box[] or bounding_box: check area and count
    for bbox_key in ("bounding_box[]", "bounding_box"):
        if bbox_key in param_keys:
            has_spatial = True
            bbox_values = _parse_multi_value(params[bbox_key])
            if (
                bbox_key == "bounding_box[]"
                and len(bbox_values) > HEAVY_MULTI_BBOX_COUNT_THRESHOLD
            ):
                return HEAVY
            for bbox_val in bbox_values:
                if _compute_bbox_area(bbox_val) > HEAVY_BBOX_AREA_THRESHOLD:
                    return HEAVY

    # circle[]: consistently cheap (avg 157ms, indexed filter, no script)
    if "circle[]" in param_keys:
        return EXPRESS

    # Other spatial params (point, point[], circle): standard
    if any(k in param_keys for k in ("point", "point[]", "circle")):
        has_spatial = True

    if has_spatial:
        return STANDARD

    # No spatial params present
    return None


def classify_request(
    params: Dict[str, Any],
    content_type: str = "",
) -> str:
    """Classify a search request into a lane name.

    Uses three layers:
    1. Non-spatial heavy signals (deterministic from production data)
    2. Spatial geometry analysis (vertex count, area, multi-spatial)
    3. Non-spatial standard signals (temporal, date ranges)

    Anything not matching heavy or standard rules -> express.
    """
    param_keys = set(params.keys())

    # Layer 1: Non-spatial heavy signals (always win)
    if param_keys & HEAVY_PARAMS:
        return HEAVY

    if any(k.startswith(HEAVY_PREFIXES) for k in param_keys):
        return HEAVY

    # Shapefile: always heavy
    if _is_shapefile_request(params, content_type):
        return HEAVY

    # Layer 2: Spatial classification (geometry-aware)
    spatial_lane = _classify_spatial(params)
    if spatial_lane is not None:
        return spatial_lane

    # Layer 3: Non-spatial standard signals
    if param_keys & STANDARD_PARAMS:
        return STANDARD

    return EXPRESS
