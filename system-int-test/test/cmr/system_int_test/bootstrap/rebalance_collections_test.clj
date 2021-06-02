(ns cmr.system-int-test.bootstrap.rebalance-collections-test
  "Tests rebalancing granule indexes by moving collections's granules from the small collections
   index to separate collection indexes"
  (:require
   [clojure.test :refer :all]
   [clj-http.client :as client]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.time-keeper :as tk]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.system :as s]
   [cmr.metadata-db.services.concept-service :as concept-service]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(comment
 ;; Use this to manually run the fixture
 ((ingest/reset-fixture {"provguid1" "PROV1"
                         "provguid2" "PROV2"})
  (constantly true)))

(deftest ^:oracle rebalance-collection-error-test
  (s/only-with-real-database
   (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}))
         gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran1"}))
         coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"}))
         gran2 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "gran2"}))]
     (index/wait-until-indexed)
     (testing "no permission for start-rebalance-collection"
       (is (= {:status 401
               :errors ["You do not have permission to perform that action."]}
              (bootstrap/start-rebalance-collection "C1-NON_EXIST" {:headers {}}))))
     (testing "no permission for get-rebalance-status"
       (is (= {:status 401
               :errors ["You do not have permission to perform that action."]}
              (bootstrap/finalize-rebalance-collection (:concept-id coll1) nil))))
     (testing "no permission for finalize-rebalance-collection"
       (is (= {:status 401
               :errors ["You do not have permission to perform that action."]}
              (bootstrap/get-rebalance-status (:concept-id coll1) nil))))
     (testing "Non existent provider"
       (is (= {:status 400
               :errors ["Provider: [NON_EXIST] does not exist in the system"]}
              (bootstrap/start-rebalance-collection "C1-NON_EXIST" {:synchronous false}))))
     (testing "Non existent collection cannot be moved to a separate index"
       (is (= {:status 400
               :errors ["Collection [C1-PROV1] does not exist."]}
              (bootstrap/start-rebalance-collection "C1-PROV1" {:synchronous false}))))
     (testing "Invalid target"
       (is (= {:status 400
               :errors [(str "Invalid target index [small-fry]. Only separate-index or "
                             "small-collections are allowed.")]}
              (bootstrap/start-rebalance-collection (:concept-id coll1) {:synchronous false
                                                                         :target "small-fry"}))))
     (testing "Finalizing not started collection"
       (is (= {:status 400
               :errors [(str "The index set does not contain the rebalancing collection ["
                             (:concept-id coll1)"]")]}
              (bootstrap/finalize-rebalance-collection (:concept-id coll1)))))
     (testing "Finalizing non existent collection"
       (is (= {:status 400
               :errors ["The index set does not contain the rebalancing collection [C1-NON_EXIST]"]}
              (bootstrap/finalize-rebalance-collection "C1-NON_EXIST"))))
     (testing "Starting already rebalancing collection"
       (bootstrap/start-rebalance-collection (:concept-id coll1) {:synchronous true})
       (is (= {:status 400
               :errors [(str "The index set already contains rebalancing collection ["
                             (:concept-id coll1)"]")]}
              (bootstrap/start-rebalance-collection (:concept-id coll1) {:synchronous false}))))
     (testing "Finalizing already finalized collection"
       (bootstrap/finalize-rebalance-collection (:concept-id coll1))
       (is (= {:status 400
               :errors [(str "The index set does not contain the rebalancing collection ["
                             (:concept-id coll1)"]")]}
              (bootstrap/finalize-rebalance-collection (:concept-id coll1)))))
     (testing "Starting already rebalanced collection"
       (is (= {:status 400
               :errors [(str "The collection [" (:concept-id coll1)
                             "] already has a separate granule index.")]}
              (bootstrap/start-rebalance-collection (:concept-id coll1) {:synchronous false}))))
     (testing "Starting with a target of small collections when already in small collections"
       (is (= {:status 400
               :errors [(str "The collection [" (:concept-id coll2)
                             "] does not have a separate granule index.")]}
              (bootstrap/start-rebalance-collection
               (:concept-id coll2) {:synchronous false
                                    :target "small-collections"}))))
     (testing "Starting target of small collections when already rebalancing to separate index"
       (bootstrap/start-rebalance-collection (:concept-id coll2))
       (is (= {:status 400
               :errors [(str "The index set already contains rebalancing collection ["
                             (:concept-id coll2)"]")]}
              (bootstrap/start-rebalance-collection
               (:concept-id coll2) {:synchronous false
                                    :target "small-collections"}))))
     (testing "Starting target of separate index when already rebalancing to small collections"
       (bootstrap/finalize-rebalance-collection (:concept-id coll2))
       (index/wait-until-indexed)
       (bootstrap/start-rebalance-collection (:concept-id coll2) {:target "small-collections"})
       (is (= {:status 400
               :errors [(str "The index set already contains rebalancing collection ["
                             (:concept-id coll2)"]")]}
              (bootstrap/start-rebalance-collection
               (:concept-id coll2) {:synchronous false
                                    :target "separate-index"})))))))

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
  "Increments the number of granules expected for a collection in the expected provider holdings"
  [expected-provider-holdings coll num]
  (for [coll-holding expected-provider-holdings]
    (if (= (:concept-id coll-holding) (:concept-id coll))
      (update coll-holding :granule-count + num)
      coll-holding)))

