from proxy.classifier import (
    EXPRESS,
    HEAVY,
    STANDARD,
    _compute_bbox_area,
    _count_polygon_vertices,
    classify_request,
)

# Non-spatial heavy signals


class TestHeavyParamSignals:
    def test_include_facets(self):
        assert classify_request({"include_facets": "v2"}) == HEAVY

    def test_online_only(self):
        assert classify_request({"online_only": "true"}) == HEAVY

    def test_cloud_cover(self):
        assert classify_request({"cloud_cover": "0,50"}) == HEAVY


class TestHeavyPatternSignals:
    def test_temporal_facet(self):
        assert classify_request({"temporal_facet[0][year]": "2024"}) == HEAVY

    def test_cycle(self):
        assert classify_request({"cycle[]": "1"}) == HEAVY

    def test_passes_pass(self):
        assert classify_request({"passes[0][pass]": "100"}) == HEAVY

    def test_options_readable_granule_name_pattern(self):
        assert (
            classify_request({"options[readable_granule_name][pattern]": "true"})
            == HEAVY
        )


# Shapefile detection


class TestShapefileDetection:
    def test_shapefile_with_multipart_content_type(self):
        assert (
            classify_request(
                {"shapefile": "data"},
                content_type="multipart/form-data; boundary=----",
            )
            == HEAVY
        )

    def test_file_param_with_multipart_content_type(self):
        assert (
            classify_request(
                {"file": "data"},
                content_type="multipart/form-data; boundary=----",
            )
            == HEAVY
        )

    def test_shapefile_without_multipart_is_not_heavy(self):
        # Without multipart content-type, shapefile param alone doesn't trigger heavy
        assert classify_request({"shapefile": "data"}) == EXPRESS

    def test_multipart_without_shapefile_param_is_not_heavy(self):
        assert (
            classify_request(
                {"concept_id": "C123"},
                content_type="multipart/form-data; boundary=----",
            )
            == EXPRESS
        )


# Non-spatial standard signals


class TestStandardParamSignals:
    def test_temporal(self):
        assert classify_request({"temporal": "2020-01-01T00:00:00Z,"}) == STANDARD

    def test_temporal_array(self):
        assert classify_request({"temporal[]": "2020-01-01T00:00:00Z,"}) == STANDARD

    def test_updated_since(self):
        assert classify_request({"updated_since": "2024-01-01"}) == STANDARD

    def test_revision_date(self):
        assert classify_request({"revision_date": "2024-01-01,"}) == STANDARD

    def test_orbit_number(self):
        assert classify_request({"orbit_number": "1000"}) == STANDARD


# Express default


class TestExpressDefault:
    def test_no_signals_returns_express(self):
        assert classify_request({"concept_id": "C1234"}) == EXPRESS

    def test_empty_params_returns_express(self):
        assert classify_request({}) == EXPRESS

    def test_simple_filter_params(self):
        assert (
            classify_request(
                {
                    "provider": "POCLOUD",
                    "tag_key": "gov.nasa.eosdis",
                    "entry_id": "X",
                }
            )
            == EXPRESS
        )


# Spatial: polygon


class TestSpatialPolygon:
    def test_polygon_array_always_heavy(self):
        assert classify_request({"polygon[]": "1,2,3,4,5,6,1,2"}) == HEAVY

    def test_polygon_20_vertices_is_standard(self):
        # 20 vertices = 40 coordinate values
        coords = ",".join(str(i) for i in range(40))
        assert classify_request({"polygon": coords}) == STANDARD

    def test_polygon_21_vertices_is_heavy(self):
        # 21 vertices = 42 coordinate values
        coords = ",".join(str(i) for i in range(42))
        assert classify_request({"polygon": coords}) == HEAVY

    def test_polygon_small_is_standard(self):
        # Simple triangle (3 vertices = 6 coords)
        assert classify_request({"polygon": "0,0,1,0,0,1"}) == STANDARD


# Spatial: bounding box


