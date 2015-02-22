(ns cmr.system-int-test.ingest.ingest-lifecycle-integration-test
  "Tests the Ingest lifecycle of a granules and collections. Verifies that at each point the correct
  data is indexed and searchable."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.old-ingest-util :as old-ingest]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn assert-granules-found
  ([granules]
   (assert-granules-found granules {}))
  ([granules params]
   (is (d/refs-match? granules (search/find-refs :granule params))
       (str "Could not find granules with " (pr-str params)))))

(defn assert-collections-found
  ([collections]
   (assert-collections-found collections {}))
  ([collections params]
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
  "Validates and ingests the collection"
  [coll]
  (assert-valid coll)
  (d/ingest "PROV1" coll))

(defn make-coll
  "Creates, validates, and ingests a collection using the unique number given"
  [n]
  (ingest-coll (dc/collection {:entry-title (str "ET" n)})))

(defn update-coll
  "Validates and updates the collection with the given attributes"
  [coll attribs]
  (ingest-coll (merge coll attribs)))

(defn ingest-gran
  "Validates and ingests the granle"
  [coll granule]
  ;; Granule is valid sent by itself
  (assert-valid granule)
  ;; Granule is valid sent with parent collection
  (assert-granule-with-parent-collection-valid granule coll)
  (d/ingest "PROV1" granule))

(defn make-gran
  "Creates, validates, and ingests a granule using the unique number given"
  [coll n]
  (ingest-gran coll (dg/granule coll {:granule-ur (str "GR" n)})))

(defn update-gran
  "Validates and updates the granule with the given attributes"
  [coll gran attribs]
  (ingest-gran coll (merge gran attribs)))


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

  ;; Insert collections
  (let [coll1 (make-coll 1)
        coll2 (make-coll 2)
        coll3 (make-coll 3)]
    (index/wait-until-indexed)
    ;; The collections can be found
    (assert-collections-and-granules-found [coll1 coll2 coll3] [])

    ;; Insert granules
    (let [gr1 (make-gran coll1 1)
          gr2 (make-gran coll1 2)
          gr3 (make-gran coll2 3)
          gr4 (make-gran coll2 4)]
      (index/wait-until-indexed)
      ;; The granule can be found
      (assert-granules-found [gr1 gr2 gr3 gr4])

      (let [;; Update a collection
            coll2 (update-coll coll2 {:projects (dc/projects "ESI")})
            ;; Update a granule
            gr1 (update-gran coll1 gr1 {:data-granule (dg/data-granule {:day-night "DAY"})})]
        (index/wait-until-indexed)
        ;; All items can still be found
        (assert-collections-and-granules-found [coll1 coll2 coll3] [gr1 gr2 gr3 gr4])

        ;; Updated collections are found
        (assert-collections-found [coll2] {:project "ESI"})

        ;; Updated granules are found
        (assert-granules-found [gr1] {:day-night-flag "DAY"})

        ;; Delete a granule
        (ingest/delete-concept (d/item->concept gr3))
        (index/wait-until-indexed)
        (assert-collections-and-granules-found [coll1 coll2 coll3] [gr1 gr2 gr4])

        ;; Reingest the granule
        (let [gr3 (ingest-gran coll2 gr3)]
          (index/wait-until-indexed)
          (assert-collections-and-granules-found [coll1 coll2 coll3] [gr1 gr2 gr3 gr4])

          ;; Delete a collection
          (ingest/delete-concept (d/item->concept coll1))
          (index/wait-until-indexed)
          ;; Verify collection delete results in collection and child collections not found
          (assert-collections-and-granules-found [coll2 coll3] [gr3 gr4])

          ;; Reingest the collection
          (let [coll1 (ingest-coll coll1)]
            (index/wait-until-indexed)
            (assert-collections-and-granules-found [coll1 coll2 coll3] [gr3 gr4])))))))

