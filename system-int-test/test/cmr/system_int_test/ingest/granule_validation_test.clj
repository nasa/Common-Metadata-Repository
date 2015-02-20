(ns ^{:doc "CMR Ingest granule validation integration tests"}
  cmr.system-int-test.ingest.granule-validation-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.umm.spatial :as umm-s]
            [cmr.umm.granule :as umm-g]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.mbr :as m]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.ingest.services.messages :as msg]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(comment
  (do
    (ingest/reset)
    (ingest/create-provider "provguid1" "PROV1"))

  (d/ingest "PROV1" (dc/collection {:product-specific-attributes
                                    [(dc/psa "bool" :boolean true)
                                     (dc/psa "bool" :boolean true)]}) :echo10)

  )

(defn assert-validation-errors
  "Asserts that when the granule and optional collection concept are validated the expected errors
  are returned. The collection concept can be passed as a third argument and it will be sent along
  with the granule instead of using a previously ingested collection."
  [expected-errors & gran-and-optional-coll-concept ]
  (let [response (apply ingest/validate-granule gran-and-optional-coll-concept)]
    (is (= {:status 400 :errors expected-errors}
           (select-keys response [:status :errors])))))

(defn assert-validation-success
  "Asserts that when the granule and optional collection concept are valid. The collection concept
  can be passed as a third argument and it will be sent along with the granule instead of using a
  previously ingested collection."
  [& gran-and-optional-coll-concept]
  (let [response (apply ingest/validate-granule gran-and-optional-coll-concept)]
    (is (= {:status 200} (select-keys response [:status :errors])))))



