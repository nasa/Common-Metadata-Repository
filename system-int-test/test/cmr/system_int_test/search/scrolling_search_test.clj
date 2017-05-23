(ns cmr.system-int-test.search.scrolling-search-test
  "Tests for using the scroll parameter to retrieve search resutls"
  (:require
   [clojure.test :refer :all]
   [cmr.common.concepts :as concepts]
   [cmr.system-int-test.data2.core :as data2-core]
   [cmr.system-int-test.data2.granule :as data2-granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest granule-scrolling
  (let [coll1 (data2-core/ingest-umm-spec-collection "PROV1" 
                                                     (data-umm-c/collection {:EntryTitle "E1"
                                                                             :ShortName "S1"
                                                                             :Version "V1"})) 
        coll2 (data2-core/ingest-umm-spec-collection "PROV1" 
                                                     (data-umm-c/collection {:EntryTitle "E2"
                                                                             :ShortName "S2"
                                                                             :Version "V2"}))
        coll1-cid (get-in coll1 [:concept-id])
        coll2-cid (get-in coll2 [:concept-id])
        gran1 (data2-core/ingest "PROV1" 
                                 (data2-granule/granule-with-umm-spec-collection coll1 
                                                                                 coll1-cid 
                                                                                 {:granule-ur "Granule1"}))
        gran2 (data2-core/ingest "PROV1" 
                          (data2-granule/granule-with-umm-spec-collection coll1 
                                                                          coll1-cid 
                                                                          {:granule-ur "Granule2"}))
        gran3 (data2-core/ingest "PROV1" 
                          (data2-granule/granule-with-umm-spec-collection coll1 
                                                                          coll1-cid 
                                                                          {:granule-ur "Granule3"}))
        gran4 (data2-core/ingest "PROV1" 
                          (data2-granule/granule-with-umm-spec-collection coll2 
                                                                          coll2-cid 
                                                                          {:granule-ur "Granule4"}))
        gran5 (data2-core/ingest "PROV1" 
                          (data2-granule/granule-with-umm-spec-collection coll2 
                                                                          coll2-cid 
                                                                          {:granule-ur "Granule5"}))
        all-grans [gran1 gran2 gran3 gran4 gran5]]
    (index/wait-until-indexed)
               
    (testing "Scrolling with page size"
      (let [{:keys [hits scroll-id] :as result} (search/find-refs :granule {:provider "PROV1" :scroll true :page-size 2})]
        (testing "First call returns scroll-id and hits count with page-size results"
          (is (= (count all-grans) hits))
          (is (not (nil? scroll-id)))
          (is (data2-core/refs-match? [gran1 gran2] result)))
        
        (testing "Subsequent call gets page-size results"
          (let [result (search/find-refs :granule {:scroll true :scroll-id scroll-id})]
            (is (= (count all-grans) hits))
            (is (data2-core/refs-match? [gran3 gran4] result))))

        (testing "All results returned eventually"
          (let [result (search/find-refs :granule {:scroll true :scroll-id scroll-id})]
            (is (= (count all-grans) hits))
            (is (data2-core/refs-match? [gran5] result))))

        (testing "Calls beyond total hits return empty list"
          (let [result (search/find-refs :granule {:scroll true :scroll-id scroll-id})]
            (is (= (count all-grans) hits))
            (is (data2-core/refs-match? [] result))))))
    
    (testing "page_num is not allowed with scrolling"
      (let [response (search/find-refs :granule 
                                      {:provider "PROV1" :scroll true :page-num 2} 
                                      {:allow-failure? true})]
        (is (= 400 (:status response)))
        (is (= "page_num is not allowed with scrolling"
               (first (:errors response))))))))
