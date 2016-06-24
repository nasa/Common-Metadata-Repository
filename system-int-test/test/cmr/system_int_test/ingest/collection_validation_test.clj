(ns cmr.system-int-test.ingest.collection-validation-test
  "CMR Ingest validation integration tests"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.umm.spatial :as umm-s]
            [cmr.common.util :refer [are2]]
            [cmr.common-app.test.side-api :as side]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.mbr :as m]
            [cmr.ingest.config :as icfg]
            [cmr.ingest.services.messages :as msg]
            [cmr.common.mime-types :as mime-types]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.search-util :as search]
            [clojure.java.io :as io]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn- iso-metadata-concept
  "Makes a bad ISO Metadata Request"
  [metadata]
  (let [format (mime-types/format->mime-type :iso19115)]
    (merge {:concept-type :collection
            :provider-id "PROV1"
            :native-id "foo"
            :metadata metadata
            :format format})))

(deftest spatial-with-no-representation
  ;; ISO19115 allows you to ingest metadata with no spatial coordinate reference but have spatial
  ;; points. We should reject it because UMM requires a spatial coordinate reference.
  (testing "A collection with spatial data but no representation should fail ingest validation"
    (let [bad-metadata (slurp
                        (io/resource
                         "iso-samples/iso-spatial-data-missing-coordinate-system.iso19115"))
          bad-request (iso-metadata-concept bad-metadata)
          bad-ingest (ingest/ingest-concept bad-request)
          {:keys [status errors]} bad-ingest]
      (is (= 422 status))
      (is (= [{:errors
               ["Spatial coordinate reference type must be supplied."]
               :path ["SpatialCoverage"]}] errors)))))

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

;; Verify that successful validation requests do not get an xml or json response body
(deftest successful-validation-with-accept-header-test
  (are [accept]
       (let [concept (dc/collection-concept {})
             response-map (select-keys (ingest/validate-concept concept {:accept-format accept :raw? true})
                                       [:status :body])]
         (= {:status 200 :body ""} response-map))
       :json :xml))

;; Verify that failed validations with no accept or content-type header return xml
(deftest failed-validation-without-headers-returns-xml
  (let [concept (assoc (dc/collection-concept {}) :metadata "<Collection>invalid xml</Collection>")
        {:keys [status body]} (ingest/validate-concept concept {:raw? true})]
    (is (= [400 "<?xml version=\"1.0\" encoding=\"UTF-8\"?><errors><error>Line 1 - cvc-complex-type.2.3: Element 'Collection' cannot have character [children], because the type's content type is element-only.</error><error>Line 1 - cvc-complex-type.2.4.b: The content of element 'Collection' is not complete. One of '{ShortName}' is expected.</error></errors>"]
           [status body]))))

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

(defn assert-invalid-keywords
  [coll-attributes field-path errors]
  (assert-invalid coll-attributes field-path errors {:validate-keywords true}))

(defn assert-valid
  ([coll-attributes]
   (assert-valid coll-attributes nil))
  ([coll-attributes options]
   (let [collection (assoc (dc/collection coll-attributes) :native-id (:native-id coll-attributes))
         provider-id (get coll-attributes :provider-id "PROV1")
         response (d/ingest provider-id collection options)]
     (is (= {:status 200} (select-keys response [:status :errors]))))))

(defn assert-valid-keywords
  [coll-attributes]
  (assert-valid coll-attributes {:validate-keywords true}))

(defn assert-invalid-spatial
  ([coord-sys shapes errors]
   (assert-invalid-spatial coord-sys shapes errors nil))
  ([coord-sys shapes errors options]
   (let [shapes (map (partial umm-s/set-coordinate-system coord-sys) shapes)]
     (assert-invalid {:spatial-coverage (dc/spatial {:gsr coord-sys
                                                     :sr coord-sys
                                                     :geometries shapes})}
                     ["SpatialCoverage" "Geometries" 0]
                     errors
                     options))))

(defn assert-valid-spatial
  [coord-sys shapes]
  (let [shapes (map (partial umm-s/set-coordinate-system coord-sys) shapes)]
    (assert-valid {:spatial-coverage (dc/spatial {:gsr coord-sys
                                                  :sr coord-sys
                                                  :geometries shapes})})))

(defn assert-conflict
  [coll-attributes errors]
  (let [collection (assoc (dc/collection coll-attributes) :native-id (:native-id coll-attributes))
        response (d/ingest "PROV1" collection {:allow-failure? true})]
    (is (= {:status 409
            :errors errors}
           (select-keys response [:status :errors])))))

