(ns cmr.system-int-test.search.granule.granule-counts-cache-test
  "Integration tests for the granule counts cache mechanism (PR #2329)."
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
   [cmr.transmit.config :as transmit-config]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn refresh-granule-counts-cache
  []
  (let [url (url/bootstrap-url "caches/refresh/granule-counts")]
    (try
      (client/post url {:headers {transmit-config/token-header (transmit-config/echo-system-token)}
                        :throw-exceptions true})
      (catch java.net.ConnectException e
        (println "WARNING: Could not connect to Bootstrap to refresh cache. Is Bootstrap running?" 
                 "Tests might fail if cache is stale.")
        (throw e)))))

(deftest provider-holdings-using-cache-test
  (testing "Provider Holdings API relies on the granule counts cache"
    (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}))
          coll2 (d/ingest "PROV2" (dc/collection {:entry-title "coll2"}))
          _ (d/ingest "PROV1" (dg/granule coll1))
          _ (index/wait-until-indexed)]

      (testing "Initial State"
        (let [holdings (search/get-provider-holdings-in-format :json)]
          (is (= 1 (some #(when (= (:entry-title %) "coll1") (:granule-count %)) holdings)))
          (is (= 0 (some #(when (= (:entry-title %) "coll2") (:granule-count %)) holdings)))))

      (testing "Provider Isolation"
         (let [holdings (search/get-provider-holdings-in-format :json {:provider-id "PROV1"})]
           (is (= 1 (count holdings)))
           (is (= "PROV1" (:provider-id (first holdings))))
           (is (not-any? #(= "PROV2" (:provider-id %)) holdings))))

      (testing "Cache Refresh (Addition)"
        (d/ingest "PROV2" (dg/granule coll2))
        (index/wait-until-indexed)
        (refresh-granule-counts-cache)
        (let [holdings (search/get-provider-holdings-in-format :json)]
          (is (= 1 (some #(when (= (:entry-title %) "coll2") (:granule-count %)) holdings)))))

      (testing "Cache Refresh (Deletion)"
        (let [gran (first (:refs (search/find-refs :granule {:collection-concept-id (:concept-id coll2)})))]
          (ingest/delete-concept (assoc gran :concept-type :granule :provider-id "PROV2"))
          (index/wait-until-indexed)
          (refresh-granule-counts-cache)
          (let [holdings (search/get-provider-holdings-in-format :json)]
            (is (= 0 (some #(when (= (:entry-title %) "coll2") (:granule-count %)) holdings)))))))))

(deftest has-granules-flags-usage-test
  (testing "Search Parameters utilizing the granule counts cache"
    (let [coll-with-gran (d/ingest "PROV1" (dc/collection {:entry-title "c-has-granules"}))
          coll-no-gran (d/ingest "PROV1" (dc/collection {:entry-title "c-no-granules"}))
          _ (d/ingest "PROV1" (dg/granule coll-with-gran))
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