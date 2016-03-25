(ns cmr.system-int-test.bootstrap.rebalance-collections-test
  "Tests rebalancing granule indexes by moving collections's granules from the small collections
   index to separate collection indexes"
  (require [clojure.test :refer :all]
           [cmr.system-int-test.utils.metadata-db-util :as mdb]
           [cmr.system-int-test.utils.ingest-util :as ingest]
           [cmr.system-int-test.utils.search-util :as search]
           [cmr.system-int-test.utils.index-util :as index]
           [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
           [cmr.system-int-test.data2.collection :as dc]
           [cmr.system-int-test.data2.granule :as dg]
           [cmr.system-int-test.data2.core :as d]
           [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
           [cmr.system-int-test.system :as s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(comment
 ;; Use this to manually run the fixture
 ((ingest/reset-fixture {"provguid1" "PROV1"
                         "provguid2" "PROV2"})
  (constantly true)))

(deftest rebalance-collection-error-test
  (s/only-with-real-database
   (let [coll (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}))
         gran (d/ingest "PROV1" (dg/granule coll {:granule-ur "gran"}))]
     (index/wait-until-indexed)

     (testing "Non existent provider"
       (is (= {:status 400
               :errors ["Provider: [NON_EXIST] does not exist in the system"]}
              (bootstrap/start-rebalance-collection "C1-NON_EXIST" false))))
     (testing "Non existent collection"
       (is (= {:status 400
               :errors ["Collection [C1-PROV1] does not exist."]}
              (bootstrap/start-rebalance-collection "C1-PROV1" false))))
     (testing "Finalizing not started collection"
       (is (= {:status 400
               :errors [(str "The index set does not contain the rebalancing collection ["
                             (:concept-id coll)"]")]}
              (bootstrap/finalize-rebalance-collection (:concept-id coll)))))
     (testing "Starting already rebalancing collection"
       (bootstrap/start-rebalance-collection (:concept-id coll) true)
       (is (= {:status 400
               :errors [(str "The index set already contains rebalancing collection ["
                             (:concept-id coll)"]")]}
              (bootstrap/start-rebalance-collection (:concept-id coll) false))))
     (testing "Finalizing already finalized collection"
       (bootstrap/finalize-rebalance-collection (:concept-id coll))
       (is (= {:status 400
               :errors [(str "The index set does not contain the rebalancing collection ["
                             (:concept-id coll)"]")]}
              (bootstrap/finalize-rebalance-collection (:concept-id coll)))))
     (testing "Starting already rebalanced collection"
       (is (= {:status 400
               :errors [(str "The collection [" (:concept-id coll)
                             "] already has a separate granule index")]}
              (bootstrap/start-rebalance-collection (:concept-id coll) false)))))))

(defn count-by-params
  "Returns the number of granules found by the given params"
  [params]
  (let [response (search/find-refs :granule (assoc params :page-size 0))]
    (when (= 400 (:status response))
      (throw (Exception. (str "Search by params failed:" (pr-str response)))))
    (:hits response)))

(defn verify-provider-holdings
  "Verifies counts in the search application by searching several different ways for counts."
  [expected-provider-holdings message]
  ;; Verify search counts in provider holdings
  (is (= expected-provider-holdings (:results (search/provider-holdings-in-format :json))) message)

  ;; Verify search counts when searching individually by concept id
  (let [separate-holdings (for [coll-holding expected-provider-holdings]
                            (assoc coll-holding
                                   :granule-count
                                   (count-by-params {:concept-id (:concept-id coll-holding)})))]

    (is (= expected-provider-holdings separate-holdings)) message)
  ;; Verify search counts when searching by provider id
  (let [expected-counts-by-provider (reduce (fn [count-map {:keys [provider-id granule-count]}]
                                              (update count-map provider-id #(+ (or % 0) granule-count)))
                                            {}
                                            expected-provider-holdings)
        actual-counts-by-provider (into {} (for [provider-id (keys expected-counts-by-provider)]
                                             [provider-id (count-by-params {:provider-id provider-id})]))]
    (is (= expected-counts-by-provider actual-counts-by-provider) message)))

(defn assert-rebalance-status
  [expected-counts collection]
  (is (= (assoc expected-counts :status 200)
         (bootstrap/get-rebalance-status (:concept-id collection)))))

(defn ingest-granule-for-coll
  [coll n]
  (let [granule-ur (str (:entry-title coll) "_gran_" n)]
    (d/ingest (:provider-id coll) (dg/granule coll {:granule-ur granule-ur}))))

(defn inc-provider-holdings-for-coll
  "Updates the number of granules expected for a collection in the expected provider holdings"
  [expected-provider-holdings coll num]
  (for [coll-holding expected-provider-holdings]
    (if (= (:concept-id coll-holding) (:concept-id coll))
      (update coll-holding :granule-count + num)
      coll-holding)))

(deftest rebalance-collection-test
  (s/only-with-real-database
   (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}))
         coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"}))
         coll3 (d/ingest "PROV2" (dc/collection {:entry-title "coll3"}))
         coll4 (d/ingest "PROV2" (dc/collection {:entry-title "coll4"}))
         granules (doall (for [coll [coll1 coll2 coll3 coll4]
                               n (range 4)]
                           (ingest-granule-for-coll coll n)))

         expected-provider-holdings (for [coll [coll1 coll2 coll3 coll4]]
                                      (-> coll
                                          (select-keys [:provider-id :concept-id :entry-title])
                                          (assoc :granule-count 4)))]
     (index/wait-until-indexed)

     (assert-rebalance-status {:small-collections 4} coll1)
     (verify-provider-holdings expected-provider-holdings "Initial")

     ;; Start rebalancing of collection 1. After this it will be in small collections and a separate index
     (bootstrap/start-rebalance-collection (:concept-id coll1))
     (index/wait-until-indexed)

     ;; After rebalancing 4 granules are in small collections and in the new index.
     (assert-rebalance-status {:small-collections 4 :separate-index 4} coll1)
     ;; Search counts are correct before cache is cleared

     ;; This is currently failing.
     ;; This is the same problem as noted below with the "After finalize before clear cache"
     ;; Search is using the cached index set and doesn't know about the rebalancing collection and
     ;; therefore doesn't know to exclude it.
     ;; Fix during CMR-2668. The problem is described there.
     ; (verify-provider-holdings expected-provider-holdings "After start before clear cache")

     ;; Clear the search cache so it will get the last index set
     (search/clear-caches)
     (verify-provider-holdings expected-provider-holdings  "After start and after clear cache")

     ;; Index new data. It should go into both indexes
     (ingest-granule-for-coll coll1 4)
     (ingest-granule-for-coll coll1 5)
     (index/wait-until-indexed)
     (assert-rebalance-status {:small-collections 6 :separate-index 6} coll1)

     (let [expected-provider-holdings (inc-provider-holdings-for-coll
                                       expected-provider-holdings coll1 2)]
       (verify-provider-holdings expected-provider-holdings "After indexing more")

       ;; Finalize rebalancing
       (bootstrap/finalize-rebalance-collection (:concept-id coll1))
       (index/wait-until-indexed)

       ;; The granules have been removed from small collections
       (assert-rebalance-status {:small-collections 0 :separate-index 6} coll1)

       ;; Note that after finalize has run but before search has updated to use the new index set
       ;; it will find 0 granules for this collection. The job for refreshing that cache runs every
       ;; 5 minutes.
       ;; This check is here as a demonstration of the problem and not an assertion of what we want to happen.
       ;; Fix during CMR-2668
       (verify-provider-holdings
         (inc-provider-holdings-for-coll
          expected-provider-holdings coll1 -6)
         "After finalize before clear cache")
       ;; After the cache is cleared the right amount of data is found
       (search/clear-caches)
       (verify-provider-holdings
         expected-provider-holdings
         "After finalize after clear cache")))))


;; Tests rebalancing multiple collections at the same time.
(deftest rebalance-multiple-collections-test
  (s/only-with-real-database
   (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}))
         coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"}))
         coll3 (d/ingest "PROV2" (dc/collection {:entry-title "coll3"}))
         coll4 (d/ingest "PROV2" (dc/collection {:entry-title "coll4"}))
         granules (doall (for [coll [coll1 coll2 coll3 coll4]
                               n (range 4)]
                           (ingest-granule-for-coll coll n)))

         expected-provider-holdings (for [coll [coll1 coll2 coll3 coll4]]
                                      (-> coll
                                          (select-keys [:provider-id :concept-id :entry-title])
                                          (assoc :granule-count 4)))]
     (index/wait-until-indexed)
     ;; Start rebalancing of collection 1 and 2. After this it will be in small collections and a separate index
     (bootstrap/start-rebalance-collection (:concept-id coll1))
     (bootstrap/start-rebalance-collection (:concept-id coll2))
     (index/wait-until-indexed)

     ;; After rebalancing 4 granules are in small collections and in the new index.
     (assert-rebalance-status {:small-collections 4 :separate-index 4} coll1)
     (assert-rebalance-status {:small-collections 4 :separate-index 4} coll2)

     ;; Searches are correct before and after clearing the cache
     ;; Failing as noted in test above.
     ;; Fix during CMR-2668. The problem is described there.
     ; (verify-provider-holdings expected-provider-holdings)
     ;; Clear the search cache so it will get the last index set
     (search/clear-caches)
     (verify-provider-holdings expected-provider-holdings "After rebalance started after cache cleared")

     ;; Index new data. It should go into both indexes
     (ingest-granule-for-coll coll1 4)
     (ingest-granule-for-coll coll1 5)
     (ingest-granule-for-coll coll2 6)
     (ingest-granule-for-coll coll2 7)
     (ingest-granule-for-coll coll2 8)
     (index/wait-until-indexed)
     (assert-rebalance-status {:small-collections 6 :separate-index 6} coll1)
     (assert-rebalance-status {:small-collections 7 :separate-index 7} coll2)

     (let [expected-provider-holdings (-> expected-provider-holdings
                                          (inc-provider-holdings-for-coll coll1 2)
                                          (inc-provider-holdings-for-coll coll2 3))]
       (verify-provider-holdings expected-provider-holdings "After indexing more granules")

       ;; Finalize rebalancing
       (bootstrap/finalize-rebalance-collection (:concept-id coll1))
       (bootstrap/finalize-rebalance-collection (:concept-id coll2))
       (index/wait-until-indexed)

       ;; The granules have been removed from small collections
       (assert-rebalance-status {:small-collections 0 :separate-index 6} coll1)
       (assert-rebalance-status {:small-collections 0 :separate-index 7} coll2)

       ;; After the cache is cleared the right amount of data is found
       (search/clear-caches)
       (verify-provider-holdings expected-provider-holdings "After finalizing")))))

