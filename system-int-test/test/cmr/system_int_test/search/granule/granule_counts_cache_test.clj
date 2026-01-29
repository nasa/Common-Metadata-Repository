(ns cmr.system-int-test.search.granule.granule-counts-cache-test
  "Integration tests for the granule counts cache."
  (:require
   [clj-http.client :as client]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.transmit.config :as transmit-config]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn refresh-granule-counts-cache
  []
  (let [url (url/bootstrap-url "caches/refresh/granule-counts-cache")]
    (try
      (let [response (client/post url {:headers {transmit-config/token-header (transmit-config/echo-system-token)}
                                       :throw-exceptions false})]
        (if (= 200 (:status response))
          true
          (throw (Exception. (str "Failed to refresh cache. Status: " (:status response))))))
      (catch Exception e
        (throw e)))))

(deftest provider-holdings-using-cache-test
  (testing "Provider Holdings API relies on the granule counts cache"
    (e/grant-all (s/context) (e/coll-catalog-item-id "PROV1"))
    (e/grant-all (s/context) (e/coll-catalog-item-id "PROV2"))
    (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}) {:token "mock-echo-system-token" :validate-keywords false})
          coll2 (d/ingest "PROV2" (dc/collection {:entry-title "coll2"}) {:token "mock-echo-system-token" :validate-keywords false})
          _ (d/ingest "PROV1" (dg/granule coll1) {:token "mock-echo-system-token" :validate-keywords false})
          _ (index/wait-until-indexed)
          _ (search/clear-caches)]
      (testing "Initial State"
        (let [holdings (:results (search/provider-holdings-in-format :json))]
          (is (= 1 (:granule-count (first (filter #(= (:entry-title %) "coll1") holdings)))))
          (is (= 0 (:granule-count (first (filter #(= (:entry-title %) "coll2") holdings)))))))
      (testing "Provider Isolation"
         (let [holdings (:results (search/provider-holdings-in-format :json {:token "mock-echo-system-token" :provider-id "PROV1"}))]
           (is (= 1 (count holdings)))
           (is (= "PROV1" (:provider-id (first holdings))))
           (is (not-any? #(= "PROV2" (:provider-id %)) holdings))))
      (testing "Cache Refresh (Addition)"
        (d/ingest "PROV2" (dg/granule coll2) {:token "mock-echo-system-token" :validate-keywords false})
        (index/wait-until-indexed)
        (refresh-granule-counts-cache)
        (Thread/sleep 2000)  
        (let [holdings (:results (search/provider-holdings-in-format :json {:token "mock-echo-system-token"}))]
          (is (= 1 (:granule-count (first (filter #(= (:entry-title %) "coll2") holdings)))))))
      (testing "Cache Refresh (Deletion)"
        (let [gran (first (:refs (search/find-refs :granule {:collection-concept-id (:concept-id coll2)})))]
          (ingest/delete-concept {:concept-type :granule :provider-id "PROV2" 
                                  :native-id (:name gran)} {:token "mock-echo-system-token" :validate-keywords false})
          (index/wait-until-indexed)
          (refresh-granule-counts-cache)
          (Thread/sleep 2000)  
          (let [holdings (:results (search/provider-holdings-in-format :json {:token "mock-echo-system-token"}))]
            (is (= 0 (:granule-count (first (filter #(= (:entry-title %) "coll2") holdings)))))))))))

(deftest has-granules-flags-usage-test
  (testing "Search Parameters utilizing the granule counts cache"
    (let [coll-with-gran (d/ingest "PROV1" (dc/collection {:entry-title "c-has-granules"}) {:token "mock-echo-system-token" :validate-keywords false})
          coll-no-gran (d/ingest "PROV1" (dc/collection {:entry-title "c-no-granules"}) {:token "mock-echo-system-token" :validate-keywords false})
          _ (d/ingest "PROV1" (dg/granule coll-with-gran) {:token "mock-echo-system-token" :validate-keywords false})
          _ (index/wait-until-indexed)]
      
      (testing "has_granules"
        (is (d/refs-match? [coll-with-gran] (search/find-refs :collection {:has_granules true})))
        (is (d/refs-match? [coll-no-gran] (search/find-refs :collection {:has_granules false}))))

      (testing "has_granules_or_cwic"
        (let [refs (search/find-refs :collection {:has_granules_or_cwic true})]
          (is (some #(= (:id %) (:concept-id coll-with-gran)) (:refs refs)))))

      (testing "has_granules_or_opensearch"
        (let [refs (search/find-refs :collection {:has_granules_or_opensearch true})]
          (is (some #(= (:id %) (:concept-id coll-with-gran)) (:refs refs))))))))