;; Rebalances a single collection with a single granule
(deftest ^:oracle simple-rebalance-collection-test
  (s/only-with-real-database
   (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}))
         gran1 (ingest-granule-for-coll coll1 1)
         expected-provider-holdings (-> coll1
                                        (select-keys [:provider-id :concept-id :entry-title])
                                        (assoc :granule-count 1)
                                        vector)]
     (index/wait-until-indexed)

     (assert-rebalance-status {:small-collections 1 :rebalancing-status "NOT_REBALANCING"} coll1)
     (verify-provider-holdings expected-provider-holdings "Initial")

     ;; Start rebalancing of collection 1. After this it will be in small collections and a separate index
     (bootstrap/start-rebalance-collection (:concept-id coll1))
     (index/wait-until-indexed)

     ;; After rebalancing 1 granule is in small collections and in the new index.
     (assert-rebalance-status {:small-collections 1 :separate-index 1 :rebalancing-status "COMPLETE"} coll1)

     ;; Clear the search cache so it will get the last index set
     (search/clear-caches)
     (verify-provider-holdings expected-provider-holdings  "After start and after clear cache")

     ;; Index new data. It should go into both indexes
     (ingest-granule-for-coll coll1 2)
     (index/wait-until-indexed)
     (assert-rebalance-status {:small-collections 2 :separate-index 2 :rebalancing-status "COMPLETE"} coll1)

     (let [expected-provider-holdings (inc-provider-holdings-for-coll
                                       expected-provider-holdings coll1 1)]
       (verify-provider-holdings expected-provider-holdings "After indexing more")

       ;; Finalize rebalancing
       (bootstrap/finalize-rebalance-collection (:concept-id coll1))
       (index/wait-until-indexed)

       ;; The granules have been removed from small collections
       (assert-rebalance-status {:small-collections 0 :separate-index 2 :rebalancing-status "NOT_REBALANCING"} coll1)

       ;; After the cache is cleared the right amount of data is found
       (search/clear-caches)
       (verify-provider-holdings
         expected-provider-holdings
         "After finalize after clear cache")))))

