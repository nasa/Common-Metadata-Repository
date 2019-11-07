(ns cmr.system-int-test.ingest.granule-validation-test
  "CMR Ingest granule validation integration tests"
  (:require
    [clj-time.core :as t]
    [clj-time.format :as tf ]
    [clojure.java.io :as io]
    [clojure.java.shell :as shell]
    [clojure.test :refer :all]
    [cmr.common.time-keeper :as tk]
    [cmr.common.util :refer [are2]]
    [cmr.ingest.services.messages :as msg]
    [cmr.spatial.line-string :as l]
    [cmr.spatial.mbr :as m]
    [cmr.spatial.point :as p]
    [cmr.spatial.polygon :as poly]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.url-helper :as url]
    [cmr.umm.umm-granule :as umm-g]
    [cmr.umm.umm-spatial :as umm-s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(comment
  (do
    (dev-sys-util/reset)
    (ingest/create-provider {:provider-guid "provguid1" :provider-id "PROV1"}))

  (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                      {:AdditionalAttributes
                       [(data-umm-cmn/additional-attribute {:Name "bool" :DataType :boolean :value true})
                        (data-umm-cmn/additional-attribute {:Name "bool" :DataType :boolean :Value true})]})))



(defn assert-validation-errors
  "Asserts that when the granule and optional collection concept are validated the expected errors
  are returned. The collection concept can be passed as a third argument and it will be sent along
  with the granule instead of using a previously ingested collection."
  [expected-status-code expected-errors & gran-and-optional-coll-concept]
  (let [response (apply ingest/validate-granule gran-and-optional-coll-concept)]
    (is (= {:status expected-status-code :errors expected-errors}
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
        expected-errors ["Exception while parsing invalid XML: Line 1 - cvc-complex-type.2.3: Element 'Granule' cannot have character [children], because the type's content type is element-only."
                         "Exception while parsing invalid XML: Line 1 - cvc-complex-type.2.4.b: The content of element 'Granule' is not complete. One of '{GranuleUR}' is expected."]]

    (testing "granule with end date slightly in the future and collection with
             EndsAtPresentFlag=true and end date in the far future (see CMR-1351)"
             (let [collection (data-umm-c/collection {:EntryTitle "correct"
                                              :ShortName "S1"
                                              :Version "V1"
                                              :TemporalExtents [(data-umm-cmn/temporal-extent
                                                          {:beginning-date-time "2009-03-08T00:00:00Z"
                                                           :ending-date-time "2121-11-04T00:00:00Z"
                                                           :ends-at-present? true})]})
                   coll-concept (d/umm-c-collection->concept collection)
                   future-date-time (t/plus (tk/now) (t/hours 1))
                   time-formatter (tf/formatters :date-time-no-ms)
                   future-date-time-str (tf/unparse time-formatter future-date-time)
                   granule (assoc (dg/granule-with-umm-spec-collection collection (:concept-id collection))
                                  :temporal (dg/temporal {:beginning-date-time "2010-11-04T00:00:00Z"
                                                          :ending-date-time future-date-time-str}))]
               (assert-validation-success
                 (d/item->concept granule)
                 coll-concept)))

    (testing "with collection as additional parameter"
      (let [collection (data-umm-c/collection {})
            coll-concept (d/umm-c-collection->concept collection :echo10)]
        (testing "success"
          (let [concept (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection)))]
            (assert-validation-success concept coll-concept)))
        (testing "collection in different format than granule"
          (let [concept (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection)))]
            (assert-validation-success concept (d/umm-c-collection->concept collection :iso19115))
            (assert-validation-success concept (d/umm-c-collection->concept collection :dif))))

        (testing "invalid collection xml"
          (assert-validation-errors
            400
            [(msg/invalid-parent-collection-for-validation
               "Exception while parsing invalid XML: Line 1 - cvc-elt.1: Cannot find the declaration of element 'Granule'.")]
            (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection)))
            (assoc coll-concept :metadata invalid-granule-xml)))

        (testing "invalid granule xml"
          (assert-validation-errors
            400
            expected-errors
            (-> (dg/granule-with-umm-spec-collection collection (:concept-id collection))
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
            415
            ["Invalid content-type: application/xml. Valid content-types: application/echo10+xml, application/vnd.nasa.cmr.umm+json, application/iso:smap+xml."]
            (-> (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection)))
                (assoc :format "application/xml"))
            coll-concept))

        (testing "invalid collection metadata format"
          (assert-validation-errors
            415
            [(msg/invalid-parent-collection-for-validation
               (str "Invalid content-type: application/xml. Valid content-types: "
                    "application/echo10+xml, application/vnd.nasa.cmr.umm+json, application/iso:smap+xml, application/iso19115+xml, application/dif+xml, application/dif10+xml."))]
            (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection)))
            (assoc coll-concept :format "application/xml")))

        (testing "granule collection ref does not match collection"
          (testing "entry-title"
            (let [collection (data-umm-c/collection {:EntryTitle "correct"})
                  coll-concept (d/umm-c-collection->concept collection :echo10)
                  granule (assoc (dg/granule-with-umm-spec-collection collection (:concept-id collection))
                                 :collection-ref
                                 (umm-g/map->CollectionRef {:entry-title "wrong"}))]
              (assert-validation-errors
                422
                [{:path ["CollectionRef"],
                  :errors ["Collection Reference Entry Title [wrong] does not match the Entry Title of the parent collection [correct]"]}]
                (d/item->concept granule)
                coll-concept)))

          (testing "entry-id"
            (let [collection (data-umm-c/collection {:ShortName "correct"})
                  coll-concept (d/umm-c-collection->concept collection :dif)
                  granule (assoc (dg/granule-with-umm-spec-collection collection (:concept-id collection))
                                 :collection-ref
                                 (umm-g/map->CollectionRef {:entry-id "wrong"}))]
              (assert-validation-errors
                422
                [{:path ["CollectionRef"],
                  :errors ["Collection Reference Entry Id [wrong] does not match the Entry Id of the parent collection [correct_V1]"]}]
                (d/item->concept granule)
                coll-concept)))

          (let [collection (data-umm-c/collection {:ShortName "S1" :Version "V1"})
                coll-concept (d/umm-c-collection->concept collection :echo10)]
            (testing "shortname"
              (assert-validation-errors
                422
                [{:path ["CollectionRef"],
                  :errors ["Collection Reference Short Name [S2] does not match the Short Name of the parent collection [S1]"]}]
                (d/item->concept (assoc (dg/granule-with-umm-spec-collection collection (:concept-id collection))
                                        :collection-ref
                                        (umm-g/map->CollectionRef {:short-name "S2"
                                                                   :version-id "V1"})))
                coll-concept))
            (testing "version id"
              (assert-validation-errors
                422
                [{:path ["CollectionRef"],
                  :errors ["Collection Reference Version Id [V2] does not match the Version Id of the parent collection [V1]"]}]
                (d/item->concept (assoc (dg/granule-with-umm-spec-collection collection (:concept-id collection))
                                        :collection-ref
                                        (umm-g/map->CollectionRef {:short-name "S1"
                                                                   :version-id "V2"})))
                coll-concept)))

          (testing "granule collection-refs with all entry-title, short-name and version-id"
            (let [collection-attrs {:EntryTitle "correct"
                                    :ShortName "S1"
                                    :Version "V1"}
                  collection-ref-attrs {:entry-title "correct"
                                        :short-name "S1"
                                        :version-id "V1"}
                  collection (data-umm-c/collection collection-attrs)
                  coll-concept (d/umm-c-collection->concept collection :iso-smap)]
              (testing "valid SMAP ISO granule"
                (let [granule (assoc (dg/granule-with-umm-spec-collection collection (:concept-id collection))
                                     :collection-ref
                                     (umm-g/map->CollectionRef collection-ref-attrs))]
                  (assert-validation-success (d/item->concept granule :iso-smap)
                                             coll-concept)))
              (testing "invalid SMAP ISO granule"
                (are [attrs errors]
                     (let [granule (assoc (dg/granule-with-umm-spec-collection collection (:concept-id collection))
                                          :collection-ref
                                          (umm-g/map->CollectionRef (merge collection-ref-attrs attrs)))]
                       (assert-validation-errors
                         422
                         [{:path ["CollectionRef"] :errors errors}]
                         (d/item->concept granule :iso-smap)
                         coll-concept))

                     {:entry-title "wrong"}
                     ["Collection Reference Entry Title [wrong] does not match the Entry Title of the parent collection [correct]"]

                     {:short-name "S2"}
                     ["Collection Reference Short Name [S2] does not match the Short Name of the parent collection [S1]"]

                     {:version-id "V2"}
                     ["Collection Reference Version Id [V2] does not match the Version Id of the parent collection [V1]"]

                     {:entry-title "wrong" :version-id "V2"}
                     ["Collection Reference Entry Title [wrong] does not match the Entry Title of the parent collection [correct]"
                      "Collection Reference Version Id [V2] does not match the Version Id of the parent collection [V1]"]))))


          (testing "granule collection-refs missing field"
            (let [collection (data-umm-c/collection {:EntryTitle "correct"
                                             :ShortName "S1"
                                             :Version "V1"})
                  coll-concept (d/umm-c-collection->concept collection)
                  granule (assoc (dg/granule-with-umm-spec-collection collection (:concept-id collection))
                                 :collection-ref
                                 (umm-g/map->CollectionRef {:entry-title nil
                                                            :short-name nil
                                                            :version-id "V1"}))]
              (assert-validation-errors
                422
                [{:path ["CollectionRef"],
                  :errors ["Collection Reference should have at least Entry Id, Entry Title or Short Name and Version Id."]}]
                (d/item->concept granule :iso-smap)
                coll-concept))))))

    (testing "with ingested collection"
      (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))]
        (testing "successful validation of granule"
          (let [granule-concept (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection)))]
            (assert-validation-success granule-concept)

            (testing "with ingested parent collection"
              (assert-validation-success granule-concept (d/umm-c-collection->concept collection)))))
        (testing "invalid granule xml"
          (assert-validation-errors
            400
            expected-errors
            (-> (dg/granule-with-umm-spec-collection collection (:concept-id collection))
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
     (let [coll (data-umm-c/collection (d/unique-num) coll-attributes)
           coll-concept (d/umm-c-collection->concept coll metadata-format)
           granule (dg/granule-with-umm-spec-collection coll (:concept-id coll) gran-attributes)
           gran-concept (d/item->concept granule)
           response (ingest/validate-granule gran-concept coll-concept)]
       (is (= {:status 422
               :errors [{:path field-path
                         :errors errors}]}
              (select-keys response [:status :errors])))))

   (testing "through ingest API"
     (let [coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection (d/unique-num) coll-attributes) {:format metadata-format})
           response (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) gran-attributes)
                                      {:format metadata-format :allow-failure? true})]
       (is (= {:status 422
               :errors [{:path field-path
                         :errors errors}]}
              (select-keys response [:status :errors])))))))

