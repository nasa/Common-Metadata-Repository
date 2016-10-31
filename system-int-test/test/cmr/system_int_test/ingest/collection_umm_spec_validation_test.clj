(ns cmr.system-int-test.ingest.collection-umm-spec-validation-test
  "CMR Ingest umm-spec validation integration tests"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.system-int-test.data2.core :as d]
    [cmr.umm.umm-spatial :as umm-s]
    [cmr.common.util :as u :refer [are3]]
    [cmr.common-app.test.side-api :as side]
    [cmr.spatial.polygon :as poly]
    [cmr.spatial.line-string :as l]
    [cmr.spatial.mbr :as m]
    [cmr.ingest.config :as icfg]))

(defn polygon
  "Creates a single ring polygon with the given ordinates. Points must be in counter clockwise order."
  [& ords]
  (poly/polygon [(apply umm-s/ords->ring ords)]))

(defn assert-invalid
  ([coll-attributes field-path errors]
   (assert-invalid coll-attributes field-path errors nil))
  ([coll-attributes field-path errors options]
   (let [response (d/ingest "PROV1" (dc/collection coll-attributes)
                            (merge {:allow-failure? true} options))]
     (is (= {:status 422
             :errors [{:path field-path
                       :errors errors}]}
            (select-keys response [:status :errors]))))))