(deftest ^:oracle rebalance-collection-test
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

     (assert-rebalance-status {:small-collections 4 :rebalancing-status "NOT_REBALANCING"} coll1)
     (verify-provider-holdings expected-provider-holdings "Initial")

     ;; Start rebalancing of collection 1. After this it will be in small collections and a separate index
     (bootstrap/start-rebalance-collection (:concept-id coll1))
     (index/wait-until-indexed)

     ;; After rebalancing 4 granules are in small collections and in the new index.
     (assert-rebalance-status {:small-collections 4 :separate-index 4 :rebalancing-status "COMPLETE"} coll1)
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
     (assert-rebalance-status {:small-collections 6 :separate-index 6 :rebalancing-status "COMPLETE"} coll1)

     (let [expected-provider-holdings (inc-provider-holdings-for-coll
                                       expected-provider-holdings coll1 2)]
       (verify-provider-holdings expected-provider-holdings "After indexing more")

       ;; Finalize rebalancing
       (bootstrap/finalize-rebalance-collection (:concept-id coll1))
       (index/wait-until-indexed)

       ;; The granules have been removed from small collections
       (assert-rebalance-status {:small-collections 0 :separate-index 6 :rebalancing-status "NOT_REBALANCING"} coll1)

       ;; Note that after finalize has run but before search has updated to use the new index set
       ;; it will find 0 granules for this collection. The job for refreshing that cache runs every
       ;; 5 minutes.
       ;; This check is here as a demonstration of the problem and not an assertion of what we want to happen.
       ;; Fix during CMR-2668

       ;; Later: This check was originally here as a demonstration of the failure but sometimes it actually
       ;; "passes". I think there's a race condition where search will use the correct index.
       ; (verify-provider-holdings
         ; (inc-provider-holdings-for-coll
          ; expected-provider-holdings coll1 -6)
         ; "After finalize before clear cache")

       ;; After the cache is cleared the right amount of data is found
       (search/clear-caches)
       (verify-provider-holdings
         expected-provider-holdings
         "After finalize after clear cache")))))

;; Tests rebalancing multiple collections at the same time.
(deftest ^:oracle rebalance-multiple-collections-test
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
     ;; Start rebalancing of collection 1 and 2. After this it will be in small collections and a
     ;; separate index
     (bootstrap/start-rebalance-collection (:concept-id coll1))
     (bootstrap/start-rebalance-collection (:concept-id coll2))
     (index/wait-until-indexed)

     ;; After rebalancing 4 granules are in small collections and in the new index.
     (assert-rebalance-status {:small-collections 4 :separate-index 4 :rebalancing-status "COMPLETE"} coll1)
     (assert-rebalance-status {:small-collections 4 :separate-index 4 :rebalancing-status "COMPLETE"} coll2)

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
     (assert-rebalance-status {:small-collections 6 :separate-index 6 :rebalancing-status "COMPLETE"} coll1)
     (assert-rebalance-status {:small-collections 7 :separate-index 7 :rebalancing-status "COMPLETE"} coll2)

     (let [expected-provider-holdings (-> expected-provider-holdings
                                          (inc-provider-holdings-for-coll coll1 2)
                                          (inc-provider-holdings-for-coll coll2 3))]
       (verify-provider-holdings expected-provider-holdings "After indexing more granules")

       ;; Finalize rebalancing
       (bootstrap/finalize-rebalance-collection (:concept-id coll1))
       (bootstrap/finalize-rebalance-collection (:concept-id coll2))
       (index/wait-until-indexed)

       ;; The granules have been removed from small collections
       (assert-rebalance-status {:small-collections 0 :separate-index 6 :rebalancing-status "NOT_REBALANCING"} coll1)
       (assert-rebalance-status {:small-collections 0 :separate-index 7 :rebalancing-status "NOT_REBALANCING"} coll2)

       ;; After the cache is cleared the right amount of data is found
       (search/clear-caches)
       (verify-provider-holdings expected-provider-holdings "After finalizing")))))

