(ns cmr.system-int-test.search.granule.granule-counts-cache-integration-test
  "Integration tests for the granule counts cache feature (CMR-10838).
   Tests the Redis-backed cache that stores collection-concept-id to granule count mappings."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.granule-counts :as gc]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.transmit.config :as transmit-config]
   [clj-http.client :as client]
   [cmr.system-int-test.system :as sys]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"}))

(defn- create-collection
  "Creates and ingests a collection, returning the collection with concept-id."
  ([n]
   (create-collection n "PROV1"))
  ([n provider-id]
   (d/ingest provider-id 
             (dc/collection {:entry-title (str "coll" n)})
             {:validate-keywords false})))

(defn- create-granule
  "Creates and ingests a granule for the given collection."
  ([coll n]
   (create-granule coll n "PROV1"))
  ([coll n provider-id]
   (d/ingest provider-id 
             (dg/granule coll {:granule-ur (str "gran" n)}))))

(defn- refresh-cache-via-bootstrap
  "Refreshes the granule counts cache via the bootstrap API endpoint."
  []
  (let [response (client/post 
                   (str (url/bootstrap-root) "/caches/refresh/granule-counts-cache")
                   {:connection-manager (sys/conn-mgr)
                    :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                    :throw-exceptions false})]
    (is (= 200 (:status response)) "Cache refresh should succeed")
    response))

(deftest ^:integration granule-counts-cache-basic-test
  (testing "Basic cache functionality through search API"
    (let [coll1 (create-collection 1)
          coll2 (create-collection 2)
          coll3 (create-collection 3 "PROV2")]
      
      (testing "Collections with no granules"
        (index/wait-until-indexed)
        (refresh-cache-via-bootstrap)
        (let [results (search/find-refs :collection {:include-granule-counts true})]
          (is (gc/granule-counts-match? :xml {coll1 0 coll2 0 coll3 0} results)
              "Collections should have 0 granules initially")))
      
      (testing "After adding granules"
        (dotimes [n 2] (create-granule coll1 n))
        (create-granule coll2 0)
        (index/wait-until-indexed)
        (refresh-cache-via-bootstrap)
        
        (let [results (search/find-refs :collection {:include-granule-counts true})]
          (is (gc/granule-counts-match? :xml {coll1 2 coll2 1 coll3 0} results)
              "Cache should reflect updated granule counts")))
      
      (testing "Cache works with provider filtering"
        (let [results (search/find-refs :collection 
                                        {:include-granule-counts true
                                         :provider "PROV1"})]
          (is (gc/granule-counts-match? :xml {coll1 2 coll2 1} results)
              "Provider filtering should work with cached counts"))))))

(deftest ^:integration granule-counts-cache-update-test
  (testing "Cache updates correctly after data changes"
    (let [coll1 (create-collection 1)
          coll2 (create-collection 2 "PROV2")]
      
      ;; Initial state
      (dotimes [n 3] (create-granule coll1 n))
      (create-granule coll2 0 "PROV2")
      (index/wait-until-indexed)
      (refresh-cache-via-bootstrap)
      
      (testing "Initial counts"
        (let [results (search/find-refs :collection {:include-granule-counts true})]
          (is (gc/granule-counts-match? :xml {coll1 3 coll2 1} results))))
      
      (testing "After adding more granules"
        (dotimes [n 2] (create-granule coll2 (+ n 1) "PROV2"))
        (index/wait-until-indexed)
        (refresh-cache-via-bootstrap)
        
        (let [results (search/find-refs :collection {:include-granule-counts true})]
          (is (gc/granule-counts-match? :xml {coll1 3 coll2 3} results)
              "Cache should reflect new granules")))
      
      (testing "After deleting granules"
        (let [gran-to-delete (create-granule coll1 10)]
          (ingest/delete-concept (assoc gran-to-delete :concept-type :granule))
          (index/wait-until-indexed)
          (refresh-cache-via-bootstrap)
          
          (let [results (search/find-refs :collection {:include-granule-counts true})]
            (is (gc/granule-counts-match? :xml {coll1 3 coll2 3} results)
                "Cache should reflect deleted granules"))))
      
      (testing "After deleting a collection"
        (ingest/delete-concept (assoc coll2 :concept-type :collection))
        (index/wait-until-indexed)
        (refresh-cache-via-bootstrap)
        
        (let [results (search/find-refs :collection {:include-granule-counts true})]
          (is (gc/granule-counts-match? :xml {coll1 3} results)
              "Deleted collections should not appear in cache"))))))

(deftest ^:integration granule-counts-cache-formats-test
  (testing "Cache works with different metadata formats"
    (let [coll1 (create-collection 1)
          coll2 (create-collection 2)]
      
      (dotimes [n 2] (create-granule coll1 n))
      (create-granule coll2 0)
      (index/wait-until-indexed)
      (refresh-cache-via-bootstrap)
      
      (testing "XML format"
        (let [results (search/find-refs :collection {:include-granule-counts true})]
          (is (gc/granule-counts-match? :xml {coll1 2 coll2 1} results))))
      
      (testing "ECHO10 format"
        (let [results (search/find-metadata :collection :echo10 
                                           {:include-granule-counts true})]
          (is (gc/granule-counts-match? :echo10 {coll1 2 coll2 1} results))))
      
      (testing "ISO19115 format"
        (let [results (search/find-metadata :collection :iso19115 
                                           {:include-granule-counts true})]
          (is (gc/granule-counts-match? :iso19115 {coll1 2 coll2 1} results))))
      
      (testing "UMM-JSON format"
        (let [results (search/find-metadata :collection :umm-json 
                                           {:include-granule-counts true})]
          (is (gc/granule-counts-match? :umm-json {coll1 2 coll2 1} results)))))))

(deftest ^:integration granule-counts-cache-consistency-test
  (testing "Cache consistency with Elasticsearch"
    (let [coll1 (create-collection 1)
          coll2 (create-collection 2)
          coll3 (create-collection 3)]
      
      (dotimes [n 5] (create-granule coll1 n))
      (dotimes [n 3] (create-granule coll2 n))
      (create-granule coll3 0)
      (index/wait-until-indexed)
      (refresh-cache-via-bootstrap)
      
      (testing "Cached counts match search results"
        (let [cache-results (search/find-refs :collection {:include-granule-counts true})
              cache-counts (gc/results->actual-granule-count :xml cache-results)
              
              ;; Get counts via direct metadata search (which uses cache)
              metadata-results (search/find-metadata :collection :echo10 
                                                    {:include-granule-counts true})
              metadata-counts (gc/results->actual-granule-count :echo10 metadata-results)]
          
          (is (= 5 (get cache-counts (:concept-id coll1))))
          (is (= 3 (get cache-counts (:concept-id coll2))))
          (is (= 1 (get cache-counts (:concept-id coll3))))
          (is (= cache-counts metadata-counts)
              "Counts should be consistent across different search methods"))))))

(deftest ^:integration granule-counts-cache-has-granules-test
  (testing "has-granules parameter works with cache"
    (let [coll-with-grans (create-collection 1)
          coll-without-grans (create-collection 2)]
      
      (dotimes [n 3] (create-granule coll-with-grans n))
      (index/wait-until-indexed)
      (refresh-cache-via-bootstrap)
      
      (testing "Search with has-granules=true"
        (let [results (search/find-refs :collection {:has-granules true})]
          (is (= 1 (count (:refs results))))
          (is (= (:concept-id coll-with-grans) 
                 (:id (first (:refs results)))))))
      
      (testing "Search with has-granules=false"
        (let [results (search/find-refs :collection {:has-granules false})]
          (is (= 1 (count (:refs results))))
          (is (= (:concept-id coll-without-grans) 
                 (:id (first (:refs results))))))))))

(deftest ^:integration granule-counts-cache-multiple-providers-test
  (testing "Cache correctly handles multiple providers"
    (let [coll-prov1 (create-collection 1 "PROV1")
          coll-prov2 (create-collection 2 "PROV2")
          coll-prov3 (create-collection 3 "PROV3")]
      
      (dotimes [n 2] (create-granule coll-prov1 n "PROV1"))
      (dotimes [n 3] (create-granule coll-prov2 n "PROV2"))
      (dotimes [n 5] (create-granule coll-prov3 n "PROV3"))
      (index/wait-until-indexed)
      (refresh-cache-via-bootstrap)
      
      (testing "All providers"
        (let [results (search/find-refs :collection {:include-granule-counts true})]
          (is (gc/granule-counts-match? :xml 
                                        {coll-prov1 2 coll-prov2 3 coll-prov3 5} 
                                        results))))
      
      (testing "Filter by PROV1"
        (let [results (search/find-refs :collection 
                                        {:include-granule-counts true
                                         :provider "PROV1"})]
          (is (gc/granule-counts-match? :xml {coll-prov1 2} results))))
      
      (testing "Filter by PROV2"
        (let [results (search/find-refs :collection 
                                        {:include-granule-counts true
                                         :provider "PROV2"})]
          (is (gc/granule-counts-match? :xml {coll-prov2 3} results)))))))

(deftest ^:integration granule-counts-cache-error-handling-test
  (testing "Error handling for invalid parameters"
    (testing "Invalid include-granule-counts value"
      (let [response (search/find-refs :collection {:include-granule-counts "invalid"})]
        (is (= 400 (:status response)))
        (is (some #(re-find #"include_granule_counts" %) (:errors response)))))
    
    (testing "include-granule-counts not allowed on granule search"
      (let [response (search/find-refs :granule {:include-granule-counts true})]
        (is (= 400 (:status response)))
        (is (some #(re-find #"include_granule_counts.*not recognized" %) (:errors response)))))))

(deftest ^:integration granule-counts-cache-performance-test
  (testing "Cache performance with moderate dataset"
    (let [num-collections 10
          collections (doall (for [i (range num-collections)]
                              (create-collection (+ i 10))))]
      
      ;; Add varying numbers of granules to each collection
      (doseq [[idx coll] (map-indexed vector collections)]
        (dotimes [n (inc idx)] 
          (create-granule coll n)))
      
      (index/wait-until-indexed)
      
      (testing "Cache refresh completes in reasonable time"
        (let [start-time (System/currentTimeMillis)
              _ (refresh-cache-via-bootstrap)
              elapsed-time (- (System/currentTimeMillis) start-time)]
          (println "Cache refresh time for" num-collections "collections:" elapsed-time "ms")
          (is (< elapsed-time 5000) 
              "Cache refresh should complete in less than 5 seconds for 10 collections")))
      
      (testing "Search with cache is fast"
        (let [start-time (System/currentTimeMillis)
              results (search/find-refs :collection {:include-granule-counts true})
              elapsed-time (- (System/currentTimeMillis) start-time)]
          (println "Search with granule counts time:" elapsed-time "ms")
          (is (< elapsed-time 2000)
              "Search with cached counts should complete in less than 2 seconds")
          (is (= num-collections (count (:refs results)))))))))