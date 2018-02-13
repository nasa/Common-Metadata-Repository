(ns cmr.system-int-test.ingest.collection-umm-spec-validation-test
  "CMR Ingest umm-spec validation integration tests"
  (:require
    [clj-time.core :as t]
    [clojure.test :refer :all]
    [cmr.common-app.test.side-api :as side]
    [cmr.common.util :as u :refer     [are3]]
    [cmr.ingest.config :as icfg]
    [cmr.spatial.line-string :as l]
    [cmr.spatial.mbr :as m]
    [cmr.spatial.polygon :as poly]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.umm-spec.models.umm-collection-models :as umm-c]
    [cmr.umm-spec.models.umm-common-models :as umm-cmn]
    [cmr.umm.umm-spatial :as umm-s]))

;; Used for testing invalid data date ranges.
(def umm-c-invalid-data-date-ranges
  "This is valid UMM-C with invalid data date ranges."
  {:Platforms [(umm-cmn/map->PlatformType
                 {:ShortName "A340-600" :LongName "Airbus A340-600" :Type "Aircraft"})]
   :ProcessingLevel (umm-c/map->ProcessingLevelType {:Id "3"})
   :DataCenters [(umm-cmn/map->DataCenterType
                   {:Roles ["ARCHIVER"]
                    :ShortName "AARHUS-HYDRO"
                    :LongName "Hydrogeophysics Group, Aarhus University "})]
   :ScienceKeywords [(umm-cmn/map->ScienceKeywordType
                      {:Category "EARTH SCIENCE SERVICES"
                       :Topic "DATA ANALYSIS AND VISUALIZATION"
                       :Term "GEOGRAPHIC INFORMATION SYSTEMS"})]
   :SpatialExtent (umm-cmn/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})
   :ShortName "short"
   :Version "V1"
   :EntryTitle "The entry title V5"
   :CollectionProgress "COMPLETE"
   :MetadataDates [(umm-cmn/map->DateType {:Date (t/date-time 2049)
                                           :Type "UPDATE"})
                   (umm-cmn/map->DateType {:Date (t/date-time 2011)
                                           :Type "REVIEW"})
                   (umm-cmn/map->DateType {:Date (t/date-time 2050)
                                           :Type "REVIEW"})
                   (umm-cmn/map->DateType {:Date (t/date-time 2049)
                                           :Type "DELETE"})]
   :DataDates [(umm-cmn/map->DateType {:Date (t/date-time 2050)
                                       :Type "CREATE"})
               (umm-cmn/map->DateType {:Date (t/date-time 2049)
                                       :Type "UPDATE"})
               (umm-cmn/map->DateType {:Date (t/date-time 2011)
                                       :Type "UPDATE"})
               (umm-cmn/map->DateType {:Date (t/date-time 2011)
                                       :Type "REVIEW"})]
   :Abstract "A very abstract collection"
   :TemporalExtents [(umm-cmn/map->TemporalExtentType {:SingleDateTimes [(t/date-time 2012)]})]})

