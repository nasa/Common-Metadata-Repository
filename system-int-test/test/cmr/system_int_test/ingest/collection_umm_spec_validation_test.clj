(ns cmr.system-int-test.ingest.collection-umm-spec-validation-test
  "CMR Ingest umm-spec validation integration tests"
  (:require
    [clojure.test :refer :all]
    [cmr.common-app.test.side-api :as side]
    [cmr.common.util :as u :refer     [are3]]
    [cmr.ingest.config :as icfg]
    [cmr.spatial.line-string :as l]
    [cmr.spatial.mbr :as m]
    [cmr.spatial.polygon :as poly]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.umm-spec.models.umm-collection-models :as umm-c]
    [cmr.umm-spec.models.umm-common-models :as umm-cmn]
    [cmr.umm.umm-spatial :as umm-s]))

(defn polygon
  "Creates a single ring polygon with the given ordinates. Points must be in counter clockwise order."
  [& ords]
  (poly/polygon [(apply umm-s/ords->ring ords)]))

(defn assert-invalid
  ([coll-attributes field-path errors]
   (assert-invalid coll-attributes field-path errors nil))
  ([coll-attributes field-path errors options]
   (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection-missing-properties coll-attributes)
                            (merge {:allow-failure? true} options))]
     (is (= {:status 422
             :errors [{:path field-path
                       :errors errors}]}
            (select-keys response [:status :errors]))))))

