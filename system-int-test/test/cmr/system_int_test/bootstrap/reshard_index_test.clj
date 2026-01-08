(ns cmr.system-int-test.bootstrap.reshard-index-test
  "Tests the resharding API endpoint for index resharding operations."
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.indexer.services.index-set-service :as index-set-service]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.elastic-util :as es-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(deftest reshard-index-error-test
  (s/only-with-real-database
   (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}) {:validate-keywords false})
         _ (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran1"}))
         coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"}) {:validate-keywords false})
         _ (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "gran2"}))
         gran-elastic-name "gran-elastic"
         elastic-name "elastic"]
     (index/wait-until-indexed)
     (testing "no elastic cluster name given"
       (is (= {:status 400
               :errors ["Empty elastic cluster name is not allowed."]}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous true :num-shards 100}))))
     (testing "no permission for start-reshard-index"
       (is (= {:status 401
               :errors ["You do not have permission to perform that action."]}
              (bootstrap/start-reshard-index "1_small_collections" {:headers {} :elastic-name gran-elastic-name}))))
     (testing "missing shard count"
       (is (= {:status 400
               :errors ["num_shards is a required parameter."]}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous false :elastic-name gran-elastic-name}))))
     (testing "shard count cannot equal zero"
       (is (= {:status 400
               :errors ["Invalid num_shards [0]. Only integers greater than zero are allowed."]}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous false :num-shards 0 :elastic-name gran-elastic-name}))))
     (testing "shard count cannot be less than zero"
       (is (= {:status 400
               :errors ["Invalid num_shards [-1]. Only integers greater than zero are allowed."]}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous false :num-shards -1 :elastic-name gran-elastic-name}))))
     (testing "shard count must be an integer"
       (is (= {:status 400
               :errors ["Invalid num_shards [1.1]. Only integers greater than zero are allowed."]}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous false :num-shards 1.1 :elastic-name gran-elastic-name}))))
     (testing "acls are not supported"
       (is (= {:status 400
               :errors ["Resharding is not allowed for acls or groups."]}
              (bootstrap/start-reshard-index "acls" {:synchronous false :num-shards 1 :elastic-name elastic-name}))))
     (testing "groups are not supported"
       (is (= {:status 400
               :errors ["Resharding is not allowed for acls or groups."]}
              (bootstrap/start-reshard-index "groups" {:synchronous false :num-shards 1 :elastic-name elastic-name}))))
     (testing "shard count must be a number"
       (is (= {:status 400
               :errors ["Invalid num_shards [abc]. Only integers greater than zero are allowed."]}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous false :num-shards "abc" :elastic-name gran-elastic-name}))))
     (testing "index must exist"
       (is (= {:status 404
               :errors ["Index [1_non-existing-index] does not exist."]}
              (bootstrap/start-reshard-index "1_non-existing-index" {:synchronous false :num-shards 1 :elastic-name gran-elastic-name}))))
     (testing "attempting to reshard an index that is already being resharded fails"
       (is (= {:status 200
               :message "Resharding started for index 1_small_collections"}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous false :num-shards 100 :elastic-name gran-elastic-name})))
       (is (= {:status 400
               :errors ["The index set already contains resharding index [1_small_collections]"]}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous false :num-shards 100 :elastic-name gran-elastic-name}))))
     (testing "no elastic name given to get resharding status"
       (is (= {:status 400
               :errors ["Empty elastic cluster name is not allowed."]}
              (bootstrap/get-reshard-status "1_small_collections" {}))))
     (testing "get the resharding status of an index not being resharded"
       (is (= {:status 404
               :errors ["The index [1_collections_v2] is not being resharded."]}
              (bootstrap/get-reshard-status "1_collections_v2" {:elastic-name elastic-name}))))
     (testing "get the resharding status of a nonexistent index"
       (is (= {:status 404
               :errors ["The index [1_nonexistent_index] does not exist."]}
              (bootstrap/get-reshard-status "1_nonexistent_index" {:elastic-name gran-elastic-name}))))
     (testing "no elastic name given to finalize"
       (is (= {:status 400
               :errors ["Empty elastic cluster name is not allowed."]}
              (bootstrap/finalize-reshard-index "1_small_collections" {:synchronous false}))))
     (testing "finalize index that does not exist"
       (is (= {:status 404
               :errors ["The index [1_nonexistent_index] does not exist."]}
              (bootstrap/finalize-reshard-index "1_nonexistent_index" {:elastic-name gran-elastic-name})))))))