(deftest rebalance-collection-after-other-collections-test
  (s/only-with-real-database
   (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}))
         coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"}))
         coll3 (d/ingest "PROV2" (dc/collection {:entry-title "coll3"}))
         coll4 (d/ingest "PROV2" (dc/collection {:entry-title "coll4"}))
         granules (doall (for [coll [coll1 coll2 coll3 coll4]
                               n (range 4)]
                           (ingest-granule-for-coll coll n)))

         expected-provider-holdings (for [coll [coll1 coll2 coll3 coll4]]
                                      (-> coll
                                          (select-keys [:provider-id :concept-id :entry-title])
                                          (assoc :granule-count 4)))]
     (index/wait-until-indexed)

     ;; Split out coll1
     (bootstrap/start-rebalance-collection (:concept-id coll1))
     (index/wait-until-indexed)
     (bootstrap/finalize-rebalance-collection (:concept-id coll1))
     (index/wait-until-indexed)


     ;; Start rebalancing of collection 3. After this it will be in small collections and a separate index
     (bootstrap/start-rebalance-collection (:concept-id coll3))
     (index/wait-until-indexed)

     ;; After rebalancing 4 granules are in small collections and in the new index.
     (assert-rebalance-status {:small-collections 4 :separate-index 4} coll3)
     ;; Clear the search cache so it will get the last index set
     (search/clear-caches)
     (verify-provider-holdings expected-provider-holdings  "After start and after clear cache")

     ;; Index new data. It should go into both indexes
     (ingest-granule-for-coll coll3 4)
     (ingest-granule-for-coll coll3 5)
     (index/wait-until-indexed)
     (assert-rebalance-status {:small-collections 6 :separate-index 6} coll3)

     (let [expected-provider-holdings (inc-provider-holdings-for-coll
                                       expected-provider-holdings coll3 2)]
       (verify-provider-holdings expected-provider-holdings "After indexing more")

       ;; Finalize rebalancing
       (bootstrap/finalize-rebalance-collection (:concept-id coll3))
       (index/wait-until-indexed)

       ;; The granules have been removed from small collections
       (assert-rebalance-status {:small-collections 0 :separate-index 6} coll3)
       ;; After the cache is cleared the right amount of data is found
       (search/clear-caches)
       (verify-provider-holdings
         expected-provider-holdings
         "After finalize after clear cache")))))