(defn collection-invalid-data-date-ranges
  "Returns a UmmCollection with invalid data date ranges"
  []
  (umm-c/map->UMM-C umm-c-invalid-data-date-ranges))

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
      (let [response (d/ingest-umm-spec-collection
                       "PROV1"
                       (data-umm-c/collection-missing-properties
                         {:AdditionalAttributes
                          [(data-umm-cmn/additional-attribute {:Name "bool1" :DataType "BOOLEAN" :Value true})
                           (data-umm-cmn/additional-attribute {:Name "bool2" :DataType "BOOLEAN" :Value true})]})
                       {:allow-failure? true})]
        (is (= {:status 422
                :errors ["object has missing required properties ([\"CollectionProgress\",\"DataCenters\",\"Platforms\",\"ProcessingLevel\",\"ScienceKeywords\",\"TemporalExtents\"])"]}
               (select-keys response [:status :errors])))))

    (testing "schema validation errors not returned"
      (side/eval-form `(icfg/set-return-umm-json-validation-errors! false))
      (assert-valid {:AdditionalAttributes [(data-umm-cmn/additional-attribute {:Name "bool1" :DataType "BOOLEAN" :Value true})
                                            (data-umm-cmn/additional-attribute {:Name "bool2" :DataType "BOOLEAN" :Value true})]})))

  (testing "UMM-C JSON-Schema validation through Cmr-Validate-Umm-C header"
    (testing "schema validation errors returned when Cmr-Validate-Umm-C header is true"
      (let [response (d/ingest-umm-spec-collection
                       "PROV1"
                       (data-umm-c/collection-missing-properties
                         {:AdditionalAttributes
                           [(data-umm-cmn/additional-attribute {:Name "bool1" :DataType "BOOLEAN" :Value true})
                            (data-umm-cmn/additional-attribute {:Name "bool2" :DataType "BOOLEAN" :Value true})]})
                       {:allow-failure? true :validate-umm-c true})]
        (is (= {:status 422
                :errors ["object has missing required properties ([\"CollectionProgress\",\"DataCenters\",\"Platforms\",\"ProcessingLevel\",\"ScienceKeywords\",\"TemporalExtents\"])"]}
               (select-keys response [:status :errors])))))

    (testing "schema validation error returns is controlled by config setting when Cmr-Validate-Umm-C header is NOT true"
      (let [coll-attr {:AdditionalAttributes
                       [(data-umm-cmn/additional-attribute {:Name "bool1" :DataType "BOOLEAN" :Value true})
                        (data-umm-cmn/additional-attribute {:Name "bool2" :DataType "BOOLEAN" :Value true})]}]
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
                     :ScienceKeywords [(data-umm-cmn/science-keyword {:Category "upcase"
                                                                      :Topic "Cool"
                                                                      :Term "Mild"})]
                     :SpatialExtent (data-umm-c/spatial {:gsr "CARTESIAN"})
                     :RelatedUrls [(data-umm-c/related-url {:Type "type" :URL "http://www.foo.com"})]
                     :DataCenters [(data-umm-c/data-center {:Roles ["ARCHIVER"]
                                                            :ShortName "Larc"})]
                     :Platforms [{:ShortName "plat"
                                  :LongName "plat"
                                  :Type "Aircraft"
                                  :Instruments [{:ShortName "inst"}]}]
                     :TemporalExtents [(data-umm-cmn/temporal-extent
                                         {:beginning-date-time "1965-12-12T07:00:00.000-05:00"
                                          :ending-date-time "1967-12-12T07:00:00.000-05:00"})]
                     :CollectionProgress :complete
                     :AdditionalAttributes
                     [(data-umm-cmn/additional-attribute {:Name "bool" :DataType "BOOLEAN" :Value true})
                      (data-umm-cmn/additional-attribute {:Name "bool" :DataType "BOOLEAN" :Value true})]}]
      (side/eval-form `(icfg/set-return-umm-json-validation-errors! false))
      (are3 [coll-attributes field-path error options]
            (assert-invalid coll-attributes field-path error options)

            "Set Cmr-Validate-Umm-C header to true - schema validation passed, umm-spec validation error is returned"
            coll-attr
            ["AdditionalAttributes"]
            ["Additional Attributes must be unique. This contains duplicates named [bool]."]
            {:validate-umm-c true})))

  (testing "Additional Attribute validation"
    (assert-invalid
      {:AdditionalAttributes
       [(data-umm-cmn/additional-attribute {:Name "bool" :DataType "BOOLEAN" :Value true})
        (data-umm-cmn/additional-attribute {:Name "bool" :DataType "BOOLEAN" :Value true})]}
      ["AdditionalAttributes"]
      ["Additional Attributes must be unique. This contains duplicates named [bool]."]))

  (testing "Additional Attribute parameter range validation"
    (assert-invalid
      {:AdditionalAttributes
       [(data-umm-cmn/additional-attribute {:Name "int"
                                          :DataType "INT"
                                          :ParameterRangeBegin 100
                                          :ParameterRangeEnd 0})]}
      ["AdditionalAttributes" 0]
      ["Parameter Range Begin [100] cannot be greater than Parameter Range End [0]."]))

  (testing "Additional Attribute parameter range value validation"
    (assert-invalid
      {:AdditionalAttributes
       [(data-umm-cmn/additional-attribute {:Name "float"
                                          :DataType "FLOAT"
                                          :ParameterRangeBegin 0.0
                                          :ParameterRangeEnd 10.0
                                          :Value 12.0})]}
      ["AdditionalAttributes" 0]
      ["Value [12.0] cannot be greater than Parameter Range End [10.0]."]))

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

          "umm-json Ingest and Ingest Validation for Invalid data date ranges"
          :umm-json (collection-invalid-data-date-ranges) "After translating item to UMM-C the metadata had the following issue: [:MetadataDates] latest UPDATE date value: [2049-01-01T00:00:00.000Z] should be in the past.  earliest REVIEW date value: [2011-01-01T00:00:00.000Z] should be in the future.  DELETE date value: [2049-01-01T00:00:00.000Z] should be equal or later than latest REVIEW date value: [2050-01-01T00:00:00.000Z].After translating item to UMM-C the metadata had the following issue: [:DataDates] CREATE date value: [2050-01-01T00:00:00.000Z] should be in the past.  latest UPDATE date value: [2049-01-01T00:00:00.000Z] should be in the past.  earliest REVIEW date value: [2011-01-01T00:00:00.000Z] should be in the future.  Earliest UPDATE date value: [2011-01-01T00:00:00.000Z] should be equal or later than CREATE date value: [2050-01-01T00:00:00.000Z]."

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
