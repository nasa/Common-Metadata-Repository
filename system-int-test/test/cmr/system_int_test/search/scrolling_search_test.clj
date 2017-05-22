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

(deftest basic-scrolling
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

    (testing "Basic scrolling"
      (let [{:keys [hits scroll-id]} (search/find-refs :granule {:provider "PROV1" :scroll true})]
        (testing "First call with scroll returns scroll-id header and hits count"
          (is (= (count all-grans) hits))
          (is (not (nil? scroll-id))))
        
        (testing "Subsequent calls with scroll-id get data"
          (is (data2-core/refs-match?
               all-grans
               (search/find-refs :granule {:scroll true :scroll-id scroll-id}))))))

    (testing "Scrolling with page size"
      (let [{:keys [hits scroll-id]} (search/find-refs :granule {:provider "PROV1" :scroll true :page-size 1})]
        (testing "First call with scroll and page size returns scroll-id and hits count"
          (is (= (count all-grans) hits))
          (is (not (nil? scroll-id))))
        
        (testing "Subsequent calls gets page-size results"
          (doseq [gran all-grans
                  result (search/find-refs :granule {:scroll true :scroll-id scroll-id})]
            (is (data2-core/refs-match? [gran] result))))))))
                 
                  
          
                                        

    