(deftest ^:oracle rebalance-collection-after-other-collections-test
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

     ;; Start rebalancing of collection 3. After this it will be in small collections and a separate
     ;; index
     (bootstrap/start-rebalance-collection (:concept-id coll3))
     (index/wait-until-indexed)

     ;; After rebalancing 4 granules are in small collections and in the new index.
     (assert-rebalance-status {:small-collections 4 :separate-index 4 :rebalancing-status "COMPLETE"} coll3)
     ;; Clear the search cache so it will get the last index set
     (search/clear-caches)
     (verify-provider-holdings expected-provider-holdings  "After start and after clear cache")

     ;; Index new data. It should go into both indexes
     (ingest-granule-for-coll coll3 4)
     (ingest-granule-for-coll coll3 5)
     (index/wait-until-indexed)
     (assert-rebalance-status {:small-collections 6 :separate-index 6 :rebalancing-status "COMPLETE"} coll3)

     (let [expected-provider-holdings (inc-provider-holdings-for-coll
                                       expected-provider-holdings coll3 2)]
       (verify-provider-holdings expected-provider-holdings "After indexing more")

       ;; Finalize rebalancing
       (bootstrap/finalize-rebalance-collection (:concept-id coll3))
       (index/wait-until-indexed)

       ;; The granules have been removed from small collections
       (assert-rebalance-status {:small-collections 0 :separate-index 6 :rebalancing-status "NOT_REBALANCING"} coll3)
       ;; After the cache is cleared the right amount of data is found
       (search/clear-caches)
       (verify-provider-holdings
         expected-provider-holdings
         "After finalize after clear cache")))))

(defn- rebalance-to-separate-index
  "Helper to rebalance a collection to its own index."
  [concept-id]
  (bootstrap/start-rebalance-collection concept-id)
  (index/wait-until-indexed)
  (bootstrap/finalize-rebalance-collection concept-id)
  (index/wait-until-indexed))

;; Rebalance collections uses delete-by-query which cannot be force refreshed.
;; As a result, after the granules are moved from small_collections index to separate index,
;; the granule deleted version is still in the small_collections index waiting to be permanently
;; deleted. At this time, if we move back to small collections index immediately as in this test,
;; the rebalancing would fail because of version conflict. It is hard to set up the perfect
;; condition for this unrealistic test, so we comment this test out.
#_(deftest rebalance-collection-back-to-small-collections-test
  (s/only-with-real-database
   (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}))
         coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"}))
         granules (doall (for [coll [coll1 coll2]
                               n (range 2)]
                           (ingest-granule-for-coll coll n)))]
     (index/wait-until-indexed)
     ;; Start with collections in their own index
     (rebalance-to-separate-index (:concept-id coll1))
     (rebalance-to-separate-index (:concept-id coll2))
     (assert-rebalance-status {:small-collections 0 :separate-index 2 :rebalancing-status "NOT_REBALANCING"} coll1)
     (assert-rebalance-status {:small-collections 0 :separate-index 2 :rebalancing-status "NOT_REBALANCING"} coll2)
     ;; Start rebalancing back to small collections
     (bootstrap/start-rebalance-collection (:concept-id coll1) {:target "small-collections"})
     (bootstrap/start-rebalance-collection (:concept-id coll2) {:target "small-collections"})
     (index/wait-until-indexed)
     ;; After rebalancing 2 granules are in small collections and in the new index.
     (assert-rebalance-status {:small-collections 2 :separate-index 2 :rebalancing-status "COMPLETE"} coll1)
     (assert-rebalance-status {:small-collections 2 :separate-index 2 :rebalancing-status "COMPLETE"} coll2)
     ;; Clear the search cache so it will get the last index set
     (search/clear-caches)
     ;; Delete the granules from small collections to show that search is still using the separate
     ;; index
     (index/delete-granules-from-small-collections coll2)
     (index/wait-until-indexed)
     (assert-rebalance-status {:small-collections 0 :separate-index 2 :rebalancing-status "COMPLETE"} coll2)
     (is (= 2 (count (:refs (search/find-refs :granule {:concept-id (:concept-id coll1)})))))
     (is (= 2 (count (:refs (search/find-refs :granule {:concept-id (:concept-id coll2)})))))
     ;; Index new data. It should go into both indexes
     (ingest-granule-for-coll coll1 4)
     (ingest-granule-for-coll coll1 5)
     (ingest-granule-for-coll coll2 4)
     (ingest-granule-for-coll coll2 5)
     (index/wait-until-indexed)
     (assert-rebalance-status {:small-collections 4 :separate-index 4 :rebalancing-status "COMPLETE"} coll1)
     (assert-rebalance-status {:small-collections 2 :separate-index 4 :rebalancing-status "COMPLETE"} coll2)
     ;; Verify it is still using the separate index for search
     (is (= 4 (count (:refs (search/find-refs :granule {:concept-id (:concept-id coll1)})))))
     (is (= 4 (count (:refs (search/find-refs :granule {:concept-id (:concept-id coll2)})))))
     ;; Finalize rebalancing
     (bootstrap/finalize-rebalance-collection (:concept-id coll1))
     (bootstrap/finalize-rebalance-collection (:concept-id coll2))
     (index/wait-until-indexed)
     ;; After the cache is cleared the right amount of data is found
     (search/clear-caches)
     ;; We manually deleted 2 of the granules from coll2 in the small collections index earlier
     ;; so we expect now that there are 2 fewer granules for coll2 and no change for coll1.
     (is (= 4 (count (:refs (search/find-refs :granule {:concept-id (:concept-id coll1)})))))
     (is (= 2 (count (:refs (search/find-refs :granule {:concept-id (:concept-id coll2)})))))
     ;; Delete the collection specific indexes to show it has no impact on search after finalize
     (index/delete-elasticsearch-index coll1)
     (index/delete-elasticsearch-index coll2)
     (index/wait-until-indexed)
     (assert-rebalance-status {:small-collections 4 :rebalancing-status "NOT_REBALANCING"} coll1)
     (assert-rebalance-status {:small-collections 2 :rebalancing-status "NOT_REBALANCING"} coll2)
     (is (= 4 (count (:refs (search/find-refs :granule {:concept-id (:concept-id coll1)})))))
     (is (= 2 (count (:refs (search/find-refs :granule {:concept-id (:concept-id coll2)}))))))))