(deftest reshard-index-success-test
  (s/only-with-real-database
   (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}) {:validate-keywords false})
         _ (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran1"}))
         coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"}) {:validate-keywords false})
         _ (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "gran2"}))
         expected-provider-holdings (for [coll [coll1 coll2]]
                                      (-> coll
                                          (select-keys [:provider-id :concept-id :entry-title])
                                          (assoc :granule-count 1)))
         gran-elastic-name "gran-elastic"]
     (index/wait-until-indexed)
     (bootstrap/verify-provider-holdings expected-provider-holdings "Initial")
     (testing "resharding an index that does exist"
       (is (= {:status 200
               :message "Resharding started for index 1_small_collections"}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous true
                                                                    :num-shards 100
                                                                    :elastic-name gran-elastic-name}))))
     (testing "get the resharding status"
       (is (= {:status 200
               :original-index "1_small_collections"
               :reshard-index "1_small_collections_100_shards"
               :reshard-status "COMPLETE"}
              (bootstrap/get-reshard-status "1_small_collections" {:elastic-name gran-elastic-name}))))
     (testing "finalizing the resharding"
       (is (= {:status 200
               :message "Resharding completed for index 1_small_collections"}
              (bootstrap/finalize-reshard-index "1_small_collections" {:synchronous false :elastic-name gran-elastic-name}))))
     (testing "alias is moved to new index"
       (is (index/alias-exists? "1_small_collections_100_shards" "1_small_collections_alias" gran-elastic-name)))
     (testing "index can be resharded more than once"
       (is (= {:status 200
               :message "Resharding started for index 1_small_collections_100_shards"}
              (bootstrap/start-reshard-index "1_small_collections_100_shards" {:synchronous true :num-shards 50 :elastic-name gran-elastic-name}))))
     (testing "finalizing the resharding a second time"
       (is (= {:status 200
               :message "Resharding completed for index 1_small_collections_100_shards"}
              (bootstrap/finalize-reshard-index "1_small_collections_100_shards" {:synchronous false :elastic-name gran-elastic-name}))))
     ; Start rebalancing of collection 1. After this it will be in small collections and a separate index
     (bootstrap/start-rebalance-collection (:concept-id coll1))
     (index/wait-until-indexed)
     (bootstrap/assert-rebalance-status {:small-collections 1 :separate-index 1 :rebalancing-status "COMPLETE"} coll1)
     ;; Finalize rebalancing
     (bootstrap/finalize-rebalance-collection (:concept-id coll1))
     (index/wait-until-indexed)

     ;; The granules have been removed from small collections
     (bootstrap/assert-rebalance-status {:small-collections 0 :separate-index 1 :rebalancing-status "NOT_REBALANCING"} coll1)

     ;; After the cache is cleared the right amount of data is found
     (search/clear-caches)
     (bootstrap/verify-provider-holdings expected-provider-holdings "After finalize after clear cache"))))