(deftest collection-keyword-validation-test
  ;; For a list of the valid keywords during testing see dev-system/resources/kms_examples

  (testing "Keyword validation using validation endpoint"
    (let [concept (dc/collection-concept {:platforms [(dc/platform {:short-name "foo"
                                                                    :long-name "Airbus A340-600"})]})
          response (ingest/validate-concept concept {:validate-keywords true})]
      (is (= {:status 422
              :errors [{:path ["Platforms" 0]
                        :errors [(str "Platform short name [foo] and long name [Airbus A340-600] "
                                      "was not a valid keyword combination.")]}]}
             response))))

  (testing "Project keyword validation"
    (are2 [short-name long-name]
          (assert-invalid-keywords
            {:projects [(assoc (dc/project short-name "") :long-name long-name)]}
            ["Projects" 0]
            [(format (str "Project short name [%s] and long name [%s]"
                          " was not a valid keyword combination.")
                     short-name long-name)])

          "Invalid short name"
          "foo" "European Digital Archive of Soil Maps"

          "Invalid with nil long name"
          "foo" nil

          "Invalid long name"
          "EUDASM" "foo"

          "Long name was nil in KMS"
          "EUCREX-94" "foo"

          "Invalid combination"
          "SEDAC/GISS CROP-CLIM DBQ" "European Digital Archive of Soil Maps")

    (are2 [short-name long-name]
          (assert-valid-keywords
            {:projects [(assoc (dc/project short-name "") :long-name long-name)]})
          "Exact match"
          "EUDASM" "European Digital Archive of Soil Maps"

          "Nil long name in project and in KMS"
          "EUCREX-94" nil

          "Case Insensitive"
          "EUDaSM" "European DIgItal ArchIve of SoIl MAps"))

  (testing "Platform keyword validation"
    (are2 [short-name long-name]
          (assert-invalid-keywords
            {:platforms [(dc/platform {:short-name short-name :long-name long-name})]}
            ["Platforms" 0]
            [(format (str "Platform short name [%s] and long name [%s]"
                          " was not a valid keyword combination.")
                     short-name long-name)])

          "Invalid short name"
          "foo" "Airbus A340-600"

          "Long name is nil in KMS"
          "AIRCRAFT" "Airbus A340-600"

          "Invalid long name"
          "DMSP 5B/F3" "foo"

          "Invalid combination"
          "DMSP 5B/F3" "Airbus A340-600")

    (are2 [short-name long-name]
          (assert-valid-keywords
            {:platforms [(dc/platform {:short-name short-name :long-name long-name})]})
          "Exact match"
          "A340-600" "Airbus A340-600"

          "Case Insensitive"
          "a340-600" "aiRBus A340-600"))

  (testing "Instrument keyword validation"
    (are2 [short-name long-name]
          (assert-invalid-keywords
            {:platforms
             [(dc/platform
                {:short-name "A340-600"
                 :long-name "Airbus A340-600"
                 :instruments [(dc/instrument {:short-name short-name
                                               :long-name long-name})]})]}
            ["Platforms" 0 "Instruments" 0]
            [(format (str "Instrument short name [%s] and long name [%s]"
                          " was not a valid keyword combination.")
                     short-name long-name)])
          "Invalid short name"
          "foo" "Airborne Topographic Mapper"

          "Long name is nil in KMS"
          "ACOUSTIC SOUNDERS" "foo"

          "Invalid long name"
          "ATM" "foo"

          "Invalid combination"
          "ATM" "Land, Vegetation, and Ice Sensor")

    (are2 [short-name long-name]
          (assert-valid-keywords
            {:platforms
             [(dc/platform
                {:short-name "A340-600"
                 :long-name "Airbus A340-600"
                 :instruments [(dc/instrument {:short-name short-name
                                               :long-name long-name})]})]})
          "Exact match"
          "ATM" "Airborne Topographic Mapper"

          "Nil long name in project and in KMS"
          "ACOUSTIC SOUNDERS" nil

          "Case Insensitive"
          "Atm" "aIRBORNE Topographic Mapper"))

  (testing "Science Keyword validation"
    (are [attribs]
         (let [sk (dc/science-keyword attribs)]
           (assert-invalid-keywords
             {:science-keywords [sk]}
             ["ScienceKeywords" 0]
             [(msg/science-keyword-not-matches-kms-keywords sk)]))

         {:category "foo"
          :topic "DATA ANALYSIS AND VISUALIZATION"
          :term "GEOGRAPHIC INFORMATION SYSTEMS"}

         {:category "EARTH SCIENCE SERVICES"
          :topic "foo"
          :term "GEOGRAPHIC INFORMATION SYSTEMS"}

         {:category "EARTH SCIENCE SERVICES"
          :topic "DATA ANALYSIS AND VISUALIZATION"
          :term "foo"}

         {:category "EARTH SCIENCE SERVICES"
          :topic "DATA ANALYSIS AND VISUALIZATION"
          :term "GEOGRAPHIC INFORMATION SYSTEMS"
          :variable-level-1 "foo"}

         {:category "EARTH SCIENCE"
          :topic "ATMOSPHERE"
          :term "AEROSOLS"
          :variable-level-1 "AEROSOL OPTICAL DEPTH/THICKNESS"
          :variable-level-2 "foo"}

         {:category "EARTH SCIENCE"
          :topic "ATMOSPHERE"
          :term "ATMOSPHERIC TEMPERATURE"
          :variable-level-1 "SURFACE TEMPERATURE"
          :variable-level-2 "MAXIMUM/MINIMUM TEMPERATURE"
          :variable-level-3 "foo"}

         ;; Invalid combination. Topic is valid but not with these other terms
         {:category "EARTH SCIENCE SERVICES"
          :topic "ATMOSPHERE"
          :term "GEOGRAPHIC INFORMATION SYSTEMS"})

    (are [attribs]
         (assert-valid-keywords {:science-keywords [(dc/science-keyword attribs)]})

         {:category "EARTH SCIENCE SERVICES"
          :topic "DATA ANALYSIS AND VISUALIZATION"
          :term "GEOGRAPHIC INFORMATION SYSTEMS"}

         {:category "EARTH SCIENCE SERVICES"
          :topic "DATA ANALYSIS AND VISUALIZATION"
          :term "GEOGRAPHIC INFORMATION SYSTEMS"
          :variable-level-1 "MOBILE GEOGRAPHIC INFORMATION SYSTEMS"}

         {:category "EARTH SCIENCE"
          :topic "ATMOSPHERE"
          :term "AEROSOLS"
          :variable-level-1 "AEROSOL OPTICAL DEPTH/THICKNESS"
          :variable-level-2 "ANGSTROM EXPONENT"}

         {:category "EARTH SCIENCE"
          :topic "ATMOSPHERE"
          :term "ATMOSPHERIC TEMPERATURE"
          :variable-level-1 "SURFACE TEMPERATURE"
          :variable-level-2 "MAXIMUM/MINIMUM TEMPERATURE"
          :variable-level-3 "24 HOUR MAXIMUM TEMPERATURE"
          :detailed-variable-level "This is ignored"}

         {:category "EARTH SCiENCE"
          :topic "ATMOsPHERE"
          :term "ATMOSpHERIC TEMPERATURE"
          :variable-level-1 "SuRFACE TEMPERATURE"
          :variable-level-2 "MAXiMUM/MiNiMUM TEMPERATURE"
          :variable-level-3 "24 HOUR MAXiMUM TEMPERATURE"})))