(deftest validation-endpoint-test
  (let [invalid-granule-xml "<Granule>invalid xml</Granule>"
        expected-errors ["Line 1 - cvc-complex-type.2.3: Element 'Granule' cannot have character [children], because the type's content type is element-only."
                         "Line 1 - cvc-complex-type.2.4.b: The content of element 'Granule' is not complete. One of '{GranuleUR}' is expected."]]

    (testing "with collection as additional parameter"
      (let [collection (dc/collection {})
            coll-concept (d/item->concept collection :echo10)]
        (testing "success"
          (let [concept (d/item->concept (dg/granule collection))]
            (assert-validation-success concept coll-concept)))
        (testing "collection in different format than granule"
          (let [concept (d/item->concept (dg/granule collection))
                iso-coll-concept (d/item->concept collection :iso19115)]
            (assert-validation-success concept iso-coll-concept)))

        (testing "invalid collection xml"
          (assert-validation-errors
            [(msg/invalid-parent-collection-for-validation
               "Line 1 - cvc-elt.1: Cannot find the declaration of element 'Granule'.")]
            (d/item->concept (dg/granule collection))
            (assoc coll-concept :metadata invalid-granule-xml)))

        (testing "invalid granule xml"
          (assert-validation-errors
            expected-errors
            (-> collection
                dg/granule
                d/item->concept
                (assoc :metadata invalid-granule-xml))
            coll-concept))

        (testing "invalid multipart params"
          (let [response (ingest/multipart-param-request
                           (url/validate-url "PROV1" :granule "native-id")
                           [{:name "foo"
                             :content "foo content"
                             :content-type "application/xml"}])]
            (is (= {:status 400
                    :errors [(msg/invalid-multipart-params #{"granule" "collection"} ["foo"])]}
                   (select-keys response [:status :errors])))))

        (testing "invalid granule metadata format"
          (assert-validation-errors
            ["Invalid content-type: application/xml. Valid content-types: application/echo10+xml, application/iso:smap+xml."]
            (-> (d/item->concept (dg/granule collection))
                (assoc :format "application/xml"))
            coll-concept))

        (testing "invalid collection metadata format"
          (assert-validation-errors
            [(msg/invalid-parent-collection-for-validation
               (str "Invalid content-type: application/xml. Valid content-types: "
                    "application/echo10+xml, application/iso:smap+xml, application/iso19115+xml, application/dif+xml."))]
            (d/item->concept (dg/granule collection))
            (assoc coll-concept :format "application/xml")))

        (testing "granule collection ref does not match collection"
          (testing "entry-title"
            (let [collection (dc/collection {:entry-title "correct"})
                  coll-concept (d/item->concept collection :echo10)
                  granule (assoc (dg/granule collection)
                                 :collection-ref
                                 (umm-g/map->CollectionRef {:entry-title "wrong"}))]
              (assert-validation-errors
                [{:path ["CollectionRef"],
                  :errors ["Collection Ref Entry Title [wrong] does not match the entry title of the parent collection [correct]"]}]
                (d/item->concept granule)
                coll-concept)))

          (let [collection (dc/collection {:short-name "S1" :version-id "V1"})
                coll-concept (d/item->concept collection :echo10)]
            (testing "shortname"
              (assert-validation-errors
                [{:path ["CollectionRef"],
                  :errors ["Collection Ref Short Name [S1] and Version ID [V2] do not match the Short Name [S1] and Version ID [V1] of the parent collection."]}]
                (d/item->concept (assoc (dg/granule collection)
                                        :collection-ref
                                        (umm-g/map->CollectionRef {:short-name "S1"
                                                                   :version-id "V2"})))
                coll-concept))
            (testing "version id"
              (assert-validation-errors
                [{:path ["CollectionRef"],
                  :errors ["Collection Ref Short Name [S2] and Version ID [V1] do not match the Short Name [S1] and Version ID [V1] of the parent collection."]}]
                (d/item->concept (assoc (dg/granule collection)
                                        :collection-ref
                                        (umm-g/map->CollectionRef {:short-name "S2"
                                                                   :version-id "V1"})))
                coll-concept))))))

    (testing "with ingested collection"
      (let [collection (d/ingest "PROV1" (dc/collection {}))]
        (testing "successful validation of granule"
          (assert-validation-success (d/item->concept (dg/granule collection))))
        (testing "invalid granule xml"
          (assert-validation-errors
            expected-errors
            (-> collection
                dg/granule
                d/item->concept
                (assoc :metadata invalid-granule-xml))))))))

(defn polygon
  "Creates a single ring polygon with the given ordinates. Points must be in counter clockwise order."
  [& ords]
  (poly/polygon [(apply umm-s/ords->ring ords)]))

(defn assert-invalid
  ([coll-attributes gran-attributes field-path errors]
   (assert-invalid coll-attributes gran-attributes field-path errors :echo10))
  ([coll-attributes gran-attributes field-path errors metadata-format]
   (testing "through validation api"
     (let [coll (dc/collection coll-attributes)
           coll-concept (d/item->concept coll metadata-format)
           granule (dg/granule coll gran-attributes)
           gran-concept (d/item->concept granule)
           response (ingest/validate-granule gran-concept coll-concept)]
       (is (= {:status 400
               :errors [{:path field-path
                         :errors errors}]}
              (select-keys response [:status :errors])))))

   (testing "through ingest API"
     (let [coll (d/ingest "PROV1" (dc/collection coll-attributes) metadata-format)
           response (d/ingest "PROV1" (dg/granule coll gran-attributes) metadata-format)]
       (is (= {:status 400
               :errors [{:path field-path
                         :errors errors}]}
              (select-keys response [:status :errors])))))))

(defn assert-valid
  [coll-attributes gran-attributes]
  (testing "through validation api"
    (let [coll (dc/collection coll-attributes)
          coll-concept (d/item->concept coll)
          granule (dg/granule coll gran-attributes)
          gran-concept (d/item->concept granule)
          response (ingest/validate-granule gran-concept coll-concept)]
      (is (= {:status 200}
             (select-keys response [:status :errors])))))

  (testing "through ingest API"
    (let [coll (d/ingest "PROV1" (dc/collection coll-attributes))
          response (d/ingest "PROV1" (dg/granule coll gran-attributes))]
      (is (= {:status 200} (select-keys response [:status :errors]))))))

(defn assert-invalid-spatial
  ([coord-sys shapes errors]
   (assert-invalid-spatial coord-sys shapes errors :echo10))
  ([coord-sys shapes errors metadata-format]
   (let [shapes (map (partial umm-s/set-coordinate-system coord-sys) shapes)]
     (assert-invalid {:spatial-coverage (dc/spatial {:gsr coord-sys})}
                     {:spatial-coverage (apply dg/spatial shapes)}
                     ["SpatialCoverage" "Geometries" 0]
                     errors
                     metadata-format))))

(defn assert-valid-spatial
  [coord-sys shapes]
  (let [shapes (map (partial umm-s/set-coordinate-system coord-sys) shapes)]
    (assert-valid {:spatial-coverage (dc/spatial {:gsr coord-sys})}
                  {:spatial-coverage (apply dg/spatial shapes)})))

;; This tests that UMM type validations are applied during collection ingest.
;; Thorough tests of UMM validations should go in cmr.umm.test.validation.core and related
;; namespaces.
(deftest granule-umm-validation-test
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
        ["Spatial validation error: The bounding rectangle north value [45] was less than the south value [46]"]))




    ))