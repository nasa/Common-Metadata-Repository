(ns cmr.system-int-test.ingest.ingest-lifecycle-integration-test
  "Tests the Ingest lifecycle of a granules and collections. Verifies that at each point the correct
  data is indexed and searchable."
  (:require
    [cheshire.core :as json]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [clojure.test :refer :all]
    [cmr.common-app.config :as common-config]
    [cmr.common-app.test.side-api :as side]
    [cmr.common.mime-types :as mt]
    [cmr.common.util :refer [are2]]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.umm-spec.models.umm-collection-models :as umm-c]
    [cmr.umm-spec.models.umm-common-models :as umm-cmn]
    [cmr.umm-spec.test.expected-conversion :as expected-conversion]
    [cmr.umm-spec.test.location-keywords-helper :as lkt]
    [cmr.umm-spec.umm-spec-core :as umm-spec]
    [cmr.umm-spec.versioning :as ver]
    [cmr.umm.collection.entry-id :as eid]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def context (lkt/setup-context-for-test))

(defn- unparse-date-time
  "Parse a date-time into a string that can be used in a parameter query"
  [date-time]
  (f/unparse (f/formatters :date-time-no-ms) date-time))

(defn- date-time-range->range-param
  "Convert a date-time range to a string suitable for a range parameter query"
  [date-time-range]
  (let [{begin-date-time :BeginningDateTime end-date-time :endingDateTime} date-time-range
        [begin-str end-str] (map unparse-date-time [begin-date-time end-date-time])]
    (format "%s,%s", begin-str end-str)))

(defn assert-granules-found
  ([granules]
   (assert-granules-found granules {}))
  ([granules params]
   (index/wait-until-indexed)
   (is (d/refs-match? granules (search/find-refs :granule params))
       (str "Could not find granules with " (pr-str params)))))

(defn assert-collections-found
  ([collections]
   (assert-collections-found collections {}))
  ([collections params]
   (index/wait-until-indexed)
   (is (d/refs-match? collections (search/find-refs :collection params)))))

(defn assert-collections-and-granules-found
  [collections granules]
  (assert-collections-found collections)
  (assert-granules-found granules))

(defn assert-valid
  [umm-record]
  (let [response (ingest/validate-concept (d/item->concept umm-record))]
    (is (= {:status 200} (select-keys response [:status :errors])))))

(defn assert-valid-umm-spec-collection
  [umm-spec-collection]
  (let [response (ingest/validate-concept (d/umm-c-collection->concept umm-spec-collection))]
    (is (= {:status 200} (select-keys response [:status :errors])))))

(defn assert-granule-with-parent-collection-valid
  "Asserts that when the granule and optional collection concept are valid. The collection concept
  can be passed as a third argument and it will be sent along with the granule instead of using a
  previously ingested collection."
  [granule collection]
  (let [response (ingest/validate-granule (d/item->concept granule) (d/item->concept collection))]
    (is (= {:status 200} (select-keys response [:status :errors])))))