(deftest ^:oracle rebalance-old-deleted-collection-back-to-small-collections-test
  (side/eval-form `(tk/set-time-override! (tk/now)))
  (s/only-with-real-database
   (let [deleted-coll (d/ingest "PROV1" (dc/collection {:entry-title "deleted-coll"}))
         granules (doseq [n (range 2)]
                    (ingest-granule-for-coll deleted-coll n))
         deleted-coll-concept (mdb/get-concept (:concept-id deleted-coll))]

     ;; Start collection with a seperate index
     (index/wait-until-indexed)
     (rebalance-to-separate-index (:concept-id deleted-coll))
     (assert-rebalance-status {:small-collections 0 :separate-index 2 :rebalancing-status "NOT_REBALANCING"} deleted-coll)
     (index/wait-until-indexed)

     (dev-sys-util/clear-caches)

     ;; Delete collection and remove the tombstone
     (ingest/delete-concept deleted-coll-concept)
     (index/wait-until-indexed)
     (side/eval-form `(tk/advance-time! ~(* (inc (concept-service/days-to-keep-tombstone)) 24 3600)))
     (is (= 204 (:status (mdb/cleanup-old-revisions))))
     ;; Verify collection revisions are all removed from the database to make sure test is valid
     (is (= 404 (:status (search/retrieve-concept (:concept-id deleted-coll) 1))))
     (is (= 404 (:status (search/retrieve-concept (:concept-id deleted-coll) 2))))

     ;; Rebalance to small-collections
     (bootstrap/start-rebalance-collection (:concept-id deleted-coll) {:target "small-collections"})
     (index/wait-until-indexed)
     (assert-rebalance-status {:small-collections 0 :separate-index 0 :rebalancing-status "COMPLETE"} deleted-coll)
     (bootstrap/finalize-rebalance-collection (:concept-id deleted-coll))

     ;; Remove index and search for granules
     (index/wait-until-indexed)
     (index/delete-elasticsearch-index deleted-coll)
     (index/wait-until-indexed)
     (assert-rebalance-status {:small-collections 0 :rebalancing-status "NOT_REBALANCING"} deleted-coll)
     (is (= 0 (count (:refs (search/find-refs :granule {:concept-id (:concept-id deleted-coll)})))))))
  (side/eval-form `(tk/clear-current-time!)))