;; This tests that UMM type validations are applied during collection ingest.
;; Thorough tests of UMM validations should go in cmr.umm.test.validation.core and related
;; namespaces.
;; Currently in the process of moving validation too UMM Spec Lib. Some validation tests
;; are in cmr.umm-spec.test.validation
(deftest collection-umm-validation-test
  (testing "UMM-C JSON-Schema validation"
    ;; enable return of schema validation errors from API
    (side/eval-form `(icfg/set-return-umm-json-validation-errors! true))
    ;; create collection valid against echo10 but invalid against schema
    (let [response (d/ingest "PROV1" (dc/collection {:product-specific-attributes
                                                     [(dc/psa {:name "bool1" :data-type :boolean :value true})
                                                      (dc/psa {:name "bool2" :data-type :boolean :value true})]})
                             {:allow-failure? true})]
      (is (= {:status 422
              :errors ["object has missing required properties ([\"Organizations\",\"Platforms\",\"ProcessingLevel\",\"RelatedUrls\",\"ScienceKeywords\",\"SpatialExtent\",\"TemporalExtents\"])"]}
             (select-keys response [:status :errors]))))
    ;; disable return of schema validation errors from API
    (side/eval-form `(icfg/set-return-umm-json-validation-errors! false))
    (assert-valid {:product-specific-attributes [(dc/psa {:name "bool1" :data-type :boolean :value true})
                                                 (dc/psa {:name "bool2" :data-type :boolean :value true})]}))
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
  (testing "attempting to ingest using an non-integer revision id returns an error"
    (let [response (ingest/ingest-concept (dc/collection-concept {:concept-id "C2-PROV1"
                                                                  :revision-id "NaN"}))]
      (is (= {:status 422
              :errors [(msg/invalid-revision-id "NaN")]}
             response))))
  (testing "attempting to ingest using a negative revision id returns an error"
    (let [response (ingest/ingest-concept (dc/collection-concept {:concept-id "C2-PROV1"
                                                                  :revision-id "-1"}))]
      (is (= {:status 422
              :errors [(msg/invalid-revision-id "-1")]}
             response))))
  (testing "ingesting a concept with just the revision-id succeeds"
    (let [response (ingest/ingest-concept (dc/collection-concept {:revision-id "2"}))]
      (is (and (= 200 (:status response)) (= 2 (:revision-id response))))))
  (testing "ingesting a concept while skipping revision-ids succeeds, but fails if revision id is smaller than the maximum revision id"
    (let [concept-id "C3-PROV1"
          coll (dc/collection-concept {:concept-id concept-id})
          _ (ingest/ingest-concept (assoc coll :revision-id "2"))
          response1 (ingest/ingest-concept (assoc coll :revision-id "6"))
          response2 (ingest/ingest-concept (assoc coll :revision-id "4"))]
      (is (and (= 200 (:status response1)) (= 6 (:revision-id response1))))
      (is (= {:status 409
              :errors [(format "Expected revision-id of [7] got [4] for [%s]" concept-id)]}
             response2)))))