(defn assert-valid
  ([coll-attributes]
   (assert-valid coll-attributes nil))
  ([coll-attributes options]
   (let [collection (assoc (data-umm-c/collection-missing-properties coll-attributes) :native-id (:native-id coll-attributes))
         provider-id (get coll-attributes :provider-id "PROV1")
         response (d/ingest-umm-spec-collection provider-id collection options)]
     (is (#{{:status 200} {:status 201}} (select-keys response [:status :errors]))))))

(defn assert-invalid-spatial
  ([coord-sys hsd field-path errors]
   (assert-invalid-spatial coord-sys hsd field-path errors nil))
  ([coord-sys  hsd field-path errors options]
   (assert-invalid {:SpatialExtent (data-umm-c/spatial {:gsr coord-sys :hsd hsd})}
                   field-path
                   errors
                   options)))

(defn assert-valid-spatial
  [coord-sys hsd]
  (assert-valid {:SpatialExtent (data-umm-c/spatial {:gsr coord-sys :hsd hsd})}))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest collection-umm-spec-validation-test
  (testing "UMM-C JSON-Schema validation through config settings"
    (testing "schema validation errors returned"
      (side/eval-form `(icfg/set-return-umm-json-validation-errors! true))
      (let [response (d/ingest-umm-spec-collection "PROV1"
                               (data-umm-c/collection-missing-properties {:AdditionalAttributes
                                               [(data-umm-c/additional-attribute {:Name "bool1" :DataType "BOOLEAN" :Value true})
                                                (data-umm-c/additional-attribute {:Name "bool2" :DataType "BOOLEAN" :Value true})]})
                               {:allow-failure? true})]
        (is (= {:status 422
                :errors ["object has missing required properties ([\"CollectionProgress\",\"DataCenters\",\"Platforms\",\"ProcessingLevel\",\"ScienceKeywords\",\"TemporalExtents\"])"]}
               (select-keys response [:status :errors])))))

    (testing "schema validation errors not returned"
      (side/eval-form `(icfg/set-return-umm-json-validation-errors! false))
      (assert-valid {:AdditionalAttributes [(data-umm-c/additional-attribute {:Name "bool1" :DataType "BOOLEAN" :Value true})
                                                   (data-umm-c/additional-attribute {:Name "bool2" :DataType "BOOLEAN" :Value true})]})))

  (testing "UMM-C JSON-Schema validation through Cmr-Validate-Umm-C header"
    (testing "schema validation errors returned when Cmr-Validate-Umm-C header is true"
      (let [response (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection-missing-properties {:AdditionalAttributes
                                                       [(data-umm-c/additional-attribute {:Name "bool1" :DataType "BOOLEAN" :Value true})
                                                        (data-umm-c/additional-attribute {:Name "bool2" :DataType "BOOLEAN" :Value true})]})
                               {:allow-failure? true :validate-umm-c true})]
        (is (= {:status 422
                :errors ["object has missing required properties ([\"CollectionProgress\",\"DataCenters\",\"Platforms\",\"ProcessingLevel\",\"ScienceKeywords\",\"TemporalExtents\"])"]}
               (select-keys response [:status :errors])))))

    (testing "schema validation error returns is controlled by config setting when Cmr-Validate-Umm-C header is NOT true"
      (let [coll-attr {:AdditionalAttributes
                       [(data-umm-c/additional-attribute {:Name "bool1" :DataType "BOOLEAN" :Value true})
                        (data-umm-c/additional-attribute {:Name "bool2" :DataType "BOOLEAN" :Value true})]}]
        (side/eval-form `(icfg/set-return-umm-json-validation-errors! false))
        (are3 [coll-attributes options]
              (assert-valid coll-attributes options)

              "Set Cmr-Validate-Umm-C header to false - schema validation error is not returned"
              coll-attr
              {:allow-failure? true :validate-umm-c false}

              "Do not set Cmr-Validate-Umm-C header - schema validation error is not returned"
              coll-attr
              {:allow-failure? true}))))

  (testing "UMM-SPEC validation through Cmr-Validation-Umm-C header"
    (let [coll-attr {:ProcessingLevel (umm-c/map->ProcessingLevelType {:Id "1"})
                     :ScienceKeywords [(data-umm-c/science-keyword {:Category "upcase"
                                                             :Topic "Cool"
                                                             :Term "Mild"})]
                     :SpatialExtent (data-umm-c/spatial {:gsr "CARTESIAN"})
                     :RelatedUrls [(data-umm-c/related-url {:Type "type" :URL "http://www.foo.com"})]
                     :DataCenters [(data-umm-c/data-center ["ARCHIVER"] "Larc")]
                     :Platforms [{:ShortName "plat"
                                  :LongName "plat"
                                  :Type "Aircraft"
                                  :Instruments [{:ShortName "inst"}]}]
                     :TemporalExtents [(data-umm-c/temporal-extent
                                         {:beginning-date-time "1965-12-12T07:00:00.000-05:00"
                                          :ending-date-time "1967-12-12T07:00:00.000-05:00"})]
                     :CollectionProgress :complete
                     :AdditionalAttributes
                     [(data-umm-c/additional-attribute {:Name "bool" :DataType "BOOLEAN" :Value true})
                      (data-umm-c/additional-attribute {:Name "bool" :DataType "BOOLEAN" :Value true})]}]
      (side/eval-form `(icfg/set-return-umm-json-validation-errors! false))
      (side/eval-form `(icfg/set-return-umm-spec-validation-errors! false))
      (are3 [coll-attributes field-path error options]
            (assert-invalid coll-attributes field-path error options)

            "Set Cmr-Validate-Umm-C header to true - schema validation passed, umm-spec validation error is returned"
            coll-attr
            ["AdditionalAttributes"]
            ["Additional Attributes must be unique. This contains duplicates named [bool]."]
            {:validate-umm-c true})))

  (side/eval-form `(icfg/set-return-umm-spec-validation-errors! true))

  (testing "Additional Attribute validation"
    (assert-invalid
      {:AdditionalAttributes
       [(data-umm-c/additional-attribute {:Name "bool" :DataType "BOOLEAN" :Value true})
        (data-umm-c/additional-attribute {:Name "bool" :DataType "BOOLEAN" :Value true})]}
      ["AdditionalAttributes"]
      ["Additional Attributes must be unique. This contains duplicates named [bool]."]))

  (testing "Nested Path Validation"
    (assert-invalid
      {:Platforms [(data-umm-c/platform {:Instruments [(data-umm-c/instrument {:ShortName "I1"})
                                               (data-umm-c/instrument {:ShortName "I1"})]})]}
      ["Platforms" 0 "Instruments"]
      ["Instruments must be unique. This contains duplicates named [I1]."]))

  (testing "Spatial validation"
    (testing "geodetic polygon"
      ;; Invalid points are caught in the schema validation
      (assert-invalid-spatial
        "GEODETIC"
        {:Geometry (umm-cmn/map->GeometryType
                     {:CoordinateSystem "GEODETIC"
                      :GPolygons [(umm-cmn/map->GPolygonType
                                    {:Boundary (umm-cmn/map->BoundaryType
                                                 {:Points [(umm-cmn/map->PointType {:Longitude 180 :Latitude 90})
                                                           (umm-cmn/map->PointType {:Longitude -180 :Latitude 90})
                                                           (umm-cmn/map->PointType {:Longitude -180 :Latitude -90})
                                                           (umm-cmn/map->PointType {:Longitude 180 :Latitude -90})
                                                           (umm-cmn/map->PointType {:Longitude 180 :Latitude 90})]})})]})}
        ["SpatialExtent" "HorizontalSpatialDomain" "Geometry" "GPolygons" 0]
        ["Spatial validation error: The shape contained duplicate points. Points 1 [lon=180 lat=90] and 2 [lon=-180 lat=90] were considered equivalent or very close."
         "Spatial validation error: The shape contained duplicate points. Points 3 [lon=-180 lat=-90] and 4 [lon=180 lat=-90] were considered equivalent or very close."
         "Spatial validation error: The shape contained consecutive antipodal points. Points 2 [lon=-180 lat=90] and 3 [lon=-180 lat=-90] are antipodal."
         "Spatial validation error: The shape contained consecutive antipodal points. Points 4 [lon=180 lat=-90] and 5 [lon=180 lat=90] are antipodal."]))

    (testing "geodetic polygon with holes"
      ;; Hole validation is not supported yet. See CMR-1173.
      ;; Holes are ignored during validation for now.
      (assert-valid-spatial
        "GEODETIC"
        {:Geometry (umm-cmn/map->GeometryType
                     {:CoordinateSystem "GEODETIC"
                      :GPolygons [(umm-cmn/map->GPolygonType
                                    {:Boundary (umm-cmn/map->BoundaryType
                                                 {:Points [(umm-cmn/map->PointType {:Longitude 1 :Latitude 1})
                                                           (umm-cmn/map->PointType {:Longitude -1 :Latitude 1})
                                                           (umm-cmn/map->PointType {:Longitude -1 :Latitude -1})
                                                           (umm-cmn/map->PointType {:Longitude 1 :Latitude -1})
                                                           (umm-cmn/map->PointType {:Longitude 1 :Latitude 1})]})
                                     :ExclusiveZone
                                       (umm-cmn/map->ExclusiveZoneType
                                         {:Boundaries [(umm-cmn/map->BoundaryType
                                                         {:Points  [(umm-cmn/map->PointType {:Longitude 0 :Latitude 0})
                                                                    (umm-cmn/map->PointType {:Longitude 0.004 :Latitude 0})
                                                                    (umm-cmn/map->PointType {:Longitude 0.006 :Latitude 0.005})
                                                                    (umm-cmn/map->PointType {:Longitude 0.002 :Latitude 0.005})
                                                                    (umm-cmn/map->PointType {:Longitude 0 :Latitude 0})]})]})})]})}))

    (testing "cartesian polygon"
      ;; The same shape from geodetic is valid as a cartesian.
      ;; Cartesian validation is not supported yet. See CMR-1172
      (assert-valid-spatial
        "CARTESIAN"
        {:Geometry (umm-cmn/map->GeometryType
                     {:CoordinateSystem "CARTESIAN"
                      :GPolygons [(umm-cmn/map->GPolygonType
                                    {:Boundary (umm-cmn/map->BoundaryType
                                                 {:Points [(umm-cmn/map->PointType {:Longitude 180 :Latitude 90})
                                                           (umm-cmn/map->PointType {:Longitude -180 :Latitude 90})
                                                           (umm-cmn/map->PointType {:Longitude -180 :Latitude -90})
                                                           (umm-cmn/map->PointType {:Longitude 180 :Latitude -90})
                                                           (umm-cmn/map->PointType {:Longitude 180 :Latitude 90})]})})]})}))

    (testing "geodetic line"
      (assert-invalid-spatial
        "GEODETIC"
        {:Geometry (umm-cmn/map->GeometryType
                     {:CoordinateSystem "GEODETIC"
                      :Lines [(umm-cmn/map->LineType
                                {:Points [(umm-cmn/map->PointType {:Longitude 0 :Latitude 0})
                                          (umm-cmn/map->PointType {:Longitude 1 :Latitude 1})
                                          (umm-cmn/map->PointType {:Longitude 2 :Latitude 2})
                                          (umm-cmn/map->PointType {:Longitude 1 :Latitude 1})]})]})}
        ["SpatialExtent" "HorizontalSpatialDomain" "Geometry" "Lines" 0]
        ["Spatial validation error: The shape contained duplicate points. Points 2 [lon=1 lat=1] and 4 [lon=1 lat=1] were considered equivalent or very close."]))

    (testing "cartesian line"
      ;; Cartesian line validation isn't supported yet. See CMR-1172
      (assert-valid-spatial
        "CARTESIAN"
        {:Geometry (umm-cmn/map->GeometryType
                     {:CoordinateSystem "CARTESIAN"
                      :Lines [(umm-cmn/map->LineType
                                {:Points [(umm-cmn/map->PointType {:Longitude 180 :Latitude 0})
                                          (umm-cmn/map->PointType {:Longitude -180 :Latitude 0})]})]})}))
    (testing "bounding box"
      (assert-invalid-spatial
        "GEODETIC"
        {:Geometry (umm-cmn/map->GeometryType
                     {:CoordinateSystem "GEODETIC"
                      :BoundingRectangles [(umm-cmn/map->BoundingRectangleType {:WestBoundingCoordinate -180
                                                                                :NorthBoundingCoordinate 45
                                                                                :EastBoundingCoordinate 180
                                                                                :SouthBoundingCoordinate 46})]})}
        ["SpatialExtent" "HorizontalSpatialDomain" "Geometry" "BoundingRectangles" 0]
        ["Spatial validation error: The bounding rectangle north value [45] was less than the south value [46]"]))))

(deftest umm-spec-validation-warnings
  ;; By default the config return-umm-spec-validation-errors is false, so warnings are returned with the collection.
  (testing "Ingest and Ingest Validation with warning messages for all formats"
    (are3 [format collection warning-message]
          (do
            (let [response (d/ingest-umm-spec-collection "PROV1" collection {:format format})]
              (is (#{200 201} (:status response)))
              (is (= warning-message (:warnings response))))
            (let [response (ingest/validate-concept (data-umm-c/collection-concept collection format))]
              (is (= 200 (:status response)))
              (is (= warning-message (:warnings response)))))

          "ECHO10 Ingest and Ingest Validation"
          :echo10 (data-umm-c/collection-missing-properties {}) "After translating item to UMM-C the metadata had the following issue: object has missing required properties ([\"CollectionProgress\",\"DataCenters\",\"Platforms\",\"ProcessingLevel\",\"ScienceKeywords\",\"TemporalExtents\"])"

          "DIF10 Ingest and Ingest Validation"
          :dif10 (data-umm-c/collection-missing-properties-dif10 {}) "After translating item to UMM-C the metadata had the following issue: object has missing required properties ([\"CollectionProgress\",\"ProcessingLevel\"])"

          "DIF9 Ingest and Ingest Validation"
          :dif (data-umm-c/collection-missing-properties-dif {}) "After translating item to UMM-C the metadata had the following issue: object has missing required properties ([\"CollectionProgress\",\"Platforms\",\"ProcessingLevel\",\"SpatialExtent\",\"TemporalExtents\"])"

          "ISO19115 Ingest and Ingest Validation"
          :iso19115 (data-umm-c/collection-missing-properties {}) "After translating item to UMM-C the metadata had the following issue: object has missing required properties ([\"CollectionProgress\",\"DataCenters\",\"Platforms\",\"ProcessingLevel\",\"ScienceKeywords\",\"SpatialExtent\",\"TemporalExtents\"])"

          "ISO SMAP Ingest and Ingest Validation"
          :iso-smap (data-umm-c/collection-missing-properties {}) "After translating item to UMM-C the metadata had the following issue: object has missing required properties ([\"CollectionProgress\",\"DataCenters\",\"Platforms\",\"ProcessingLevel\",\"ScienceKeywords\",\"SpatialExtent\",\"TemporalExtents\"])"

          "DIF9 with no version - has warnings, but passes ingest"
          :dif (assoc (data-umm-c/collection-missing-properties-dif {}) :Version nil)
          "After translating item to UMM-C the metadata had the following issue: object has missing required properties ([\"CollectionProgress\",\"Platforms\",\"ProcessingLevel\",\"SpatialExtent\",\"TemporalExtents\",\"Version\"])")))