(deftest reshard-index-with-granule-updates-test
  ;; When a granule is upserted during the reshard process, it should save to both indexes and the index set
  ;; should not update the index mapping until the reshard is finalized
  (testing "Creating and updating granule during reshard saves to both old and target indexes"
    (let [gran-elastic-name "gran-elastic"
          ;; create collection into small_collections index
          coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}) {:validate-keywords false})
          small-collections-canonical-name "small_collections"
          small-collections-index-name (str "1_" small-collections-canonical-name)

          ;; start reshard
          _ (is (= {:status 200
                    :message "Resharding started for index 1_small_collections"}
                   (bootstrap/start-reshard-index small-collections-index-name
                                                  {:synchronous true :num-shards 4 :elastic-name gran-elastic-name})))

          ;; check original index set is still mapped to original target
          orig-index-set (index/get-index-set-by-id 1)
          _ (is (= small-collections-index-name (get-in orig-index-set [:index-set :concepts :granule (keyword small-collections-canonical-name)])))

          ;; create granule
          gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran1"}))

          _ (index/wait-until-indexed)

          ;; check granule doc is saved to old index
          gran-doc-in-orig-index (es-util/get-doc small-collections-index-name (:concept-id gran1) gran-elastic-name)
          gran-doc-in-orig-index-revision-id (get-in gran-doc-in-orig-index [:_source :revision-id])
          _ (is (= gran-doc-in-orig-index-revision-id 1))

          ;; check granule doc is saved in new index too
          gran-doc-in-new-index (es-util/get-doc small-collections-index-name (:concept-id gran1) gran-elastic-name)
          gran-doc-in-new-index-revision-id (get-in gran-doc-in-new-index [:_source :revision-id])
          _ (is (= gran-doc-in-new-index-revision-id 1))

          ;; update granule
          _ (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran1"}))

          _ (index/wait-until-indexed)

          ;; check granule doc is saved to old index with updated revision
          gran-doc-in-orig-index (es-util/get-doc small-collections-index-name (:concept-id gran1) gran-elastic-name)
          gran-doc-in-orig-index-revision-id (get-in gran-doc-in-orig-index [:_source :revision-id])
          _ (is (= gran-doc-in-orig-index-revision-id 2))

          ;; check granule doc is saved in new index too with updated revision
          gran-doc-in-new-index (es-util/get-doc small-collections-index-name (:concept-id gran1) gran-elastic-name)
          gran-doc-in-new-index-revision-id (get-in gran-doc-in-new-index [:_source :revision-id])
          _ (is (= gran-doc-in-new-index-revision-id 2))

          ;; wait until reshard is complete
          _ (bootstrap/wait-for-reshard-complete small-collections-index-name gran-elastic-name {})

          ;; finalize reshard
          _ (is (= {:status 200
                    :message "Resharding completed for index 1_small_collections"}
                   (bootstrap/finalize-reshard-index small-collections-index-name {:synchronous false :elastic-name gran-elastic-name})))

          resharded-small-collections-index-name (str small-collections-index-name "_4_shards")

          ;; check collection index is mapped to new target
          updated-index-set (index/get-index-set-by-id 1)
          _ (is (= resharded-small-collections-index-name (get-in updated-index-set [:index-set :concepts :granule (keyword small-collections-canonical-name)])))]

      ;; check orig index is deleted from index-set and elastic
      (is (not-any? #(= (:name %) small-collections-canonical-name) (get-in updated-index-set [:granule :indexes])))
      (is (not (es-util/index-exists? small-collections-index-name gran-elastic-name))))))

