(ns cmr.system-int-test.search.collection.collection-collection-progress-search-test
  "Integration test for CMR collection search by collection progress"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
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

        "case insensitive (default)"
        [coll2] "planned" nil

        "pattern search with RE substring"
        [coll4 coll6 coll7] "*RE*" {"options[collection-progress][pattern]" "true"}

        "OR search (default)"
        [coll1 coll2] ["ACTIVE" "PLANNED"] nil

        "AND search returns empty (collections have single value)"
        [] ["ACTIVE" "PLANNED"] {"options[collection-progress][and]" "true"}

        "case sensitive search"
        [] "active" {"options[collection-progress][ignore-case]" "false"}

        "invalid value returns empty"
        [] "INVALID" nil))))
