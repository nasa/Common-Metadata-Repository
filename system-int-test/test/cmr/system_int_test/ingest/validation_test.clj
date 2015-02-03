(ns ^{:doc "CMR Ingest validation integration tests"}
  cmr.system-int-test.ingest.validation-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.umm.spatial :as umm-s]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.mbr :as m]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(comment
  (do
    (ingest/reset)
    (ingest/create-provider "provguid1" "PROV1"))

  )

(deftest validation-endpoint-test
  (testing "colleciton validation"
    (testing "successful validation of collection"
      (let [concept (dc/collection-for-ingest {})
            {:keys [status errors]} (ingest/validate-concept concept)]
        (is (= [200 nil] [status errors]))))
    (testing "invalid collection xml fails validation with appropriate message"
      (let [concept (dc/collection-for-ingest {})
            {:keys [status errors]}
            (ingest/validate-concept (assoc concept :metadata "<Collection>invalid xml</Collection>"))]
        (is (= [400 ["Line 1 - cvc-complex-type.2.3: Element 'Collection' cannot have character [children], because the type's content type is element-only."
                     "Line 1 - cvc-complex-type.2.4.b: The content of element 'Collection' is not complete. One of '{ShortName}' is expected."]]
               [status errors])))))
  (testing "granule validations"
    (testing "successful validation of granule"
      (let [collection (d/ingest "PROV1" (dc/collection {}))
            concept (dg/umm-granule->granule-concept (dg/granule collection))
            {:keys [status errors]}
            (ingest/validate-concept concept)]
        (is (= [200 nil] [status errors]))))
    (testing "invalid granule xml fails validation with appropriate message"
      (let [collection (d/ingest "PROV1" (dc/collection {}))
            concept (dg/umm-granule->granule-concept (dg/granule collection))
            {:keys [status errors]} (ingest/validate-concept(assoc concept :metadata "<Granule>invalid xml</Granule>"))]
        (is (= [400 ["Line 1 - cvc-complex-type.2.3: Element 'Granule' cannot have character [children], because the type's content type is element-only."
                     "Line 1 - cvc-complex-type.2.4.b: The content of element 'Granule' is not complete. One of '{GranuleUR}' is expected."]]
               [status errors]))))))


(defn polygon
  "Creates a single ring polygon with the given ordinates. Points must be in counter clockwise order."
  [& ords]
  (poly/polygon [(apply umm-s/ords->ring ords)]))


(defn ingest-spatial-coll
  [coord-sys & shapes]
  (let [shapes (map (partial umm-s/set-coordinate-system coord-sys) shapes)]
    (d/ingest "PROV1"
              (dc/collection
                {:spatial-coverage (dc/spatial {:gsr coord-sys
                                                :sr coord-sys
                                                :geometries shapes})}))))

(defn assert-invalid
  ([coll-attributes errors]
   (assert-invalid coll-attributes errors :echo10))
  ([coll-attributes errors metadata-format]
   (let [response (d/ingest "PROV1" (dc/collection coll-attributes) metadata-format)]
     (is (= {:status 400
             :errors errors}
            (select-keys response [:status :errors]))))))

(defn assert-valid
  [coll-attributes]
  (let [response (d/ingest "PROV1" (dc/collection coll-attributes))]
    (is (= {:status 200} (select-keys response [:status :errors])))))

(defn assert-invalid-spatial
  ([coord-sys shapes errors]
   (assert-invalid-spatial coord-sys shapes errors :echo10))
  ([coord-sys shapes errors metadata-format]
   (let [shapes (map (partial umm-s/set-coordinate-system coord-sys) shapes)]
     (assert-invalid {:spatial-coverage (dc/spatial {:gsr coord-sys
                                                     :sr coord-sys
                                                     :geometries shapes})}
                     errors
                     metadata-format))))

(defn assert-valid-spatial
  [coord-sys shapes]
  (let [shapes (map (partial umm-s/set-coordinate-system coord-sys) shapes)]
    (assert-valid {:spatial-coverage (dc/spatial {:gsr coord-sys
                                                  :sr coord-sys
                                                  :geometries shapes})})))

;; This tests that UMM type validations are applied during collection ingest.
;; Thorough tests of UMM validations should go in cmr.umm.test.validation.core and related
;; namespaces.
(deftest collection-umm-validation-test
  (testing "Product specific attribute validation"
    (assert-invalid
      {:product-specific-attributes
       [(dc/psa "bool" :boolean true)
        (dc/psa "bool" :boolean true)]}
      ["Product Specific Attributes must be unique. This contains duplicates named [bool]."]))
  (testing "Spatial validation"
    (testing "geodetic polygon"
      (assert-invalid-spatial
        :geodetic
        [(polygon 180 90, -180 90, -180 -90, 180 -90, 180 90)]
        ["Spatial validation error: The shape contained duplicate points. Points 3 [lon=-180 lat=-90] and 4 [lon=180 lat=-90] were considered equivalent or very close."
         "Spatial validation error: The shape contained duplicate points. Points 1 [lon=180 lat=90] and 2 [lon=-180 lat=90] were considered equivalent or very close."
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
        [(l/ords->line-string :geodetic 0,0,1,1,2,2,1,1)]
        ["Spatial validation error: The shape contained duplicate points. Points 2 [lon=1 lat=1] and 4 [lon=1 lat=1] were considered equivalent or very close."]))

    (testing "cartesian line"
      ;; Cartesian line validation isn't supported yet. See CMR-1172
      (assert-valid-spatial
        :cartesian
        [(l/ords->line-string :cartesian 180 0, -180 0)]))

    (testing "bounding box"
      (assert-invalid-spatial
        :geodetic
        [(m/mbr -180 45 180 46)]
        ["Spatial validation error: The bounding rectangle north value [45] was less than the south value [46]"]))

    (testing "point"
      (assert-invalid-spatial
        :geodetic
        [(p/point 185, 90)]
        ;; Invalid points are caught in the schema validation
        ["Line 1 - cvc-maxInclusive-valid: Value '185' is not facet-valid with respect to maxInclusive '180.0' for type 'Longitude'."
         "Line 1 - cvc-type.3.1.3: The value '185' of element 'PointLongitude' is not valid."]))

    ;; TODO Add tests for validation of points with all formats.
    ;; Add tests for validation of another spatial type with all formats.
    ;; Points and another shape should both be done since schema validation catches it with ECHO10.


    ))