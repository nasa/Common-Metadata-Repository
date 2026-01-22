
(ns cmr.system-int-test.search.granule.granule-counts-cache-integration-test
  (:require
   [clojure.test :refer :all]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.granule-counts :as gc]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"}))

(defn- create-collection
  ([n]
   (create-collection n "PROV1"))
  ([n provider-id]
   (let [collection (dc/collection {:entry-title (str "coll" n)})
         {:keys [concept-id] :as response} (ingest/ingest-concept (dc/collection-concept provider-id collection))]
     (assoc collection :concept-id concept-id))))

(defn- create-collections [provider-id count]
  (doall
   (for [n (range count)]
     (create-collection n provider-id))))

(defn- create-granule
  ([coll n]
   (create-granule coll n "PROV1"))
  ([coll n provider-id]
   (let [granule (dg/granule coll {:granule-ur (str "gran" n)})
         concept (dg/granule-concept provider-id granule)]
     (ingest/ingest-concept concept))))

(defn- refresh-cache []
  (dev-sys-util/eval-in-dev-sys `(cmr.search.data.granule-counts-cache/refresh-granule-counts-cache)))

(defn- get-cache-counts []
  (dev-sys-util/eval-in-dev-sys `(cmr.search.data.granule-counts-cache/get-granule-counts)))

(deftest ^:integration granule-counts-cache-test
  (let [coll1 (create-collection 1)
        coll2 (create-collection 2)
        coll3 (create-collection 3 "PROV2")]

    (testing "Initial cache state"
      (is (empty? (get-cache-counts)) "Cache should be empty initially"))

    (testing "Cache population"
      (dotimes [n 2] (create-granule coll1 n))
      (create-granule coll2 0)
      (index/wait-until-indexed)
      (refresh-cache)
      (let [counts (get-cache-counts)]
        (is (= 3 (count counts)))
        (is (= 2 (get counts (:concept-id coll1))))
        (is (= 1 (get counts (:concept-id coll2))))
        (is (= 0 (get counts (:concept-id coll3))))))

    (testing "Cache retrieval via search"
      (let [results (search/find-refs :collection {:include-granule-counts true})]
        (is (gc/granule-counts-match? :xml {coll1 2 coll2 1 coll3 0} results))))

    (testing "Cache update"
      (create-granule coll3 0 "PROV2")
      (index/wait-until-indexed)
      (refresh-cache)
      (let [counts (get-cache-counts)]
        (is (= 1 (get counts (:concept-id coll3))))))

    (testing "Cache with search parameters"
      (let [results (search/find-refs :collection 
                                      {:include-granule-counts true
                                       :provider "PROV1"})]
        (is (gc/granule-counts-match? :xml {coll1 2 coll2 1} results))))

    (testing "Has-granules functionality"
      (let [results (search/find-refs :collection {:has-granules true})]
        (is (= 3 (count (:refs results))))
        (is (every? #(contains? #{(:concept-id coll1) (:concept-id coll2) (:concept-id coll3)}
                                (:id %))
                    (:refs results)))))

    (testing "Cache consistency"
      (let [cache-counts (get-cache-counts)
            search-counts (gc/results->actual-granule-count 
                            :echo10 
                            (search/find-metadata :collection 
                                                  :echo10 
                                                  {:include-granule-counts true}))]
        (is (= cache-counts search-counts))))

    (testing "Different result formats"
      (doseq [format [:echo10 :iso19115 :umm-json]]
        (let [results (search/find-metadata :collection format {:include-granule-counts true})]
          (is (gc/granule-counts-match? format {coll1 2 coll2 1 coll3 1} results)))))

    (testing "Error handling"
      (is (thrown? Exception (search/find-refs :collection {:include-granule-counts "invalid"}))))

    (testing "Cache performance under load"
      (let [num-collections 100
            collections (doall (for [i (range num-collections)]
                                 (create-collection (+ i 10))))
            _ (index/wait-until-indexed)
            start-time (System/currentTimeMillis)
            _ (refresh-cache)
            end-time (System/currentTimeMillis)
            elapsed-time (- end-time start-time)]
        (println "Cache refresh time for" num-collections "collections:" elapsed-time "ms")
        (is (< elapsed-time 10000) "Cache refresh should complete in less than 10 seconds for 100 collections")))

    (testing "Cache behavior with multiple providers"
      (let [coll-prov3 (create-collection 4 "PROV3")
            _ (create-granule coll-prov3 0 "PROV3")
            _ (index/wait-until-indexed)
            _ (refresh-cache)
            counts (get-cache-counts)]
        (is (= 1 (get counts (:concept-id coll-prov3))))
        (is (= 4 (count counts)))))

    (testing "Cache behavior after deleting granules"
      (let [granule-to-delete (create-granule coll1 100)]
        (ingest/delete-concept (assoc granule-to-delete :concept-type :granule))
        (index/wait-until-indexed)
        (refresh-cache)
        (let [counts (get-cache-counts)]
          (is (= 2 (get counts (:concept-id coll1)))))))

    (testing "Cache behavior after deleting a collection"
      (let [coll-to-delete (create-collection 6)
            _ (create-granule coll-to-delete 0)
            _ (index/wait-until-indexed)
            _ (refresh-cache)
            before-delete-counts (get-cache-counts)]
        (is (contains? before-delete-counts (:concept-id coll-to-delete)))
        (ingest/delete-concept (assoc coll-to-delete :concept-type :collection))
        (index/wait-until-indexed)
        (refresh-cache)
        (let [after-delete-counts (get-cache-counts)]
          (is (not (contains? after-delete-counts (:concept-id coll-to-delete)))))))

    (testing "Cache behavior with a large number of granules in a single collection"
      (let [large-coll (create-collection 7)
            granule-count 1000]
        (dotimes [n granule-count]
          (create-granule large-coll n))
        (index/wait-until-indexed)
        (refresh-cache)
        (let [counts (get-cache-counts)]
          (is (= granule-count (get counts (:concept-id large-coll)))))))

    (testing "Cache handles non-existent provider"
      (is (empty? (dev-sys-util/eval-in-dev-sys `(cmr.search.data.granule-counts-cache/get-granule-counts ["NON_EXISTENT_PROV"])))))

    (testing "Cache refresh with custom function"
      (dev-sys-util/eval-in-dev-sys `(cmr.search.data.granule-counts-cache/refresh-granule-counts-cache #(hash-map "TEST1" 100 "TEST2" 200)))
      (let [counts (get-cache-counts)]
        (is (= 2 (count counts)))
        (is (= 100 (get counts "TEST1")))
        (is (= 200 (get counts "TEST2")))))

    (testing "Get-has-granules-map functionality"
      (let [has-granules-map (dev-sys-util/eval-in-dev-sys `(cmr.search.data.granule-counts-cache/get-has-granules-map))]
        (is (true? (get has-granules-map (:concept-id coll1))))
        (is (true? (get has-granules-map (:concept-id coll2))))
        (is (true? (get has-granules-map (:concept-id coll3))))
        (is (false? (get has-granules-map (:concept-id (create-collection 5)))))))))
