(ns cmr.system-int-test.ingest.ingest-lifecycle-integration-test
  "Tests the Ingest lifecycle of a granules and collections. Verifies that at each point the correct
  data is indexed and searchable."
  (:require [clojure.test :refer :all]
            [clj-time.format :as f]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.util :refer [are2]]
            [cmr.umm-spec.core :as umm-spec]
            [cmr.umm.collection.entry-id :as eid]
            [cmr.umm-spec.test.expected-conversion :as expected-conversion]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

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
  (assert-valid coll)
  (let [response (d/ingest "PROV1" (dissoc coll :revision-id))]
    (is (= 200 (:status response)))
    response))

(defn make-coll
  "Creates, validates, and ingests a collection using the unique number given"
  [n]
  (ingest-coll (dc/collection {:entry-title (str "ET" n)})))

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
    (is (= 200 (:status response)))
    response))

(defn make-gran
  "Creates, validates, and ingests a granule using the unique number given"
  [coll n]
  (ingest-gran coll (dg/granule coll {:granule-ur (str "GR" n)})))

(defn update-gran
  "Validates and updates the granule with the given attributes"
  [coll gran attribs]
  (ingest-gran coll (merge gran attribs)))

(comment
  ;; for REPL testing purposes
  (def example-collection-record expected-conversion/example-collection-record)
  (cmr.umm.core/parse-concept {:metadata (cmr.umm-spec.core/generate-metadata :collection :echo10 example-collection-record)
                               :concept-type :collection
                               :format "application/echo10+xml"})
  )

(deftest mmt-ingest-round-trip
    (testing "ingest and search UMM JSON metadata"
      (let [example-collection-record expected-conversion/example-collection-record
            umm-json (umm-spec/generate-metadata :collection :umm-json example-collection-record)
            coll (d/ingest-concept-with-metadata {:provider-id "PROV1"
                                                  :concept-type :collection
                                                  :format-key :umm-json
                                                  :metadata umm-json})]
        (index/wait-until-indexed)
        ;; parameter queries
         (are2 [items params]
           (d/refs-match? items (search/find-refs :collection params))

           "entry-title matches"
           [coll] {:entry_title "The entry title V5"}
           "entry-title not matches"
           [] {:entry_title "foo"}

           "entry-id matches"
           [coll] {:entry_id (eid/entry-id (:ShortName example-collection-record) (:Version example-collection-record))}
           "entry-id not matches"
           [] {:entry_id "foo"}

           "native-id matches"
           [coll] {:native_id "native-id"}
           "native-id not matches"
           [] {:native_id "foo"}

           "short-name matches"
           [coll] {:short_name "short"}
           "short-name not matches"
           [] {:short_name "foo"}

           "version matches"
           [coll] {:version "V5"}
           "version not matches"
           [] {:version "foo"}

           "updated-since matches"
           [coll] {:updated_since "2000-01-01T10:00:00Z"}
           "updated-since not matches"
           [] {:updated_since "3000-01-01T10:00:00Z"}

           "revision-date matches"
           [coll] {:revision_date "2000-01-01T10:00:00Z,3000-01-01T10:00:00Z"}
           "revision-date not matches"
           [] {:revision_date "3000-01-01T10:00:00Z,3001-01-01T10:00:00Z"}

           "processing level matches"
           [coll] {:processing_level "3"}
           "processing level not matches"
           [] {:processing_level "foo"}

           "collection data type matches"
           [coll] {"collection_data_type[]" "SCIENCE_QUALITY"}

           "temporal matches"
           [coll] {:temporal "2000-01-01T00:00:00Z,2015-12-04T13:55:29Z"}
           "temporal not matches"
           [] {:temporal "3000-01-01T10:00:00Z,3001-01-01T10:00:00Z"}

           "concept-id matches"
           [coll] {:concept_id "C1200000000-PROV1"}
           "concept-id not matches"
           [] {:concept-id "C1200000001-PROV1"}

           "platform matches"
           [coll] {:platform "Platform 1"}
           "platform not matches"
           [] {:platform "foo"}

           "instrument"
           [coll] {:instrument "An Instrument"}

           "sensor"
           [coll] {:sensor "ABC"}

           "project matches"
           [coll] {:project "project short_name"}
           "project not matches"
           [] {:project "foo"}

           ;; archive-center, data-center - TODO still need to figure out how to tell them apart in
           ;; UMM-C (CMR-2265).

           "spatial keywords match"
           [coll] {"spatial_keyword[]" "SPK1"}
           "non-matching spatial keyword"
           [] {"spatial_keyword[]" "foobar"}

           "temporal keywords match"
           [coll] {:keyword "temporal keyword 1"}

           "two-d-coordinate-system-name matches"
           [coll] {:two-d-coordinate-system-name "Tiling System Name"}

           "science-keywords"
           [coll] {:science-keywords {:0 {:category "EARTH SCIENCE"
                                          :topic "top"
                                          :term "ter"}}}

           "additional attributes match"
           [coll] {"attribute[]" "PercentGroundHit"}

           "downloadable matches"
           [] {:downloadable false}
           "downloadable not matches"
           [coll] {:downloadable true}

           "browsable matches"
           [coll] {:browsable true}
           "browsable not matches"
           [] {:browsable false}

           "bounding box"
           [coll] {:bounding_box "-180,-90,180,90"}

           ))))

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
            coll2 (update-coll coll2 {:projects (dc/projects "ESI")})
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

