(ns cmr.system-int-test.ingest.collection-umm-spec-validation-test
  "CMR Ingest umm-spec validation integration tests"
  (:require
    [clj-time.core :as time]
    [clojure.string :as string]
    [clojure.test :refer :all]
    [cmr.common-app.test.side-api :as side]
    [cmr.common.util :as u :refer [are3]]
    [cmr.ingest.config :as ingest-config]
    [cmr.spatial.polygon :as polygon]
    [cmr.system-int-test.data2.core :as data-core]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.umm-spec.models.umm-collection-models :as umm-c]
    [cmr.umm-spec.models.umm-common-models :as umm-cmn]
    [cmr.umm-spec.versioning :as umm-spec-versioning]
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
   :SpatialExtent (umm-c/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})
   :ShortName "short"
   :Version "V1"
   :EntryTitle "The entry title V5"
   :CollectionProgress "COMPLETE"
   :MetadataDates [(umm-cmn/map->DateType {:Date (time/date-time 2049)
                                           :Type "UPDATE"})
                   (umm-cmn/map->DateType {:Date (time/date-time 2011)
                                           :Type "REVIEW"})
                   (umm-cmn/map->DateType {:Date (time/date-time 2050)
                                           :Type "REVIEW"})
                   (umm-cmn/map->DateType {:Date (time/date-time 2049)
                                           :Type "DELETE"})]
   :DataDates [(umm-cmn/map->DateType {:Date (time/date-time 2050)
                                       :Type "CREATE"})
               (umm-cmn/map->DateType {:Date (time/date-time 2049)
                                       :Type "UPDATE"})
               (umm-cmn/map->DateType {:Date (time/date-time 2011)
                                       :Type "UPDATE"})
               (umm-cmn/map->DateType {:Date (time/date-time 2011)
                                       :Type "REVIEW"})]
   :Abstract "A very abstract collection"
   :TemporalExtents [(umm-cmn/map->TemporalExtentType {:SingleDateTimes [(time/date-time 2012)]})]
   :DOI (umm-cmn/map->DoiType {:DOI "10.5678/TestDOI1"})
   :MetadataSpecification (umm-c/map->MetadataSpecificationType
                             {:URL (str "https://cdn.earthdata.nasa.gov/umm/collection/v"
                                        umm-spec-versioning/current-collection-version),
                              :Name "UMM-C"
                              :Version umm-spec-versioning/current-collection-version})})

(defn collection-invalid-data-date-ranges
  "Returns a UmmCollection with invalid data date ranges"
  []
  (umm-c/map->UMM-C umm-c-invalid-data-date-ranges))

(defn polygon
  "Creates a single ring polygon with the given ordinates. Points must be in counter clockwise order."
  [& ords]
  (polygon/polygon [(apply umm-s/ords->ring ords)]))