class TestSpatialBoundingBox:
    def test_bbox_area_under_threshold_is_standard(self):
        # 99 * 50 = 4950 sq deg (just under 5000)
        assert classify_request({"bounding_box": "-50,-25,49,25"}) == STANDARD

    def test_bbox_area_over_threshold_is_heavy(self):
        # 101 * 50 = 5050 sq deg (just over 5000)
        assert classify_request({"bounding_box": "-50,-25,51,25"}) == HEAVY

    def test_bbox_array_2_values_is_standard(self):
        # 2 small bboxes -> standard (not exceeding count threshold)
        assert (
            classify_request(
                {
                    "bounding_box[]": ["0,0,1,1", "2,2,3,3"],
                }
            )
            == STANDARD
        )

    def test_bbox_array_3_values_is_heavy(self):
        # 3 bboxes -> heavy (exceeds count threshold of 2)
        assert (
            classify_request(
                {
                    "bounding_box[]": ["0,0,1,1", "2,2,3,3", "4,4,5,5"],
                }
            )
            == HEAVY
        )

    def test_bbox_antimeridian_crossing_is_conservatively_heavy(self):
        # Naive math: abs(-170 - 170) * abs(10 - -10) = 340 * 20 = 6800 sq deg.
        # Real area is small, but overestimating toward HEAVY is the safe direction.
        assert classify_request({"bounding_box": "170,-10,-170,10"}) == HEAVY


# Spatial: circle


class TestSpatialCircle:
    def test_circle_array_is_express(self):
        assert classify_request({"circle[]": "40,-90,100"}) == EXPRESS

    def test_single_circle_is_standard(self):
        assert classify_request({"circle": "40,-90,100"}) == STANDARD


# Spatial: point


class TestSpatialPoint:
    def test_point_is_standard(self):
        assert classify_request({"point": "40,-90"}) == STANDARD

    def test_point_array_is_standard(self):
        assert classify_request({"point[]": "40,-90"}) == STANDARD


# Combined signals


class TestCombinedSignals:
    def test_heavy_nonspatial_overrides_spatial_standard(self):
        # include_facets is a heavy signal; polygon with few vertices would be standard
        assert (
            classify_request(
                {
                    "polygon": "0,0,1,0,0,1",
                    "include_facets": "v2",
                }
            )
            == HEAVY
        )

    def test_no_spatial_no_signals_is_express(self):
        assert classify_request({"provider": "POCLOUD"}) == EXPRESS

    def test_standard_temporal_with_simple_spatial(self):
        # point (standard spatial) + temporal (standard non-spatial) -> standard
        assert (
            classify_request(
                {
                    "point": "40,-90",
                    "temporal": "2020-01-01,",
                }
            )
            == STANDARD
        )


# Helper unit tests


class TestCountPolygonVertices:
    def test_triangle(self):
        assert _count_polygon_vertices("0,0,1,0,0,1") == 3

    def test_with_whitespace(self):
        assert _count_polygon_vertices(" 0,0,1,0,0,1 ") == 3

    def test_20_vertices(self):
        coords = ",".join(str(i) for i in range(40))
        assert _count_polygon_vertices(coords) == 20

    def test_21_vertices(self):
        coords = ",".join(str(i) for i in range(42))
        assert _count_polygon_vertices(coords) == 21


class TestComputeBboxArea:
    def test_simple_box(self):
        # 10 wide, 10 tall = 100
        assert _compute_bbox_area("0,0,10,10") == 100.0

    def test_antimeridian_crossing_overestimates(self):
        # Naive: abs(-170 - 170) * abs(10 - -10) = 340 * 20 = 6800
        # Overestimates, which is the conservative direction for classification.
        assert _compute_bbox_area("170,-10,-170,10") == 6800.0

    def test_invalid_value_returns_above_threshold(self):
        # Malformed bbox should be treated conservatively (pushed toward heavy)
        assert _compute_bbox_area("invalid") > 5000

    def test_empty_string_returns_above_threshold(self):
        assert _compute_bbox_area("") > 5000

    def test_malformed_bbox_classifies_as_heavy(self):
        assert classify_request({"bounding_box": "garbage"}) == HEAVY