(defn assert-valid
  ([coll-attributes]
   (assert-valid coll-attributes nil))
  ([coll-attributes options]
   (let [collection (assoc (dc/collection coll-attributes) :native-id (:native-id coll-attributes))
         provider-id (get coll-attributes :provider-id "PROV1")
         response (d/ingest provider-id collection options)]
     (is (#{{:status 200} {:status 201}} (select-keys response [:status :errors]))))))

(defn assert-invalid-spatial
  ([coord-sys shapes field-path errors]
   (assert-invalid-spatial coord-sys shapes field-path errors nil))
  ([coord-sys shapes field-path errors options]
   (let [shapes (map (partial umm-s/set-coordinate-system coord-sys) shapes)]
     (assert-invalid {:spatial-coverage (dc/spatial {:gsr coord-sys
                                                     :sr coord-sys
                                                     :geometries shapes})}
                     field-path
                     errors
                     options))))

(defn assert-valid-spatial
  [coord-sys shapes]
  (let [shapes (map (partial umm-s/set-coordinate-system coord-sys) shapes)]
    (assert-valid {:spatial-coverage (dc/spatial {:gsr coord-sys
                                                  :sr coord-sys
                                                  :geometries shapes})})))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest collection-umm-spec-validation-test
  (testing "UMM-C JSON-Schema validation through config settings"
    (testing "schema validation errors returned"
      (side/eval-form `(icfg/set-return-umm-json-validation-errors! true))
      (let [response (d/ingest "PROV1" 
                               (dc/collection {:product-specific-attributes
                                               [(dc/psa {:name "bool1" :data-type :boolean :value true})
                                                (dc/psa {:name "bool2" :data-type :boolean :value true})]})
                               {:allow-failure? true})]
        (is (= {:status 422
                :errors ["object has missing required properties ([\"DataCenters\",\"Platforms\",\"ProcessingLevel\",\"RelatedUrls\",\"ScienceKeywords\",\"SpatialExtent\",\"TemporalExtents\"])"]}
               (select-keys response [:status :errors])))))

    (testing "schema validation errors not returned" 
      (side/eval-form `(icfg/set-return-umm-json-validation-errors! false))
      (assert-valid {:product-specific-attributes [(dc/psa {:name "bool1" :data-type :boolean :value true})
                                                   (dc/psa {:name "bool2" :data-type :boolean :value true})]})))

  (testing "UMM-C JSON-Schema validation through Cmr-Validate-Umm-C header"
    (testing "schema validation errors returned when Cmr-Validate-Umm-C header is true"
      (let [response (d/ingest "PROV1" (dc/collection {:product-specific-attributes
                                                       [(dc/psa {:name "bool1" :data-type :boolean :value true})
                                                        (dc/psa {:name "bool2" :data-type :boolean :value true})]})
                               {:allow-failure? true :validate-umm-c true})]
        (is (= {:status 422
                :errors ["object has missing required properties ([\"DataCenters\",\"Platforms\",\"ProcessingLevel\",\"RelatedUrls\",\"ScienceKeywords\",\"SpatialExtent\",\"TemporalExtents\"])"]}
               (select-keys response [:status :errors])))))

    (testing "schema validation error returns is controlled by config setting when Cmr-Validate-Umm-C header is NOT true" 
      (let [coll-attr {:product-specific-attributes
                       [(dc/psa {:name "bool1" :data-type :boolean :value true})
                        (dc/psa {:name "bool2" :data-type :boolean :value true})]}]
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
    (let [coll-attr {:processing-level-id "1"
                     :science-keywords [(dc/science-keyword {:category "upcase"
                                                             :topic "Cool"
                                                             :term "Mild"})]
                     :spatial-coverage (dc/spatial {:gsr :cartesian})
                     :related-urls [(dc/related-url {:type "type" :url "http://www.foo.com"})]
                     :organizations [{:type :archive-center :org-name "Larc"}]
                     :platforms [{:short-name "plat"
                                  :long-name "plat"
                                  :type "Aircraft"
                                  :instruments [{:short-name "inst"}]}]
                     :beginning-date-time "1965-12-12T07:00:00.000-05:00"
                     :ending-date-time "1967-12-12T07:00:00.000-05:00"
                     :product-specific-attributes
                     [(dc/psa {:name "bool" :data-type :boolean :value true})
                      (dc/psa {:name "bool" :data-type :boolean :value true})]}]
      (side/eval-form `(icfg/set-return-umm-json-validation-errors! false))
      (side/eval-form `(icfg/set-return-umm-spec-validation-errors! false))
      (are3 [coll-attributes field-path error options]
            (assert-invalid coll-attributes field-path error options)

            "Set Cmr-Validate-Umm-C header to true - schema validation passed, umm-spec validation error is returned"
            coll-attr
            ["AdditionalAttributes"]
            ["Additional Attributes must be unique. This contains duplicates named [bool]."]
            {:validate-umm-c true}

            "Set Cmr-Validate-Umm-C header to false - schema validation passed, umm-spec validation error is not returned"
            coll-attr
            ["ProductSpecificAttributes"]
            ["Product Specific Attributes must be unique. This contains duplicates named [bool]."]
            {:validate-umm-c false}

            "Do not set Cmr-Validate-Umm-C header - schema validation passed, umm-spec validation error is not returned"
            coll-attr
            ["ProductSpecificAttributes"]
            ["Product Specific Attributes must be unique. This contains duplicates named [bool]."]
            nil)))
  
  (side/eval-form `(icfg/set-return-umm-spec-validation-errors! true))

  (testing "Additional Attribute validation"
    (assert-invalid
      {:product-specific-attributes
       [(dc/psa {:name "bool" :data-type :boolean :value true})
        (dc/psa {:name "bool" :data-type :boolean :value true})]}
      ["AdditionalAttributes"]
      ["Additional Attributes must be unique. This contains duplicates named [bool]."]))

  (testing "Nested Path Validation"
    (assert-invalid
      {:platforms [(dc/platform {:instruments [(dc/instrument {:short-name "I1"})
                                               (dc/instrument {:short-name "I1"})]})]}
      ["Platforms" 0 "Instruments"]
      ["Instruments must be unique. This contains duplicates named [I1]."]))

  (testing "Spatial validation"
    (testing "geodetic polygon"
      ;; Invalid points are caught in the schema validation
      (assert-invalid-spatial
        :geodetic
        [(polygon 180 90, -180 90, -180 -90, 180 -90, 180 90)]
        ["SpatialExtent" "HorizontalSpatialDomain" "Geometry" "GPolygons" 0]
        ["Spatial validation error: The shape contained duplicate points. Points 1 [lon=180 lat=90] and 2 [lon=-180 lat=90] were considered equivalent or very close."
         "Spatial validation error: The shape contained duplicate points. Points 3 [lon=-180 lat=-90] and 4 [lon=180 lat=-90] were considered equivalent or very close."
         "Spatial validation error: The shape contained consecutive antipodal points. Points 2 [lon=-180 lat=90] and 3 [lon=-180 lat=-90] are antipodal."
         "Spatial validation error: The shape contained consecutive antipodal points. Points 4 [lon=180 lat=-90] and 5 [lon=180 lat=90] are antipodal."]))

    (testing "geodetic polygon with holes"
      ;; Hole validation is not supported yet. See CMR-1173.
      ;; Holes are ignored during validation for now.
      (assert-valid-spatial
        :geodetic
        [(poly/polygon [(umm-s/ords->ring 1 1, -1 1, -1 -1, 1 -1, 1 1)
                        (umm-s/ords->ring 0,0, 0.00004,0, 0.00006,0.00005, 0.00002,0.00005, 0,0)])]))
    (testing "cartesian polygon"
      ;; The same shape from geodetic is valid as a cartesian.
      ;; Cartesian validation is not supported yet. See CMR-1172
      (assert-valid-spatial :cartesian
                            [(polygon 180 90, -180 90, -180 -90, 180 -90, 180 90)]))

    (testing "geodetic line"
      (assert-invalid-spatial
        :geodetic
        [(l/ords->line-string :geodetic [0,0,1,1,2,2,1,1])]
        ["SpatialExtent" "HorizontalSpatialDomain" "Geometry" "Lines" 0]
        ["Spatial validation error: The shape contained duplicate points. Points 2 [lon=1 lat=1] and 4 [lon=1 lat=1] were considered equivalent or very close."]))

    (testing "cartesian line"
      ;; Cartesian line validation isn't supported yet. See CMR-1172
      (assert-valid-spatial
        :cartesian
        [(l/ords->line-string :cartesian [180 0, -180 0])]))

    (testing "bounding box"
      (assert-invalid-spatial
        :geodetic
        [(m/mbr -180 45 180 46)]
        ["SpatialExtent" "HorizontalSpatialDomain" "Geometry" "BoundingRectangles" 0]
        ["Spatial validation error: The bounding rectangle north value [45] was less than the south value [46]"])))
  (side/eval-form `(icfg/set-return-umm-spec-validation-errors! false)))

(deftest umm-spec-validation-warnings
  ;; By default the config return-umm-spec-validation-errors is false, so warnings are returned with the collection. 
  (testing "Ingest and Ingest Validation with warning messages for all formats"
    (are3 [format collection warning-message]
          (do
            (let [response (d/ingest "PROV1" collection {:format format})]
              (is (#{200 201} (:status response)))
              (is (= warning-message (:warnings response))))
            (let [response (ingest/validate-concept (dc/collection-concept collection format))]
              (is (= 200 (:status response)))
              (is (= warning-message (:warnings response)))))

          "ECHO10 Ingest and Ingest Validation"
          :echo10 (dc/collection {}) "After translating item to UMM-C the metadata had the following issue: object has missing required properties ([\"DataCenters\",\"Platforms\",\"ProcessingLevel\",\"RelatedUrls\",\"ScienceKeywords\",\"SpatialExtent\",\"TemporalExtents\"])"

          "DIF10 Ingest and Ingest Validation"
          :dif10 (dc/collection-dif10 {}) "After translating item to UMM-C the metadata had the following issue: object has missing required properties ([\"ProcessingLevel\"])"

          "DIF9 Ingest and Ingest Validation"
          :dif (dc/collection-dif {}) "After translating item to UMM-C the metadata had the following issue: object has missing required properties ([\"Platforms\",\"ProcessingLevel\",\"RelatedUrls\",\"SpatialExtent\",\"TemporalExtents\"])"

          "ISO19115 Ingest and Ingest Validation"
          :iso19115 (dc/collection {}) "After translating item to UMM-C the metadata had the following issue: object has missing required properties ([\"DataCenters\",\"Platforms\",\"ProcessingLevel\",\"RelatedUrls\",\"ScienceKeywords\",\"SpatialExtent\",\"TemporalExtents\"])"

          "ISO SMAP Ingest and Ingest Validation"
          :iso-smap (dc/collection-smap {}) "After translating item to UMM-C the metadata had the following issue: object has missing required properties ([\"DataCenters\",\"Platforms\",\"ProcessingLevel\",\"RelatedUrls\",\"ScienceKeywords\",\"SpatialExtent\",\"TemporalExtents\"])"

          "DIF9 with no version - has warnings, but passes ingest"
          :dif (assoc-in (dc/collection-dif {}) [:product :version-id] nil)
          "After translating item to UMM-C the metadata had the following issue: object has missing required properties ([\"Platforms\",\"ProcessingLevel\",\"RelatedUrls\",\"SpatialExtent\",\"TemporalExtents\",\"Version\"])")))