(defn assert-valid
  [coll-attributes gran-attributes]
  (testing "through validation api"
    (let [coll (data-umm-c/collection (d/unique-num) coll-attributes)
          coll-concept (d/umm-c-collection->concept coll)
          granule (dg/granule-with-umm-spec-collection coll (:concept-id coll) gran-attributes)
          gran-concept (d/item->concept granule)
          response (ingest/validate-granule gran-concept coll-concept)]
      (is (= {:status 200}
             (select-keys response [:status :errors])))))

  (testing "through ingest API"
    (let [provider-id (get gran-attributes :provider-id "PROV1")
          coll (d/ingest-umm-spec-collection provider-id (data-umm-c/collection coll-attributes))
          response (d/ingest provider-id (dg/granule-with-umm-spec-collection coll (:concept-id coll) gran-attributes))]
      (is (#{{:status 201} {:status 200}} (select-keys response [:status :errors]))))))

(defn assert-invalid-spatial
  ([coord-sys umm-c-coord-sys shapes errors]
   (assert-invalid-spatial coord-sys umm-c-coord-sys shapes errors :echo10))
  ([coord-sys umm-c-coord-sys shapes errors metadata-format]
   (let [shapes (map (partial umm-s/set-coordinate-system coord-sys) shapes)]
     (assert-invalid {:SpatialExtent (data-umm-c/spatial {:gsr umm-c-coord-sys})}
                     {:spatial-coverage (apply dg/spatial shapes)}
                     ["SpatialCoverage" "Geometries" 0]
                     errors
                     metadata-format))))

(defn assert-valid-spatial
  [coord-sys umm-c-coord-sys shapes]
  (let [shapes (map (partial umm-s/set-coordinate-system coord-sys) shapes)]
    (assert-valid {:SpatialExtent (data-umm-c/spatial {:gsr umm-c-coord-sys})}
                  {:spatial-coverage (apply dg/spatial shapes)})))

(defn assert-conflict
  [gran-attributes errors]
  (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection))
        response (d/ingest "PROV1" (dg/granule-with-umm-spec-collection collection (:concept-id collection) gran-attributes) {:allow-failure? true})]
    (is (= {:status 409
            :errors errors}
           (select-keys response [:status :errors])))))

(deftest granule-geodetic-spatial-validation-test
  (testing "geodetic polygon"
    ;; Invalid points are caught in the schema validation
    (assert-invalid-spatial
     :geodetic
     "GEODETIC"
     [(polygon 180 90, -180 90, -180 -90, 180 -90, 180 90)]
     ["Spatial validation error: The shape contained duplicate points. Points 1 [lon=180 lat=90] and 2 [lon=-180 lat=90] were considered equivalent or very close."
      "Spatial validation error: The shape contained duplicate points. Points 3 [lon=-180 lat=-90] and 4 [lon=180 lat=-90] were considered equivalent or very close."
      "Spatial validation error: The shape contained consecutive antipodal points. Points 2 [lon=-180 lat=90] and 3 [lon=-180 lat=-90] are antipodal."
      "Spatial validation error: The shape contained consecutive antipodal points. Points 4 [lon=180 lat=-90] and 5 [lon=180 lat=90] are antipodal."]))

  (testing "geodetic polygon with holes"
    (assert-valid-spatial
     :geodetic
     "GEODETIC"
     [(poly/polygon [(umm-s/ords->ring 1 1, -1 1, -1 -1, 1 -1, 1 1)
                     (umm-s/ords->ring 0,0, 0.00004,0, 0.00006,0.00005, 0.00002,0.00005, 0,0)])]))

  (testing "geodetic line"
    (assert-invalid-spatial
     :geodetic
     "GEODETIC"
     [(l/ords->line-string :geodetic [0,0,1,1,2,2,1,1])]
     ["Spatial validation error: The shape contained duplicate points. Points 2 [lon=1 lat=1] and 4 [lon=1 lat=1] were considered equivalent or very close."]))

  (testing "bounding box"
    (assert-invalid-spatial
     :geodetic
     "GEODETIC"
     [(m/mbr -180 45 180 46)]
     ["Spatial validation error: The bounding rectangle north value [45] was less than the south value [46]"])))

(deftest granule-cartesian-spatial-validation-test
  (testing "cartesian polygon"
    ;; The same shape from geodetic is valid as a cartesian.
    (assert-valid-spatial :cartesian "CARTESIAN"
                          [(polygon 180 90, -180 90, -180 -90, 180 -90, 180 90)]))

  (testing "cartesian line"
    (assert-valid-spatial
     :cartesian
     "CARTESIAN"
     [(l/ords->line-string :cartesian [180 0, -180 0])])))

(deftest inappropriate-spatial-coverage-test
  (testing "granule with spatial but parent collection does not"
    (are2 [coll-gsr]
          (assert-invalid
            {:SpatialExtent (when coll-gsr (data-umm-c/spatial {:gsr coll-gsr}))}
            {:spatial-coverage
             (dg/spatial
               (umm-s/set-coordinate-system
                 :geodetic
                 (poly/polygon
                   [(umm-s/ords->ring 1 1, -1 1, -1 -1, 1 -1, 1 1)
                    (umm-s/ords->ring 0,0, 0.00004,0, 0.00006,0.00005, 0.00002,0.00005, 0,0)])))}
            ["SpatialCoverage" "Geometries"]
            ["[Geometries] cannot be set when the parent collection's GranuleSpatialRepresentation is NO_SPATIAL"])

          "parent collection has no spatial info"
          nil

          "parent collection GranuleSpatialRepresentation is NO_SPATIAL"
          "NO_SPATIAL")))

(deftest missing-spatial-coverage-test
  (let [collection-attrs {:SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})}
        granule-attrs {:format "application/echo10+xml; charset=utf-8"}]
    (assert-invalid collection-attrs
                    granule-attrs
                    ["SpatialCoverage" "Geometries"]
                    ["[Geometries] must be provided when the parent collection's GranuleSpatialRepresentation is GEODETIC"])))

