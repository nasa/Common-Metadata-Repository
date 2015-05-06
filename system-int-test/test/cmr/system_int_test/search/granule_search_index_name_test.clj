(ns cmr.system-int-test.search.granule-search-index-name-test
  "Integration tests for searching granules based on the configured index-names.
  This tests that the cmr-search-app and cmr-indexer-app will both put and find data in the correct granule index."
  (:require [clojure.test :refer :all]
            [cmr.common.config :as config]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
            [cmr.indexer.system :as indexer-system]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "LONG_PROV2"}))

(defn get-colls-with-separate-indexes
  "Gets the collections with separate indexes configured in the running dev system."
  []
  (dev-sys-util/eval-in-dev-sys `(indexer-system/colls-with-separate-indexes)))

(defn set-colls-with-separate-indexes!
  "Sets the collections with separate indexes configured in the running dev system."
  [separate-indexes]
  (dev-sys-util/eval-in-dev-sys
    `(indexer-system/set-colls-with-separate-indexes!
       ~(vec separate-indexes)))

  ;; Verify that our update took place
  (is (= separate-indexes (get-colls-with-separate-indexes)))

  ;; Make the indexer update indexes adding two new indexes for coll2 and coll3
  (index/update-indexes)
  (index/wait-until-indexed)

  ;; The search application caches the index set so we have to clear it's cache prior to searching.
  (search/clear-caches))

(defmacro use-separate-indexes
  "Defines separate indexes for collections in the indexer, executes the body, and then undoes it's
  changes. This must run a reset as the final step so it should be the last thing to run in a test."
  [body]
  `(let [orig-colls-with-sep-indexes# (get-colls-with-separate-indexes)]
     (set-colls-with-separate-indexes!
       (concat orig-colls-with-sep-indexes# ["C2-PROV1" "C3-PROV1"]
               (map #(str "C" % "-LONG_PROV2") (range 250))))
     (try
       ~body
       (finally
         ;; Reset must be called here to delete any extra data in those indexes.
         ;; After the separate indexes are removed from the configuration reset wouldn't know to clear them.
         (dev-sys-util/reset)
         (set-colls-with-separate-indexes! orig-colls-with-sep-indexes#)))))

;; Tests general searching on differen fields with some granules in separate indexes and some in
;; small collections
(deftest search-on-index-names
  (use-separate-indexes
    (let [coll1 (d/ingest "PROV1" (dc/collection {:short-name "ShortOne"
                                                  :concept-id "C1-PROV1"}))
          coll2 (d/ingest "PROV1" (dc/collection {:short-name "ShortTwo"
                                                  :concept-id "C2-PROV1"}))
          coll3 (d/ingest "PROV1" (dc/collection {:concept-id "C3-PROV1"}))
          coll4 (d/ingest "PROV1" (dc/collection {:short-name "ShortThree"
                                                  :concept-id "C4-PROV1"}))
          gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))
          gran2 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule2"}))
          gran3 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule3"}))
          gran4 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "Granule4"}))
          gran5 (d/ingest "PROV1" (dg/granule coll3 {:granule-ur "Granule5"}))
          gran6 (d/ingest "PROV1" (dg/granule coll4 {:granule-ur "Granule6"}))]

      (index/wait-until-indexed)

      (testing "search for granules in separate collection index."
        (is (d/refs-match?
              [gran1 gran4]
              (search/find-refs :granule {"granule-ur[]" ["Granule1" "Granule4"]}))))
      (testing "search for granules in separate collection index with collection identifier."
        (is (d/refs-match?
              [gran1]
              (search/find-refs :granule {:short-name "ShortOne"
                                          "granule-ur[]" ["Granule1" "Granule4"]}))))
      (testing "search for granules in multiple collection indexes with collection identifier."
        (is (d/refs-match?
              [gran1 gran2 gran3 gran4]
              (search/find-refs :granule {"short-name[]" ["ShortOne" "ShortTwo"]}))))
      (testing "search for granules in multiple collection indexes with collection identifier and small_collections."
        (is (d/refs-match?
              [gran4 gran6]
              (search/find-refs :granule {"short-name[]" ["ShortTwo" "ShortThree"]}))))
      (testing "search for granules in small_collections index."
        (is (d/refs-match?
              [gran5]
              (search/find-refs :granule {:granule-ur "Granule5"}))))
      (testing "search for granules in small_collections index with collection identifier."
        (is (d/refs-match?
              [gran6]
              (search/find-refs :granule {:short-name "ShortThree"})))))))


;; This tests that additional granule indexes can be configured in the indexer after initially
;; indexing things and that this won't impact existing collections
(deftest update-granule-collection-indexes-test
  ;; Add some initial granules
  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1" :concept-id "C1-PROV1"}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran1"}))]
    (index/wait-until-indexed)
    (d/assert-refs-match [gran1] (search/find-refs :granule {}))

    ;; Use separate collection indexes
    (use-separate-indexes
      ;; Add two more granules which should end up in their own indexes and be searchable.
      (let [coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2" :concept-id "C2-PROV1"}))
            coll3 (d/ingest "PROV1" (dc/collection {:entry-title "coll3" :concept-id "C3-PROV1"}))
            gran2 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "gran2"}))
            gran3 (d/ingest "PROV1" (dg/granule coll3 {:granule-ur "gran3"}))]
        (index/wait-until-indexed)
        (d/assert-refs-match [gran1 gran2 gran3] (search/find-refs :granule {}))))))