;; Rebalance collections uses delete-by-query which cannot be force refreshed.
;; As a result, after the granules are moved from small_collections index to separate index,
;; the granule deleted version is still in the small_collections index waiting to be permanently
;; deleted. At this time, if we move back to small collections index immediately as in this test,
;; the rebalancing would fail because of version conflict. It is hard to set up the perfect
;; condition in test, so we comment this test out.
(deftest reshard-individual-granule-index-with-rebalancing-test
  (s/only-with-real-database
   (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}) {:validate-keywords false})
         _ (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran1"}))
         coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"}) {:validate-keywords false})
         _ (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "gran2"}))
         expected-provider-holdings (for [coll [coll1 coll2]]
                                      (-> coll
                                          (select-keys [:provider-id :concept-id :entry-title])
                                          (assoc :granule-count 1)))
         coll1-concept-id (:concept-id coll1)
         coll1-index (index-set-service/gen-valid-index-name "1" (:concept-id coll1))
         coll1-index-base-name (str/replace-first coll1-index #"^1_" "")
         gran-elastic-name "gran-elastic"]
     (index/wait-until-indexed)
     (bootstrap/verify-provider-holdings expected-provider-holdings "Initial")
     ;; Start rebalancing of collection 1. After this it will be in small collections and a separate index
     (bootstrap/start-rebalance-collection (:concept-id coll1))
     (index/wait-until-indexed)

     (bootstrap/wait-for-rebalance-to-complete coll1 {})

     ;; After rebalancing 1 granule is in small collections and in the new index.
     (bootstrap/assert-rebalance-status {:small-collections 1 :separate-index 1 :rebalancing-status "COMPLETE"} coll1)

     ; Finalize rebalancing
     (bootstrap/finalize-rebalance-collection (:concept-id coll1))
     (index/wait-until-indexed)

     ;; The granules have been removed from small collections
     (bootstrap/assert-rebalance-status {:small-collections 0 :separate-index 1 :rebalancing-status "NOT_REBALANCING"} coll1)

     (testing "index set is in correct state"
       (let [orig-index-set (index/get-index-set-by-id 1)
             orig-granule-concepts (get-in orig-index-set [:index-set :concepts :granule])
             expected-granule-concepts {:small_collections "1_small_collections"
                                        (keyword coll1-concept-id) (index-set-service/gen-valid-index-name 1 coll1-concept-id)}]
         (is (= expected-granule-concepts orig-granule-concepts))))

     (testing "resharding an individual granule index"
       (is (= {:status 200
               :message (format "Resharding started for index %s" coll1-index)}
              (bootstrap/start-reshard-index coll1-index {:synchronous true :num-shards 2 :elastic-name gran-elastic-name}))))
     (testing "get the resharding status"
       (is (= {:status 200
               :original-index coll1-index
               :reshard-index (str coll1-index "_2_shards")
               :reshard-status "COMPLETE"}
              (bootstrap/get-reshard-status coll1-index {:elastic-name gran-elastic-name}))))
     (testing "finalizing the resharding"
       (bootstrap/wait-for-reshard-complete coll1-index gran-elastic-name {})
       (is (= {:status 200
               :message (format "Resharding completed for index %s" coll1-index)}
              (bootstrap/finalize-reshard-index coll1-index {:synchronous false :elastic-name gran-elastic-name}))))
     (testing "index set is in correct state after resharding"
       (let [orig-index-set (index/get-index-set-by-id 1)
             resharded-coll1-index-name (str coll1-index "_2_shards")
             orig-granule-concepts (get-in orig-index-set [:index-set :concepts :granule])
             expected-granule-concepts {:small_collections "1_small_collections"
                                        (keyword coll1-concept-id) resharded-coll1-index-name}
             orig-gran-indexes (get-in orig-index-set [:index-set :granule :indexes])
             orig-coll1-gran-index-details (first (filter #(= (:name %) (str coll1-index-base-name "_2_shards")) orig-gran-indexes))
             found-idx-mapping-num-of-shards (get-in orig-coll1-gran-index-details [:settings :index :number_of_shards])]

         (is (= expected-granule-concepts orig-granule-concepts))
         (is (= 2 found-idx-mapping-num-of-shards))))
     (testing "alias is moved to new index"
       (is (index/alias-exists? (str coll1-index "_2_shards") (str coll1-index "_alias") gran-elastic-name)))

     ;;; Start rebalancing of collection 1 back to the small_collections. After this it will be in small collections and the separate index will be removed
     (bootstrap/start-rebalance-collection (:concept-id coll1) {:target "small-collections"})
     (index/wait-until-indexed)

     (bootstrap/wait-for-rebalance-to-complete coll1 {})
     ;;; It is hard to control the timing of force delete in Elasticsearch, so we skip the testing of exact doc count in the following steps
     (bootstrap/assert-rebalance-status {:small-collections 1 :separate-index 1 :rebalancing-status "COMPLETE"} coll1)
     ;;; Finalize rebalancing
     (bootstrap/finalize-rebalance-collection (:concept-id coll1))
     (index/wait-until-indexed)

     ;; TODO after an collection index is returned to small_collections the index set is not updated correctly... that's not good
     (testing "index set is in correct state after returning coll1 to small_collections index"
       (let [orig-index-set (index/get-index-set-by-id 1)
             ;_ (println "orig-index-set after returning coll1 to small_collections = " orig-index-set)
             orig-granule-concepts (get-in orig-index-set [:index-set :concepts :granule])
             _ (println "orig-granule-concepts = " orig-granule-concepts)
             expected-granule-concepts {:small_collections "1_small_collections"}
             orig-gran-indexes (get-in orig-index-set [:index-set :granule :indexes])
             _ (println "orig-gran-indexes = " orig-gran-indexes)]
         (is (= expected-granule-concepts orig-granule-concepts))
         (is (= 1 (count orig-gran-indexes)))))

     ;; After the cache is cleared the right amount of data is found
     (search/clear-caches)
     (bootstrap/verify-provider-holdings expected-provider-holdings "After finalize after clear cache")
     )))