(deftest duplicate-granule-ur-test
  (testing "same granule-ur and native-id across providers is valid"
    (assert-valid
     {}
     {:granule-ur "UR-1" :concept-id "G1-PROV1" :native-id "Native1"})
    (assert-valid
     {}
     {:granule-ur "UR-1" :concept-id "G1-PROV2" :native-id "Native1" :provider-id "PROV2"}))
  (testing "updating the same granule is valid"
    (assert-valid
     {}
     {:granule-ur "UR-1" :concept-id "G1-PROV1" :native-id "Native1"}))
  (testing "granule-ur must be unique for a provider"
    (assert-conflict
     {:granule-ur "UR-1" :concept-id "G2-PROV1" :native-id "Native2"}
     ["The Granule Ur [UR-1] must be unique. The following concepts with the same granule ur were found: [G1-PROV1]."]))
  ;; Turn off enforcement of duplicate granule UR constraint
  (dev-sys-util/eval-in-dev-sys
   `(cmr.metadata-db.services.concept-constraints/set-enforce-granule-ur-constraint! false))
  (testing "same granule-ur and native-id across providers is valid"
    (assert-valid
     {}
     {:granule-ur "UR-1" :concept-id "G2-PROV1" :native-id "Native2"}))
  ;; Turn it back on after the test
  (dev-sys-util/eval-in-dev-sys
   `(cmr.metadata-db.services.concept-constraints/set-enforce-granule-ur-constraint! true)))

