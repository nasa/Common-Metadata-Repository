(ns cmr.system-int-test.ingest.collection-validation-test
  "CMR Ingest validation integration tests"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.umm.spatial :as umm-s]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.mbr :as m]
            [cmr.ingest.services.messages :as msg]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest validation-endpoint-test
  (testing "successful validation of collection"
    (let [concept (dc/collection-concept {})
          {:keys [status errors]} (ingest/validate-concept concept)]
      (is (= [200 nil] [status errors]))))
  (testing "invalid collection xml fails validation with appropriate message"
    (let [concept (dc/collection-concept {})
          {:keys [status errors]}
          (ingest/validate-concept (assoc concept
                                          :metadata "<Collection>invalid xml</Collection>"))]
      (is (= [400 ["Line 1 - cvc-complex-type.2.3: Element 'Collection' cannot have character [children], because the type's content type is element-only."
                   "Line 1 - cvc-complex-type.2.4.b: The content of element 'Collection' is not complete. One of '{ShortName}' is expected."]]
             [status errors])))))

(defn polygon
  "Creates a single ring polygon with the given ordinates. Points must be in counter clockwise order."
  [& ords]
  (poly/polygon [(apply umm-s/ords->ring ords)]))

(defn assert-invalid
  ([coll-attributes field-path errors]
   (assert-invalid coll-attributes field-path errors :echo10))
  ([coll-attributes field-path errors metadata-format]
   (let [response (d/ingest "PROV1" (dc/collection coll-attributes) metadata-format)]
     (is (= {:status 400
             :errors [{:path field-path
                       :errors errors}]}
            (select-keys response [:status :errors]))))))

(defn assert-valid
  [coll-attributes]
  (let [collection (assoc (dc/collection coll-attributes) :native-id (:native-id coll-attributes))
        provider-id (get coll-attributes :provider-id "PROV1")
        response (d/ingest provider-id collection)]
    (is (= {:status 200} (select-keys response [:status :errors])))))

(defn assert-invalid-spatial
  ([coord-sys shapes errors]
   (assert-invalid-spatial coord-sys shapes errors :echo10))
  ([coord-sys shapes errors metadata-format]
   (let [shapes (map (partial umm-s/set-coordinate-system coord-sys) shapes)]
     (assert-invalid {:spatial-coverage (dc/spatial {:gsr coord-sys
                                                     :sr coord-sys
                                                     :geometries shapes})}
                     ["SpatialCoverage" "Geometries" 0]
                     errors
                     metadata-format))))

(defn assert-valid-spatial
  [coord-sys shapes]
  (let [shapes (map (partial umm-s/set-coordinate-system coord-sys) shapes)]
    (assert-valid {:spatial-coverage (dc/spatial {:gsr coord-sys
                                                  :sr coord-sys
                                                  :geometries shapes})})))

(defn assert-conflict
  [coll-attributes errors]
  (let [collection (assoc (dc/collection coll-attributes) :native-id (:native-id coll-attributes))
        response (d/ingest "PROV1" collection)]
    (is (= {:status 409
            :errors errors}
           (select-keys response [:status :errors])))))

;; This tests that UMM type validations are applied during collection ingest.
;; Thorough tests of UMM validations should go in cmr.umm.test.validation.core and related
;; namespaces.
(deftest collection-umm-validation-test
  (testing "Product specific attribute validation"
    (assert-invalid
      {:product-specific-attributes
       [(dc/psa "bool" :boolean true)
        (dc/psa "bool" :boolean true)]}
      ["ProductSpecificAttributes"]
      ["Product Specific Attributes must be unique. This contains duplicates named [bool]."]))
  (testing "Nested Path Validation"
    (assert-invalid
      {:platforms [(dc/platform "P1" "none" nil (dc/instrument "I1") (dc/instrument "I1"))]}
      ["Platforms" 0 "Instruments"]
      ["Instruments must be unique. This contains duplicates named [I1]."]))
  (testing "Spatial validation"
    (testing "geodetic polygon"
      ;; Invalid points are caught in the schema validation
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
        ["Spatial validation error: The bounding rectangle north value [45] was less than the south value [46]"]))))

(deftest duplicate-entry-title-test
  (testing "same entry-title and native-id across providers is valid"
    (assert-valid
      {:entry-title "ET-1" :concept-id "C1-PROV1" :native-id "Native1"})
    (assert-valid
      {:entry-title "ET-1" :concept-id "C1-PROV2" :native-id "Native1" :provider-id "PROV2"}))
  (testing "entry-title must be unique for a provider"
    (assert-conflict
      {:entry-title "ET-1" :concept-id "C2-PROV1" :native-id "Native2"}
      ["The Entry Title [ET-1] must be unique. The following concepts with the same entry title were found: [C1-PROV1]."])))

(deftest header-validations
  (testing "ingesting a concept with the same concept-id and revision-id fails"
    (let [concept-id "C1-PROV1"
          existing-concept (dc/collection-concept {:revision-id 1 :concept-id concept-id})
          _ (ingest/ingest-concept existing-concept)
          response (ingest/ingest-concept existing-concept)]
      (is (= {:status 409
              :errors [(format "Expected revision-id of [2] got [1] for [%s]" concept-id)]}
             response))))
  (testing "attempting to ingest using an invalid revision id returns an error"
    (let [response (ingest/ingest-concept (dc/collection-concept {:concept-id "C2-PROV1"
                                                                  :revision-id "NaN"}))]
      (is (= {:status 400
              :errors [(msg/invalid-revision-id "NaN")]}
             response)))))

(comment
  (ingest/delete-provider "PROV1")
  ;; Attempt to create race conditions by ingesting the same concept-id simultaneously. We expect
  ;; some requests to succeed while others return a 409.
  ;; If the race condition is reproduced you will see a message like:
  ;; 409 returned, Errors: [Conflict with existing concept-id [C1-PROV1] and revision-id [23]]
  (do
    (ingest/create-provider "provguid1" "PROV1")
    (cmr.system-int-test.utils.echo-util/grant-all-ingest "PROV1")

    (doseq [_ (range 150)]
      (future (do (let [response (ingest/ingest-concept
                                   (dc/collection-concept {:concept-id "C1-PROV1"
                                                           :native-id "Same Native ID"}))]
                    (when (= 409 (:status response))
                      (println "409 returned, Errors:" (:errors response)))))))))
