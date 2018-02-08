(ns cmr.system-int-test.search.scrolling-search-test
  "Tests for using the scroll parameter to retrieve search results"
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common-app.api.routes :as routes]
   [cmr.common.mime-types :as mime-types]
   [cmr.common.util :as util :refer [are3]]
   [cmr.elastic-utils.config :as es-config]
   [cmr.system-int-test.data2.core :as data2-core]
   [cmr.system-int-test.data2.granule :as data2-granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
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
      (let [{:keys [hits scroll-id] :as result} (search/find-refs 
                                                 :granule 
                                                 {:provider "PROV1" :scroll true :page-size 2})]
        (testing "First call returns scroll-id and hits count with page-size results"
          (is (= (count all-grans) hits))
          (is (not (nil? scroll-id)))
          (is (data2-core/refs-match? [gran1 gran2] result)))
        
        (testing "Subsequent searches gets original page-size results"
          (let [result (search/find-refs :granule 
                                         {:scroll true :page-size 10}
                                         {:headers {routes/SCROLL_ID_HEADER scroll-id}})]
            (is (= (count all-grans) hits))
            (is (data2-core/refs-match? [gran3 gran4] result))))

        (testing "Remaining results returned on last search"
          (let [result (search/find-refs :granule 
                                         {:scroll true}
                                         {:headers {routes/SCROLL_ID_HEADER scroll-id}})]
            (is (= (count all-grans) hits))
            (is (data2-core/refs-match? [gran5] result))))

        (testing "Searches beyond total hits return empty list"
          (let [result (search/find-refs :granule 
                                         {:scroll true}
                                         {:headers {routes/SCROLL_ID_HEADER scroll-id}})]
            (is (= (count all-grans) hits))
            (is (data2-core/refs-match? [] result))))))
    
    (testing "Scrolling with different search params from the original"
      (let [result (search/find-concepts-in-format
                     mime-types/xml
                     :granule
                     {:provider "PROV1" :scroll true :page-size 2})
            format (get-in result [:headers :Content-Type])
            hits (get-in result [:headers :CMR-Hits])
            scroll-id (get-in result [:headers :CMR-Scroll-Id])
            ;; Do a subsequent scroll search with different format, provider, scroll and page-size.
            ;; Verify the new search params are ignored. It still returns the same xml format.
            subsequent-result (search/find-concepts-in-format
                                mime-types/json
                                :granule
                                {:provider "PROV2" :scroll false :page-size 10}
                                {:headers {routes/SCROLL_ID_HEADER scroll-id}})
            subsequent-format (get-in subsequent-result [:headers :Content-Type])
            subsequent-hits (get-in subsequent-result [:headers :CMR-Hits])]
         (is (= hits subsequent-hits))
         (is (= format subsequent-format))))

    ;; The following test is included for completeness to test session timeouts, but it
    ;; cannot be run regularly because it is impossible to predict how long it will take
    ;; for Elasticsearch to actually time out a scroll session. Even when the scroll timeout
    ;; is set to 1 second it may be many seconds before Elasticsearch disposes of the session. 
    ;; The following test should not be removed and only uncommented during manual testing.
    
    #_(testing "Expired scroll-id is invalid"
        (let [timeout (es-config/elastic-scroll-timeout)
              ;; Set the timeout to one second
              _ (es-config/set-elastic-scroll-timeout! "1s")
              {:keys [scroll-id] :as result} (search/find-refs 
                                              :granule 
                                              {:provider "PROV1" :scroll true :page-size 2})]   
          (testing "First call returns scroll-id and hits count with page-size results"
            (is (data2-core/refs-match? [gran1 gran2] result)))  
          ;; This is problematic. Can't use timekeeper tricks here since ES is the one enforcing 
          ;; the timeout, and it seems to have it's own scheule, so our session id does not time out
          ;; in exactly one second. 
          (Thread/sleep 100000)
          (testing "Subsequent calls get unknown scroll-id error"
            (let [response (search/find-refs :granule 
                                            {:scroll true}
                                            {:allow-failure? true
                                             :headers {routes/SCROLL_ID_HEADER scroll-id}})]
              (is (= 404 (:status response)))
              (is (= (str "Scroll session [" scroll-id "] does not exist")
                    (first (:errors response))))))
          (es-config/set-elastic-scroll-timeout! timeout)))

    (testing "disable scrolling"
      (dev-sys-util/eval-in-dev-sys
       `(cmr.common-app.services.search.parameter-validation/set-scrolling-enabled! false))
      (let [response (search/find-refs :granule 
                                       {:provider "PROV1" :scroll true}
                                       {:allow-failure? true})]
        (is (= 400 (:status response)))
        (is (= "Scrolling is disabled."
                (first (:errors response)))))
      (dev-sys-util/eval-in-dev-sys
       `(cmr.common-app.services.search.parameter-validation/set-scrolling-enabled! true)))

    (testing "invalid parameters"
      (are3 [query options status err-msg]
        (let [options (merge {:allow-failure? true})
              response (search/find-refs :granule query options)]
          (is (= status (:status response)))
          (is (= err-msg
                 (first (:errors response)))))

        "Scroll queries cannot be all-granule queries"
        {:scroll true}
        {}
        400
        (str "The CMR does not allow querying across granules in all collections when scrolling." 
             " You should limit your query using conditions that identify one or more collections "
             "such as provider, concept_id, short_name, or entry_title.")

        "scroll parameter must be boolean"
        {:provider "PROV1" :scroll "foo"} 
        {}
        400
        "Parameter scroll must take value of true or false but was [foo]"

        "page_num is not allowed with scrolling"
        {:provider "PROV1" :scroll true :page-num 2}
        {}
        400
        "page_num is not allowed with scrolling"
        
        "offset is not allowed with scrolling"
        {:provider "PROV1" :scroll true :offset 2}
        {}
        400
        "offset is not allowed with scrolling"

        "Unknown scroll-id"
        {:scroll true}
        {:headers {routes/SCROLL_ID_HEADER "foo"}}
        404
        "Scroll session [foo] does not exist"))))