(def ^:private coll-file
  "temp file for collection metadata in multipart request"
  "cmr_5260_collection.xml")

(def ^:private granule-file
  "temp file for granule metadata in multipart request"
  "cmr_5260_granule.json")

(defn- validate-granule-multipart
  "Returns the granule validataion result for the given collection and granule
  via multipart granule validation request."
  [granule-concept coll-concept]
  ;; clj-http does not work with content-type that contains semicolons
  ;; (e.g. application/vnd.nasa.cmr.umm+json; version=1.4)
  ;; So we execute curl command directly from shell for UMM-G granule validation requests
  (try
    (spit coll-file (:metadata coll-concept))
    (spit granule-file (:metadata granule-concept))
    (let [{:keys [provider-id native-id]} granule-concept
          validate-url (url/validate-url provider-id :granule native-id)
          coll-form (format "collection=<%s;type=%s" coll-file (:format coll-concept))
          granule-form (format "granule=<%s;type=%s" granule-file (:format granule-concept))
          {:keys [out]} (shell/sh "curl" "-i" "-F" granule-form "-F" coll-form validate-url)]
      out)
    (finally
      (io/delete-file coll-file)
      (io/delete-file granule-file))))

(defn- validate-granule-multipart-ok?
  "Validate the granule multipart request returns 200 OK"
  [granule-concept coll-concept]
  (is (re-find
       #"200 OK"
       (validate-granule-multipart granule-concept coll-concept))))

