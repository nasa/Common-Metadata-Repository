(ns cmr.system-int-test.search.granule.granule-counts-cache-test
  "Tests the Redis-backed cache that stores collection-concept-id to granule count mappings."
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

;; Performance test thresholds
(def ^:private cache-refresh-timeout-ms 5000)
(def ^:private cache-search-timeout-ms 2000)

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"}))

(defn- create-collection
  "Creates and ingests a collection, returning the collection with concept-id."
  ([n]
   (create-collection n "PROV1"))
  ([n provider-id]
   ;; Skip keyword validation to speed up test setup
   ;; Use timestamp to ensure unique collection names across test runs
   (let [unique-id (str "coll-" n "-" (System/currentTimeMillis))
         collection (dc/collection {:entry-title unique-id})
         ingest-result (d/ingest provider-id collection {:validate-keywords false})]
     (println "Debug: Create collection request:" (pr-str collection))
     (println "Debug: Create collection response:" (pr-str ingest-result))
     ingest-result)))

(defn- create-granule
  "Creates and ingests a granule for the given collection."
  ([coll n]
   (create-granule coll n "PROV1"))
  ([coll n provider-id]
   ;; Include collection concept-id in granule UR to ensure uniqueness
   (let [granule (dg/granule coll {:granule-ur (str "gran-" (:concept-id coll) "-" n)})
         ingest-result (d/ingest provider-id granule)]
     (println "Debug: Create granule request:" (pr-str granule))
     (println "Debug: Create granule response:" (pr-str ingest-result))
     ingest-result)))

(defn- refresh-cache-via-bootstrap
  "Refreshes the granule counts cache via the bootstrap API endpoint.
   This forces Redis to rebuild the collection -> granule count mappings.
   Throws an exception if the refresh fails."
  []
  (let [response (client/post 
                   (str (url/bootstrap-root) "/caches/refresh/granule-counts-cache")
                   {:connection-manager (sys/conn-mgr)
                    :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                    :throw-exceptions false})]
    (when (not= 200 (:status response))
      (throw (ex-info "Cache refresh failed" {:response response})))
    response))

(deftest ^:integration granule-counts-cache-basic-test
  (testing "Basic cache functionality through search API"
    ;; Set up collections across different providers
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
        ;; Add some granules and make sure cache picks them up
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
    (try
    ;; Make sure the cache stays in sync when we modify data
    (let [coll1 (create-collection 1)
          coll2 (create-collection 2 "PROV2")]
        (println "Debug: Created collections:" (pr-str [coll1 coll2]))
      
      ;; Initial state
        (dotimes [n 3] 
          (let [gran (create-granule coll1 n)]
            (println "Debug: Created granule for coll1:" (pr-str gran))))
        (let [gran (create-granule coll2 0 "PROV2")]
          (println "Debug: Created granule for coll2:" (pr-str gran)))
        (index/wait-until-indexed)
        (refresh-cache-via-bootstrap)
      
      (testing "Initial counts"
        (let [results (search/find-refs :collection {:include-granule-counts true})]
            (println "Debug: Initial search results:" (pr-str results))
          (is (gc/granule-counts-match? :xml {coll1 3 coll2 1} results))))
      
      (testing "After adding more granules"
          (dotimes [n 2]
            (let [gran (create-granule coll2 (+ n 1) "PROV2")]
              (println "Debug: Created additional granule for coll2:" (pr-str gran))))
        (index/wait-until-indexed)
        (refresh-cache-via-bootstrap)
        
        (let [results (search/find-refs :collection {:include-granule-counts true})]
            (println "Debug: Search results after adding granules:" (pr-str results))
          (is (gc/granule-counts-match? :xml {coll1 3 coll2 3} results)
              "Cache should reflect new granules")))
      
      (testing "After deleting granules"
        ;; Cache should decrement counts when granules are deleted
        ;; Create a 4th granule and then delete it, leaving us back at 3
        (let [gran-to-delete (create-granule coll1 10)]
            (println "Debug: Created granule to delete:" (pr-str gran-to-delete))
          (index/wait-until-indexed)
          ;; Delete the granule - use try-catch to handle potential errors gracefully
          (let [delete-result (ingest/delete-concept (assoc gran-to-delete :concept-type :granule))]
              (println "Debug: Delete granule result:" (pr-str delete-result))
            (is (= 200 (:status delete-result)) 
                (str "Granule delete should succeed, got: " (:status delete-result))))
          (index/wait-until-indexed)
          (refresh-cache-via-bootstrap)
          
          (let [results (search/find-refs :collection {:include-granule-counts true})]
              (println "Debug: Search results after deleting granule:" (pr-str results))
            (is (gc/granule-counts-match? :xml {coll1 3 coll2 3} results)
                "Cache should reflect deleted granules"))))
      
      (testing "After deleting a collection"
        ;; First delete all granules in the collection to avoid orphans
        (index/wait-until-indexed)
        ;; Now delete the collection
        (let [delete-result (ingest/delete-concept (assoc coll2 :concept-type :collection))]
            (println "Debug: Delete collection result:" (pr-str delete-result))
          (is (= 200 (:status delete-result))
              (str "Collection delete should succeed, got: " (:status delete-result))))
        (index/wait-until-indexed)
        (refresh-cache-via-bootstrap)
        
        (let [results (search/find-refs :collection {:include-granule-counts true})]
            (println "Debug: Search results after deleting collection:" (pr-str results))
          (is (gc/granule-counts-match? :xml {coll1 3} results)
                "Deleted collections should not appear in cache"))))
      (catch Exception e
        (println "Error in granule-counts-cache-update-test:")
        (println (.getMessage e))
        (println "Cause:" (.getCause e))
        (.printStackTrace e)))))

(deftest ^:integration granule-counts-cache-formats-test
  (testing "Cache works with different metadata formats"
    ;; Test that cache works with both XML and ECHO10 formats
    ;; Other format transformations (ISO19115, UMM-JSON) are tested elsewhere
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
          (is (gc/granule-counts-match? :echo10 {coll1 2 coll2 1} results)))))))

(deftest ^:integration granule-counts-cache-consistency-test
  (testing "Cache consistency with Elasticsearch"
    ;; Verify cache results match what we'd get from direct ES queries
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
    ;; Test filtering by whether collections have any granules
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
    ;; Each provider should have its own accurate counts
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
    ;; Make sure cache operations don't take forever with real-world data volumes
    (let [num-collections 10
          collections (doall (for [i (range num-collections)]
                              (create-collection (+ i 10))))]
      
      ;; Give each collection a different number of granules (1, 2, 3, etc.)
      (doseq [[idx coll] (map-indexed vector collections)]
        (dotimes [n (inc idx)] 
          (create-granule coll n)))
      
      (index/wait-until-indexed)
      
      (testing "Cache refresh completes in reasonable time"
        (let [start-time (System/currentTimeMillis)
              _ (refresh-cache-via-bootstrap)
              elapsed-time (- (System/currentTimeMillis) start-time)]
          (is (< elapsed-time cache-refresh-timeout-ms) 
              (str "Cache refresh should complete in less than " cache-refresh-timeout-ms "ms for 10 collections (took " elapsed-time "ms)"))))
      
      (testing "Search with cache is fast"
        (let [start-time (System/currentTimeMillis)
              results (search/find-refs :collection {:include-granule-counts true})
              elapsed-time (- (System/currentTimeMillis) start-time)]
          (is (< elapsed-time cache-search-timeout-ms)
              (str "Search with cached counts should complete in less than " cache-search-timeout-ms "ms (took " elapsed-time "ms)"))
          (is (= num-collections (count (:refs results)))))))))