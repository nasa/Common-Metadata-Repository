(ns cmr.system-int-test.search.collection-latency-search-test
  "Integration tests for collection latency search"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as umm-c]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest collection-latency-search-test

  (let [coll1 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 1 {:CollectionDataType "NEAR_REAL_TIME"})
               {:format :umm-json})
        coll2 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 2 {:CollectionDataType "LOW_LATENCY"})
               {:format :umm-json})
        coll3 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 3 {:CollectionDataType "EXPEDITED"})
               {:format :umm-json})
        coll4 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 4 {:CollectionDataType "OTHER"})
               {:format :umm-json})
        coll5 (d/ingest-umm-spec-collection
               "PROV1"
               (umm-c/collection 5 {:CollectionDataType "SCIENCE_QUALITY"})
               {:format :umm-json}) 
        id1 (:concept-id coll1)
        id2 (:concept-id coll2)
        id3 (:concept-id coll3)
        id4 (:concept-id coll4)
        id5 (:concept-id coll5)]

    (index/wait-until-indexed)

    (testing "latency parameter search"
      (are3 [items latency options]
        (let [params (merge {:latency latency}
                            options)]
          (d/refs-match? items (search/find-refs :collection params)))

        "latency search1"
        [coll1] "1 to 3 hours" nil

        "latency search2 ignore case"
        [coll2] "3 to 24 HOuRs" nil

        "latency search3"
        [coll3] "1 to 4 days" nil

        "Pattern search"
        [coll1 coll2 coll3] "*to*" {"options[latency][pattern]" "true"}

        "Pattern search, leading wildcard only"
        [coll1] "*to 3 hours" {"options[latency][pattern]" "true"}

        "Or search"
        [coll1 coll2] ["1 to 3 hours" "3 to 24 hours"] nil))))
