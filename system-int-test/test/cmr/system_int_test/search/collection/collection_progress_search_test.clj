(ns cmr.system-int-test.search.collection.collection-progress-search-test
  "Integration test for CMR collection search by collection progress"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.common-app.test.side-api :as side]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-collection-progress
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:CollectionProgress "ACTIVE"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:CollectionProgress "PLANNED"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:CollectionProgress "COMPLETE"}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 4 {:CollectionProgress "DEPRECATED"}))
        coll5 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 5 {:CollectionProgress "NOT PROVIDED"}))
        coll6 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 6 {:CollectionProgress "PREPRINT"}))
        coll7 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 7 {:CollectionProgress "INREVIEW"}))
        coll8 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 8 {:CollectionProgress "SUPERSEDED"}))]

    (index/wait-until-indexed)

    (testing "collection progress parameter search"
      (are3 [items progress options]
        (let [params (merge {:collection-progress progress}
                            options)]
          (d/refs-match? items (search/find-refs :collection params)))

        "single value search"
        [coll1] "ACTIVE" nil

        "case insensitive"
        [coll2] "planned" nil

        "pattern search with RE substring"
        [coll4 coll6 coll7] "*RE*" {"options[collection-progress][pattern]" "true"}

        "OR search"
        [coll1 coll2] ["ACTIVE" "PLANNED"] nil

        "case sensitive search"
        [] "active" {"options[collection-progress][ignore-case]" "false"}

        "invalid value returns empty"
        [] "INVALID" nil))))

(deftest search-collection-progress-active-filter
  "Tests the non-operational collection filter feature flag behavior."
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:CollectionProgress "ACTIVE"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:CollectionProgress "PLANNED"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:CollectionProgress "COMPLETE"}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 4 {:CollectionProgress "DEPRECATED"}))
        coll5 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 5 {:CollectionProgress "NOT PROVIDED"}))
        coll6 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 6 {:CollectionProgress "PREPRINT"}))
        coll7 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 7 {:CollectionProgress "INREVIEW"}))
        coll8 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 8 {:CollectionProgress "SUPERSEDED"}))]

    (index/wait-until-indexed)

    (testing "flag OFF (default) - all 8 collections returned"
      (d/refs-match? [coll1 coll2 coll3 coll4 coll5 coll6 coll7 coll8]
                     (search/find-refs :collection {})))

    (side/eval-form `(cmr.search.config/set-enable-non-operational-collection-filter! true))

    (testing "flag ON, no params - only active collections returned (excludes PLANNED, DEPRECATED, PREPRINT, INREVIEW)"
      (d/refs-match? [coll1 coll3 coll5 coll8]
                     (search/find-refs :collection {})))

    (testing "flag ON + include-non-operational=true - all 8 collections returned"
      (d/refs-match? [coll1 coll2 coll3 coll4 coll5 coll6 coll7 coll8]
                     (search/find-refs :collection {:include-non-operational "true"})))

    (testing "flag ON + include-non-operational=false - only active collections returned"
      (d/refs-match? [coll1 coll3 coll5 coll8]
                     (search/find-refs :collection {:include-non-operational "false"})))

    (testing "flag ON + explicit collection-progress=PLANNED - PLANNED returned (no synthetic filter injected)"
      (d/refs-match? [coll2]
                     (search/find-refs :collection {:collection-progress "PLANNED"})))

    (testing "flag ON + collection-progress=PLANNED + include-non-operational=false - empty (PLANNED is non-operational)"
      (d/refs-match? []
                     (search/find-refs :collection {:collection-progress "PLANNED"
                                                    :include-non-operational "false"})))

    (side/eval-form `(cmr.search.config/set-enable-non-operational-collection-filter! false))))