(defn- validate-granule-multipart-failed?
  "Validate the granule multipart request returns the expected status code and error message."
  [granule-concept coll-concept status-code error-msg]
  (is (re-find
       (re-pattern (str "(?s)" status-code ".*" error-msg))
       (validate-granule-multipart granule-concept coll-concept))))

(deftest validation-umm-g-granule-test
  (let [collection-attrs {:EntryTitle "correct"
                          :ShortName "S1"
                          :Version "V1"}
        collection-ref-attrs {:entry-title "correct"
                              :short-name "S1"
                              :version-id "V1"}
        collection (data-umm-c/collection collection-attrs)
        coll-concept (d/umm-c-collection->concept collection :iso-smap)]
    (testing "valid UMM-G granule"
      (let [granule (assoc (dg/granule-with-umm-spec-collection collection nil)
                           :collection-ref
                           (umm-g/map->CollectionRef collection-ref-attrs))
            granule-concept (d/item->concept granule :umm-json)]
        (validate-granule-multipart-ok? granule-concept coll-concept)))

    (testing "invalid UMM-G granule"
      (let [granule (assoc (dg/granule-with-umm-spec-collection collection nil)
                           :collection-ref
                           (umm-g/map->CollectionRef
                            (merge collection-ref-attrs {:entry-title "wrong"})))
            granule-concept (d/item->concept granule :umm-json)
            expected-err-msg (str "Collection Reference Entry Title \\[wrong\\] does not match "
                                  "the Entry Title of the parent collection \\[correct\\]")]
        (validate-granule-multipart-failed?
         granule-concept coll-concept 422 expected-err-msg)))))
