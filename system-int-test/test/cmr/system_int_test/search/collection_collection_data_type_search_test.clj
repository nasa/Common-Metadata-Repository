(ns cmr.system-int-test.search.collection-collection-data-type-search-test
  "Integration test for CMR collection search by collection data type"
  (:require
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-collection-data-type
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:CollectionDataType "NEAR_REAL_TIME"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:CollectionDataType "SCIENCE_QUALITY"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:CollectionDataType "OTHER"}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 4 {}))]

    (index/wait-until-indexed)

    (testing "search by collection data type."
      (are [items value options] (d/refs-match? items (search/find-refs
                                                       :collection
                                                       (apply merge {:collection-data-type value}
                                                              options)))
           [coll1] "NEAR_REAL_TIME" nil
           [coll2 coll4] "SCIENCE_QUALITY" nil
           [coll2 coll4] "SCIENCE?QUALITY" {"options[collection-data-type][pattern]" "true"}
           [coll2 coll4] "SCIEN*LITY" {"options[collection-data-type][pattern]" "true"}
           [coll3] "OTHER" nil
           [coll1] "near_real_time" nil
           [coll2 coll4] "science_quality" nil
           [coll3] "Other" nil
           [coll1 coll3] ["NEAR_REAL_TIME" "OTHER"] nil
           [] "BLAH" nil))

    (testing "search by collection data type NEAR_REAL_TIME aliases."
      (are [items value] (d/refs-match? items (search/find-refs :collection {:collection-data-type value}))
           [coll1] "near_real_time"
           [coll1] "nrt"
           [coll1] "NRT"
           [coll1] "near real time"
           [coll1] "near-real time"
           [coll1] "near-real-time"
           [coll1] "near real-time"))

    (testing "search by collection data type case default is ignore case true."
      (is (d/refs-match? [coll3]
                         (search/find-refs :collection
                                           {:collection-data-type "other"}))))

    (testing "search by collection data type ignore case false."
      (is (d/refs-match? []
                         (search/find-refs :collection
                                           {:collection-data-type "science_quality"
                                            "options[collection-data-type][ignore-case]" "false"}))))

    (testing "search by collection data type ignore case true"
      (is (d/refs-match? [coll2 coll4]
                         (search/find-refs :collection
                                           {:collection-data-type "science_quality"
                                            "options[collection-data-type][ignore-case]" "true"}))))))