(defn ingest-coll
  "Validates and ingests the collection. Allow the revision-id to be determined by metadata-db
  rather than passing one in."
  [coll]
  (assert-valid-umm-spec-collection coll)
  (let [response (d/ingest-umm-spec-collection "PROV1" (dissoc coll :revision-id))]
    (is (#{200 201} (:status response)))
    response))

(defn make-coll
  "Creates, validates, and ingests a collection using the unique number given"
  [n]
  (ingest-coll (data-umm-c/collection {:ShortName (str "SN" n) :EntryTitle (str "ET" n)})))

(defn update-coll
  "Validates and updates the collection with the given attributes"
  [coll attribs]
  (ingest-coll (merge coll attribs)))

(defn ingest-gran
  "Validates and ingests the granule. Allow the revision-id to be determined by metadata-db
  rather than passing one in."
  [coll granule]
  ;; Granule is valid sent by itself
  (assert-valid granule)
  ;; Granule is valid sent with parent collection
  (assert-granule-with-parent-collection-valid granule coll)
  (let [response (d/ingest "PROV1" (dissoc granule :revision-id))]
    (is (#{200 201} (:status response)))
    response))

(defn make-gran
  "Creates, validates, and ingests a granule using the unique number given"
  [coll n]
  (ingest-gran coll (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:granule-ur (str "GR" n)})))

(defn update-gran
  "Validates and updates the granule with the given attributes"
  [coll gran attribs]
  (ingest-gran coll (merge gran attribs)))

(comment
  ;; for REPL testing purposes
  (def example-collection-record expected-conversion/example-collection-record)
  (cmr.umm.umm-core/parse-concept {:metadata (cmr.umm-spec.umm-spec-core/generate-metadata
                                              example-collection-record :echo10)
                                   :concept-type :collection
                                   :format "application/echo10+xml"}))

(deftest spatial-keywords-migration-test
  (testing "Make sure that spatial keywords are converted to LocationKeywords from 1.1->1.2"
    (let [coll expected-conversion/example-collection-record
          mime-type "application/vnd.nasa.cmr.umm+json;version=1.1"
          input-str (umm-spec/generate-metadata context coll mime-type)
          input-version "1.1"
          output-version "1.2"
          {:keys [status headers body]} (ingest/translate-between-umm-versions :collection input-version input-str output-version nil)
          content-type (first (mt/extract-mime-types (:content-type headers)))
          response (json/parse-string body)]
      (is (= [{"Category" "CONTINENT",
               "Type" "AFRICA",
               "Subregion1" "CENTRAL AFRICA",
               "Subregion2" "ANGOLA"}
              {"Category" "OTHER", "Type" "Detailed Somewhereville"}]
             (get response "LocationKeywords")))
      (is (= ["ANGOLA" "Detailed Somewhereville"] (get response "SpatialKeywords"))))))

(deftest mmt-ingest-round-trip
  (testing "ingest and search UMM JSON metadata"
    ;; test for each UMM JSON version
  (let [accepted-version (common-config/collection-umm-version)
        _ (side/eval-form `(common-config/set-collection-umm-version! 
                          ver/current-collection-version))]
    (doseq [v (ver/versions :collection)]
      (let [coll      expected-conversion/example-collection-record
            mime-type (str "application/vnd.nasa.cmr.umm+json;version=" v)
            json      (umm-spec/generate-metadata context coll mime-type)
            result    (d/ingest-concept-with-metadata  {:provider-id  "PROV1"
                                                        :concept-type :collection
                                                        :format       mime-type
                                                        :metadata     json})]
        (index/wait-until-indexed)
        ;; parameter queries
        (are2 [items params]
              (d/refs-match? items (search/find-refs :collection params))

              "entry-title matches"
              [result] {:entry_title "The entry title V5"}
              "entry-title not matches"
              [] {:entry_title "foo"}

              "entry-id matches"
              [result] {:entry_id (eid/entry-id (:ShortName result) (:Version result))}
              "entry-id not matches"
              [] {:entry_id "foo"}

              "native-id matches"
              [result] {:native_id "native-id"}
              "native-id not matches"
              [] {:native_id "foo"}

              "short-name matches"
              [result] {:short_name "short"}
              "short-name not matches"
              [] {:short_name "foo"}

              "version matches"
              [result] {:version "V5"}
              "version not matches"
              [] {:version "foo"}

              "updated-since matches"
              [result] {:updated_since "2000-01-01T10:00:00Z"}
              "updated-since not matches"
              [] {:updated_since "3000-01-01T10:00:00Z"}

              "revision-date matches"
              [result] {:revision_date "2000-01-01T10:00:00Z,3000-01-01T10:00:00Z"}
              "revision-date not matches"
              [] {:revision_date "3000-01-01T10:00:00Z,3001-01-01T10:00:00Z"}

              "processing level matches"
              [result] {:processing_level "3"}
              "processing level not matches"
              [] {:processing_level "foo"}

              "collection data type matches"
              [result] {"collection_data_type[]" "SCIENCE_QUALITY"}

              "temporal matches"
              [result] {:temporal "2000-01-01T00:00:00Z,2015-12-04T13:55:29Z"}
              "temporal not matches"
              [] {:temporal "3000-01-01T10:00:00Z,3001-01-01T10:00:00Z"}

              "concept-id matches"
              [result] {:concept_id "C1200000005-PROV1"}
              "concept-id not matches"
              [] {:concept-id "C1200000002-PROV1"}

              "platform matches"
              [result] {:platform "Platform 1"}
              "platform not matches"
              [] {:platform "foo"}

              "instrument"
              [result] {:instrument "An Instrument"}

              "sensor"
              [result] {:sensor "ABC"}

              "project matches"
              [result] {:project "project short_name"}
              "project not matches"
              [] {:project "foo"}

              ;; archive-center, data-center - CMR-2265 still need to figure out how to tell them apart in UMM-C
              ;; Got rid of spatial keywords, moving to location keywords.
              "temporal keywords match"
              [result] {:keyword "temporal keyword 1"}

              "two-d-coordinate-system-name matches"
              [result] {:two-d-coordinate-system-name "MISR"}

              "science-keywords"
              [result] {:science-keywords {:0 {:category "EARTH SCIENCE"
                                               :topic    "top"
                                               :term     "ter"}}}

              "additional attributes match"
              [result] {"attribute[]" "PercentGroundHit"}

              "downloadable matches"
              [] {:downloadable false}
              "downloadable not matches"
              [result] {:downloadable true}

              "browsable matches"
              [result] {:browsable true}
              "browsable not matches"
              [] {:browsable false}

              "bounding box"
              [result] {:bounding_box "-180,-90,180,90"})))
    (side/eval-form `(common-config/set-collection-umm-version! ~accepted-version)))))

;; Tests that over the lifecycle of a collection and granule the right data will be found.
;; Test Outline
;; - Ingest collections
;; - Ingest granules
;; - update collection
;; - update granule
;; - delete granule
;; - re-ingest granule
;; - delete collection
;; - re-ingest collection
;; At each step the data that is indexed is checked. It also verifies validation works at every step
;; as well
(deftest ingest-lifecycle-test
  ;; Nothing should be found yet.
  (assert-collections-and-granules-found [] [])

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; Inserts
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; Insert collections
  (let [coll1 (make-coll 1)
        coll2 (make-coll 2)
        coll3 (make-coll 3)]
    ;; The collections can be found
    (assert-collections-and-granules-found [coll1 coll2 coll3] [])
    ;; Insert granules
    (let [gr1 (make-gran coll1 1)
          gr2 (make-gran coll1 2)
          gr3 (make-gran coll2 3)
          gr4 (make-gran coll2 4)]
      (assert-collections-and-granules-found [coll1 coll2 coll3] [gr1 gr2 gr3 gr4])
      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; Updates
      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

      (let [;; Update a collection
            coll2 (update-coll coll2 {:Projects (data-umm-cmn/projects "ESI")})
            ;; Update a granule
            gr1 (update-gran coll1 gr1 {:data-granule (dg/data-granule {:day-night "DAY"})})]
        ;; All items can still be found
        (assert-collections-and-granules-found [coll1 coll2 coll3] [gr1 gr2 gr3 gr4])
        ;; Updated collections and granule are found with specific parameters
        (assert-collections-found [coll2] {:project "ESI"})
        (assert-granules-found [gr1] {:day-night-flag "DAY"})
        ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
        ;; Deletion and Recreation
        ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

        ;; Delete a granule
        (ingest/delete-concept (d/item->concept gr3))
        (assert-collections-and-granules-found [coll1 coll2 coll3] [gr1 gr2 gr4])

        ;; Reingest the granule
        (let [gr3 (ingest-gran coll2 gr3)]
          (assert-collections-and-granules-found [coll1 coll2 coll3] [gr1 gr2 gr3 gr4])

          ;; Delete a collection
          (ingest/delete-concept (d/item->concept coll1))
          ;; Verify collection delete results in collection and child collections not found
          (assert-collections-and-granules-found [coll2 coll3] [gr3 gr4])

          ;; Reingest the collection
          (let [coll1 (ingest-coll coll1)]
            (assert-collections-and-granules-found [coll1 coll2 coll3] [gr3 gr4])))))))