(defn assert-invalid
  ([coll-attributes field-path errors]
   (assert-invalid coll-attributes field-path errors nil))
  ([coll-attributes field-path errors options]
   (let [response (data-core/ingest-umm-spec-collection
                   "PROV1"
                   (data-umm-c/collection-missing-properties coll-attributes)
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
         response (data-core/ingest-umm-spec-collection provider-id collection options)]
     (is (#{{:status 200} {:status 201}} (select-keys response [:status :errors]))))))

(defn assert-invalid-spatial
  ([coord-sys hsd field-path errors]
   (assert-invalid-spatial coord-sys hsd field-path errors nil))
  ([coord-sys hsd field-path errors options]
   (assert-invalid {:SpatialExtent (data-umm-c/spatial {:gsr coord-sys :hsd hsd})}
                   field-path
                   errors
                   options)))

(defn assert-valid-spatial
  ([coord-sys hsd]
   (assert-valid-spatial coord-sys hsd nil))
  ([coord-sys hsd options]
   (assert-valid {:SpatialExtent (data-umm-c/spatial {:gsr coord-sys :hsd hsd})} options)))

(defn- spatial-with-enums
  "Create a SpatialExtent with spatial enums. Helper for testing."
  [{:keys [granule-spatial-representation coordinate-system]}]
  {:SpatialExtent (data-umm-c/spatial {:gsr granule-spatial-representation
                                       :hsd {:Geometry (umm-c/map->GeometryType
                                                        {:CoordinateSystem coordinate-system
                                                         :Points [(umm-c/map->PointType
                                                                   {:Longitude 0 :Latitude 0})]})}})})

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest collection-umm-spec-validation-test
  (testing "UMM-C JSON-Schema validation through config settings"
    (testing "schema validation errors returned"
      (side/eval-form `(ingest-config/set-return-umm-json-validation-errors! true))
      (let [response (data-core/ingest-umm-spec-collection
                       "PROV1"
                       (data-umm-c/collection-missing-properties
                         {:AdditionalAttributes
                          [(data-umm-cmn/additional-attribute {:Name "bool1" :DataType "BOOLEAN" :Value true})
                           (data-umm-cmn/additional-attribute {:Name "bool2" :DataType "BOOLEAN" :Value true})]})
                       {:allow-failure? true})]
        (is (= {:status 422

                :errors ["#: required key [DataCenters] not found"
                         "#: required key [ProcessingLevel] not found"
                         "#: required key [ScienceKeywords] not found"
                         "#: required key [TemporalExtents] not found"
                         "#: required key [Platforms] not found"
                         "#: required key [CollectionProgress] not found"]}
               (select-keys response [:status :errors])))))

    (testing "schema validation errors not returned"
      (side/eval-form `(ingest-config/set-return-umm-json-validation-errors! false))
      (assert-valid {:AdditionalAttributes [(data-umm-cmn/additional-attribute {:Name "bool1" :DataType "BOOLEAN" :Value true})
                                            (data-umm-cmn/additional-attribute {:Name "bool2" :DataType "BOOLEAN" :Value true})]})))

  (testing "UMM-C JSON-Schema validation through Cmr-Validate-Umm-C header"
    (testing "schema validation errors returned when Cmr-Validate-Umm-C header is true"
      (let [response (data-core/ingest-umm-spec-collection
                       "PROV1"
                       (data-umm-c/collection-missing-properties
                         {:AdditionalAttributes
                           [(data-umm-cmn/additional-attribute {:Name "bool1" :DataType "BOOLEAN" :Value true})
                            (data-umm-cmn/additional-attribute {:Name "bool2" :DataType "BOOLEAN" :Value true})]})
                       {:allow-failure? true :validate-umm-c true})]
        (is (= {:status 422
                :errors ["#: required key [DataCenters] not found"
                         "#: required key [ProcessingLevel] not found"
                         "#: required key [ScienceKeywords] not found"
                         "#: required key [TemporalExtents] not found"
                         "#: required key [Platforms] not found"
                         "#: required key [CollectionProgress] not found"]}
               (select-keys response [:status :errors])))))

    (testing "schema validation error returns is controlled by config setting when Cmr-Validate-Umm-C header is NOT true"
      (let [coll-attr {:AdditionalAttributes
                       [(data-umm-cmn/additional-attribute {:Name "bool1" :DataType "BOOLEAN" :Value true})
                        (data-umm-cmn/additional-attribute {:Name "bool2" :DataType "BOOLEAN" :Value true})]}]
        (side/eval-form `(ingest-config/set-return-umm-json-validation-errors! false))
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
      (side/eval-form `(ingest-config/set-return-umm-json-validation-errors! false))
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
    (testing "Spatial enums"
      (testing "Invalid GranuleSpatialRepresentation enum"
        (assert-invalid
         (spatial-with-enums {:granule-spatial-representation "INVALID_GRANULE_SPATIAL_REPRESENTATION"})
         ["SpatialExtent"]
         ["Value [INVALID_GRANULE_SPATIAL_REPRESENTATION] not found in enum (possible values: [\"CARTESIAN\",\"GEODETIC\",\"NO_SPATIAL\",\"ORBIT\"])"]
         {:format :iso19115}))

      (testing "Valid GranuleSpatialRepresentation enum"
        (assert-valid
         (spatial-with-enums {:granule-spatial-representation "CARTESIAN"})
         {:format :iso19115}))

      (testing "Invalid CoordinateSystem enum"
        (assert-invalid
         (spatial-with-enums {:coordinate-system "INVALID_COORDINATE_SYSTEM"})
         ["SpatialExtent" "HorizontalSpatialDomain" "Geometry" "CoordinateSystem"]
         ["Value [INVALID_COORDINATE_SYSTEM] not found in enum (possible values: [\"CARTESIAN\",\"GEODETIC\"])"]
         {:format :iso19115}))

      (testing "Valid CoordinateSystem enum"
        (assert-valid
         (spatial-with-enums {:coordinate-system "GEODETIC"})
         {:format :iso19115})))

    (testing "geodetic polygon"
      ;; Invalid points are caught in the schema validation
      (assert-invalid-spatial
        "GEODETIC"
        {:Geometry (umm-c/map->GeometryType
                     {:CoordinateSystem "GEODETIC"
                      :GPolygons [(umm-c/map->GPolygonType
                                    {:Boundary (umm-c/map->BoundaryType
                                                 {:Points [(umm-c/map->PointType {:Longitude 180 :Latitude 90})
                                                           (umm-c/map->PointType {:Longitude -180 :Latitude 90})
                                                           (umm-c/map->PointType {:Longitude -180 :Latitude -90})
                                                           (umm-c/map->PointType {:Longitude 180 :Latitude -90})
                                                           (umm-c/map->PointType {:Longitude 180 :Latitude 90})]})})]})}
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
        {:Geometry (umm-c/map->GeometryType
                     {:CoordinateSystem "GEODETIC"
                      :GPolygons [(umm-c/map->GPolygonType
                                    {:Boundary (umm-c/map->BoundaryType
                                                 {:Points [(umm-c/map->PointType {:Longitude 1 :Latitude 1})
                                                           (umm-c/map->PointType {:Longitude -1 :Latitude 1})
                                                           (umm-c/map->PointType {:Longitude -1 :Latitude -1})
                                                           (umm-c/map->PointType {:Longitude 1 :Latitude -1})
                                                           (umm-c/map->PointType {:Longitude 1 :Latitude 1})]})
                                     :ExclusiveZone
                                       (umm-c/map->ExclusiveZoneType
                                         {:Boundaries [(umm-c/map->BoundaryType
                                                         {:Points  [(umm-c/map->PointType {:Longitude 0 :Latitude 0})
                                                                    (umm-c/map->PointType {:Longitude 0.004 :Latitude 0})
                                                                    (umm-c/map->PointType {:Longitude 0.006 :Latitude 0.005})
                                                                    (umm-c/map->PointType {:Longitude 0.002 :Latitude 0.005})
                                                                    (umm-c/map->PointType {:Longitude 0 :Latitude 0})]})]})})]})}))

    (testing "cartesian polygon"
      ;; The same shape from geodetic is valid as a cartesian.
      ;; Cartesian validation is not supported yet. See CMR-1172
      (assert-valid-spatial
        "CARTESIAN"
        {:Geometry (umm-c/map->GeometryType
                     {:CoordinateSystem "CARTESIAN"
                      :GPolygons [(umm-c/map->GPolygonType
                                    {:Boundary (umm-c/map->BoundaryType
                                                 {:Points [(umm-c/map->PointType {:Longitude 180 :Latitude 90})
                                                           (umm-c/map->PointType {:Longitude -180 :Latitude 90})
                                                           (umm-c/map->PointType {:Longitude -180 :Latitude -90})
                                                           (umm-c/map->PointType {:Longitude 180 :Latitude -90})
                                                           (umm-c/map->PointType {:Longitude 180 :Latitude 90})]})})]})}))

    (testing "geodetic line"
      (assert-invalid-spatial
        "GEODETIC"
        {:Geometry (umm-c/map->GeometryType
                     {:CoordinateSystem "GEODETIC"
                      :Lines [(umm-c/map->LineType
                                {:Points [(umm-c/map->PointType {:Longitude 0 :Latitude 0})
                                          (umm-c/map->PointType {:Longitude 1 :Latitude 1})
                                          (umm-c/map->PointType {:Longitude 2 :Latitude 2})
                                          (umm-c/map->PointType {:Longitude 1 :Latitude 1})]})]})}
        ["SpatialExtent" "HorizontalSpatialDomain" "Geometry" "Lines" 0]
        ["Spatial validation error: The shape contained duplicate points. Points 2 [lon=1 lat=1] and 4 [lon=1 lat=1] were considered equivalent or very close."]))

    (testing "cartesian line"
      ;; Cartesian line validation isn't supported yet. See CMR-1172
      (assert-valid-spatial
        "CARTESIAN"
        {:Geometry (umm-c/map->GeometryType
                     {:CoordinateSystem "CARTESIAN"
                      :Lines [(umm-c/map->LineType
                                {:Points [(umm-c/map->PointType {:Longitude 180 :Latitude 0})
                                          (umm-c/map->PointType {:Longitude -180 :Latitude 0})]})]})}))
    (testing "bounding box"
      (assert-invalid-spatial
        "GEODETIC"
        {:Geometry (umm-c/map->GeometryType
                     {:CoordinateSystem "GEODETIC"
                      :BoundingRectangles [(umm-c/map->BoundingRectangleType {:WestBoundingCoordinate -180
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
            (let [response (data-core/ingest-umm-spec-collection "PROV1" collection {:format format})]
              (is (#{200 201} (:status response)))
              (is (= warning-message (:warnings response))))
            (let [response (ingest/validate-concept (data-umm-c/collection-concept collection format))]
              (is (= 200 (:status response)))
              (is (= warning-message (:warnings response)))))

          "ECHO10 Ingest and Ingest Validation"
          :echo10 (data-umm-c/collection-missing-properties {})
          "After translating item to UMM-C the metadata had the following issue(s): #: required key [DataCenters] not found;; #: required key [ProcessingLevel] not found;; #: required key [ScienceKeywords] not found;; #: required key [TemporalExtents] not found;; #: required key [Platforms] not found;; #: required key [CollectionProgress] not found"

          "umm-json Ingest and Ingest Validation for Invalid data date ranges"
          :umm-json (collection-invalid-data-date-ranges)
          "After translating item to UMM-C the metadata had the following issue(s): [:MetadataDates] latest UPDATE date value: [2049-01-01T00:00:00.000Z] should be in the past. earliest REVIEW date value: [2011-01-01T00:00:00.000Z] should be in the future. DELETE date value: [2049-01-01T00:00:00.000Z] should be equal or later than latest REVIEW date value: [2050-01-01T00:00:00.000Z].;; [:DataDates] CREATE date value: [2050-01-01T00:00:00.000Z] should be in the past. latest UPDATE date value: [2049-01-01T00:00:00.000Z] should be in the past. earliest REVIEW date value: [2011-01-01T00:00:00.000Z] should be in the future. Earliest UPDATE date value: [2011-01-01T00:00:00.000Z] should be equal or later than CREATE date value: [2050-01-01T00:00:00.000Z].;; [:Platforms 0] Platform short name [A340-600], long name [Airbus A340-600], and type [Aircraft] was not a valid keyword combination."
          "DIF10 Ingest and Ingest Validation"
          :dif10 (data-umm-c/collection-missing-properties-dif10 {})
          "After translating item to UMM-C the metadata had the following issue(s): #: required key [ProcessingLevel] not found;; #: required key [CollectionProgress] not found;; [:Platforms 0] Platform short name [A340-600], long name [Airbus A340-600], and type [null] was not a valid keyword combination.;; [:Platforms 0 :Instruments 0] Instrument short name [Not provided] and long name [null] was not a valid keyword combination.;; [:Projects 0] Project short name [Not provided] and long name [null] was not a valid keyword combination."
          "DIF9 Ingest and Ingest Validation"
          :dif (data-umm-c/collection-missing-properties-dif {})
          "After translating item to UMM-C the metadata had the following issue(s): #: required key [ProcessingLevel] not found;; #: required key [TemporalExtents] not found;; #: required key [SpatialExtent] not found;; #: required key [Platforms] not found;; #: required key [CollectionProgress] not found"
          "ISO19115 Ingest and Ingest Validation"
          :iso19115 (data-umm-c/collection-missing-properties {})
          "After translating item to UMM-C the metadata had the following issue(s): #: required key [DataCenters] not found;; #: required key [ProcessingLevel] not found;; #: required key [ScienceKeywords] not found;; #: required key [TemporalExtents] not found;; #: required key [SpatialExtent] not found;; #: required key [Platforms] not found;; #: required key [CollectionProgress] not found"

          "ISO SMAP Ingest and Ingest Validation"
          :iso-smap (data-umm-c/collection-missing-properties {})
          "After translating item to UMM-C the metadata had the following issue(s): #: required key [DataCenters] not found;; #: required key [ProcessingLevel] not found;; #: required key [ScienceKeywords] not found;; #: required key [TemporalExtents] not found;; #: required key [SpatialExtent] not found;; #: required key [Platforms] not found;; #: required key [CollectionProgress] not found"

          "DIF9 with no version - has warnings, but passes ingest"
          :dif (assoc (data-umm-c/collection-missing-properties-dif {}) :Version nil)
          "After translating item to UMM-C the metadata had the following issue(s): #: required key [Version] not found;; #: required key [ProcessingLevel] not found;; #: required key [TemporalExtents] not found;; #: required key [SpatialExtent] not found;; #: required key [Platforms] not found;; #: required key [CollectionProgress] not found")))

(deftest umm-spec-temporal-validation
  (testing "Invalid temporal extents"
    (let [invalid-char-range-umm (assoc (data-umm-c/collection)
                                        :TemporalExtents
                                        [{:RangeDateTimes [{:BeginningDateTime "2000-01-01c"
                                                            :EndingDateTime "2006-01-01"}]}])
          invalid-char-single-umm (assoc (data-umm-c/collection)
                                         :TemporalExtents
                                         [{:SingleDateTimes ["2000-01-01" "1993-07-29S" "2005-01-01T00:00:00Z"]}])
          invalid-year-range-umm (assoc (data-umm-c/collection)
                                        :TemporalExtents
                                        [{:RangeDateTimes [{:BeginningDateTime "2005-01-01"
                                                            :EndingDateTime "200004-01-01"}]}])
          invalid-year-single-umm (assoc (data-umm-c/collection)
                                         :TemporalExtents
                                         [{:SingleDateTimes ["2000-01-01" "1999-01-01T00:00:00Z" "20000006-01-01"]}])
          invalid-year-periodic-umm (assoc (data-umm-c/collection)
                                           :TemporalExtents
                                           [{:PeriodicDateTimes [{:Name "Periodic name"
                                                                  :DurationUnit "DAY"
                                                                  :DurationValue "1"
                                                                  :PeriodCycleDurationUnit "DAY"
                                                                  :PeriodCycleDurationValue "1"
                                                                  :StartDate "2005-01-01"
                                                                  :EndDate "123456789-10-11"}]}])]
      (are3 [format collection error-message]
            (let [response (data-core/ingest-umm-spec-collection "PROV1"
                                                                 collection
                                                                 {:format format
                                                                  :allow-failure? true})]
              (is (= 422 (:status response)))
              (is (= error-message (first (:errors response)))))

            "DIF9 invalid character in RangeDateTimes"
            :dif invalid-char-range-umm "[2000-01-01c] is not a valid datetime"

            "DIF10 invalid year in RangeDateTimes"
            :dif10 invalid-year-range-umm "[200004-01-01] is not a valid datetime"

            "DIF10 invalid year in SingleDateTimes"
            :dif10 invalid-year-single-umm "[20000006-01-01] is not a valid datetime"

            "DIF10 invalid year in PeriodicDateTimes"
            :dif10 invalid-year-periodic-umm "[123456789-10-11] is not a valid datetime"

            "ISO SMAP invalid character in RangeDateTimes"
            :iso-smap invalid-char-range-umm "[2000-01-01c] is not a valid datetime"

            "ISO SMAP invalid character in SingleDateTimes"
            :iso-smap invalid-char-single-umm "[1993-07-29S] is not a valid datetime"

            "ISO MENDS invalid character in RangeDateTimes"
            :iso19115 invalid-char-range-umm "[2000-01-01c] is not a valid datetime"

            "ISO MENDS invalid character in SingleDateTimes"
            :iso19115 invalid-char-single-umm "[1993-07-29S] is not a valid datetime"))))


(deftest umm-spec-direct-distribution-info-validation-tests
  (testing "Invalid Direct Distribution Information "
    (let [invalid-nil-region-umm (assoc (data-umm-c/collection)
                                        :DirectDistributionInformation
                                         {:Region nil
                                          :S3BucketAndObjectPrefixNames ["one" "two"]
                                          :S3CredentialsAPIEndpoint "hello"
                                          :S3CredentialsAPIDocumentationURL "hello"})
          invalid-region-umm (assoc (data-umm-c/collection)
                                    :DirectDistributionInformation
                                     {:Region "hello"
                                      :S3BucketAndObjectPrefixNames ["one" "two"]
                                      :S3CredentialsAPIEndpoint "hello"
                                      :S3CredentialsAPIDocumentationURL "hello"})
          invalid-url-umm (assoc (data-umm-c/collection)
                                 :DirectDistributionInformation
                                  {:Region "us-west-1"
                                   :S3BucketAndObjectPrefixNames ["one" "two"]
                                   :S3CredentialsAPIEndpoint "hello"
                                   :S3CredentialsAPIDocumentationURL "hello"})]

      (are3 [format error-code collection error-message]
        (let [response (data-core/ingest-umm-spec-collection "PROV1"
                                                             collection
                                                             {:format format
                                                              :allow-failure? true
                                                              :validate-umm-c true})]
          (is (= error-code (:status response)))
          (is (string/includes? (first (:errors response))  error-message)))

        "DIF10 nil Region is invalid"
        :dif10 400 invalid-nil-region-umm "Invalid content was found starting with element 'S3BucketAndObjectPrefixName"

        "ECHO10 nil Region is invalid"
        :echo10 400 invalid-nil-region-umm "Invalid content was found starting with element 'S3BucketAndObjectPrefixName"

        "ISO 19115 nil Region is invalid"
        :iso19115 422 invalid-nil-region-umm "#/DirectDistributionInformation: required key [Region] not found"

        "DIF10 bad Region is invalid"
        :dif10 400 invalid-region-umm "Value 'hello' is not facet-valid with respect to enumeration"

        "ECHO10 bad Region is invalid"
        :echo10 400 invalid-region-umm "Value 'hello' is not facet-valid with respect to enumeration"

        "ISO 19115 bad Region is invalid"
        :iso19115 422 invalid-region-umm "DirectDistributionInformation/Region: hello is not a valid enum value"

        "DIF10 bad URL is invalid"
        :dif10 422 invalid-url-umm "[hello] is not a valid URL"

        "ECHO10 bad URL is invalid"
        :echo10 422 invalid-url-umm "[hello] is not a valid URL"

        "ISO 19115 bad URL is invalid"
        :iso19115 422 invalid-url-umm "[hello] is not a valid URL"))))