(defn- assert-revision-conflict
  [concept-id format-str response]
  (is (= {:status 409
          :errors [(format format-str concept-id)]}
         response)))

;; Added to test out of order processing of ingest and delete requests with revision-ids in their
;; header. The proper handling of incorrectly ordered requests is important for Virtual Product
;; Service which picks events off the queue and sends them to ingest service. It cannot be
;; gauranteed that the ingest events are processed by Virtual Product Service in the same order
;; that the events are placed on the queue.
(deftest revision-conflict-tests
  (testing "Update with lower revision id should be rejected if it comes after an concept with a higher revision id"
    (let [concept (dc/collection-concept {:revision-id 4})
          concept-id (:concept-id (ingest/ingest-concept concept))
          response (ingest/ingest-concept (assoc concept :revision-id 2))]
      (assert-revision-conflict concept-id "Expected revision-id of [5] got [2] for [%s]" response)))

  (testing "Delete with lower revision id than latest concept should be rejected"
    (let [concept (dc/collection-concept {:revision-id 4})
          concept-id (:concept-id (ingest/ingest-concept concept))
          response (ingest/delete-concept concept {:revision-id 2})]
      (assert-revision-conflict concept-id "Expected revision-id of [5] got [2] for [%s]" response)))

  (testing "Ingest with lower revision id than latest tombstone should be rejected"
    (let [concept (dc/collection-concept {})
          concept-id (:concept-id (ingest/ingest-concept concept))
          _ (ingest/delete-concept concept {:revision-id 5})
          response (ingest/ingest-concept (assoc concept :revision-id 3))]
      (assert-revision-conflict concept-id "Expected revision-id of [6] got [3] for [%s]" response)))

  (testing "Delete with lower revision id than latest tombstone results in a 404"
    (let [concept (dc/collection-concept {})
          concept-id (:concept-id (ingest/ingest-concept concept))
          _ (ingest/delete-concept concept {:revision-id 5})
          {:keys [status errors]} (ingest/delete-concept concept {:revision-id 3})]
      (is (= status 404))
      (is (= errors [(format "Concept with native-id [%s] and concept-id [%s] is already deleted."
                             (:native-id concept) concept-id)]))))

  (testing "Deleting non-existent collection should be rejected"
    (let [concept (dc/collection-concept {})
          response (ingest/delete-concept concept {:revision-id 2})
          {:keys [status errors]} response]
      (is (= status 404))
      (is (re-find #"Collection .* does not exist" (first errors))))))

(deftest nil-version-test
  (testing "Collections with nil versions are rejected"
    (let [concept (dc/collection-concept {:version-id nil} :iso19115)
          response (ingest/ingest-concept concept)]
      (is (= {:status 422
              :errors ["Version is required."]}
             response)))))

(comment
  (ingest/delete-provider "PROV1")
  ;; Attempt to create race conditions by ingesting the same concept-id simultaneously. We expect
  ;; some requests to succeed while others return a 409.
  ;; If the race condition is reproduced you will see a message like:
  ;; 409 returned, Errors: [Conflict with existing concept-id [C1-PROV1] and revision-id [23]]
  (do
    (cmr.system-int-test.utils.dev-system-util/reset)
    (ingest/create-provider {:provider-guid "provguid1" :provider-id "PROV1"})

    (doseq [_ (range 150)]
      (future (do (let [response (ingest/ingest-concept
                                   (dc/collection-concept {:concept-id "C1-PROV1"
                                                           :native-id "Same Native ID"}))]
                    (when (= 409 (:status response))
                      (println "409 returned, Errors:" (:errors response)))))))))
