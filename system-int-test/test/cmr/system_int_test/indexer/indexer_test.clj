(ns cmr.system-int-test.indexer.indexer-test
  "Tests indexer APIs"
  (:require [clojure.test :refer :all]
            [cheshire.core :as json]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.metadata-db-util :as mdb]
            [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(deftest db-migrate-test
  (testing "Given that index-set needs an update for non-gran cluster, an update to collections index collections_v2 creates or updates the correct index-alias pair"
    (let [elastic-name "elastic"
          coll-aliases ["collection_search_alias" "1_collections_v2_alias"]

          alias-to-index-map
          (into {}
                (for [alias-name coll-aliases
                      :let [mapped-indexes (index/get-indexes-mapped-to-alias alias-name elastic-name)]]
                  (do
                    [(keyword alias-name) mapped-indexes])))

          _ (is (= alias-to-index-map
                   {:collection_search_alias [:1_collections_v2]
                    :1_collections_v2_alias [:1_collections_v2]}))

          coll-index-name "1_collections_v2"

          ;; reshard coll index
          start-reshard-resp (bootstrap/start-reshard-index coll-index-name
                                                            {:synchronous true :num-shards 2 :elastic-name elastic-name})
          _ (is (= 200 (:status start-reshard-resp)))
          task-id (:task-id start-reshard-resp)
          _ (bootstrap/wait-for-reshard-complete coll-index-name elastic-name task-id {})
          finalize-reshard-resp (bootstrap/finalize-reshard-index coll-index-name {:synchronous true :elastic-name elastic-name})
          _ (is (= 200 (:status finalize-reshard-resp)))

          ;; force a db migrate
          _ (index/db-migrate {:force true})

          alias-to-index-map-after-db-migrate
          (into {}
                (for [alias-name coll-aliases
                      :let [mapped-indexes (index/get-indexes-mapped-to-alias alias-name elastic-name)]]
                  (do
                    [(keyword alias-name) mapped-indexes])))

          _ (is (= alias-to-index-map-after-db-migrate
                   {:collection_search_alias [:1_collections_v2_2_shards]
                    :1_collections_v2_alias [:1_collections_v2_2_shards]}))

          ;; unmap the indexes that all collections aliases are mapping to
          _ (index/unmap-alias-from-all-indexes "collection_search_alias" elastic-name)

          ;; db-migrate force and check that the alias mapping is back pointing to the right resharded collection index
          _ (index/db-migrate {:force true})

          alias-to-index-map-after-unmapping-alias
          (into {}
                (for [alias-name coll-aliases
                      :let [mapped-indexes (index/get-indexes-mapped-to-alias alias-name elastic-name)]]
                  (do
                    [(keyword alias-name) mapped-indexes])))]

      (is (= alias-to-index-map-after-unmapping-alias
             {:collection_search_alias [:1_collections_v2_2_shards]
              :1_collections_v2_alias [:1_collections_v2_2_shards]}))))

  (testing "DB migrate on granule cluster captures all the existing granules correctly and saves to db correctly"
    (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}) {:validate-keywords false})
          coll-concept-id (:concept-id coll1)
          _ (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran1"}))

          ;; make gran index separate from small_collections
          _ (index/wait-until-indexed)
          _ (bootstrap/assert-rebalance-status {:small-collections 1 :rebalancing-status "NOT_REBALANCING"} coll1)
          _ (bootstrap/start-rebalance-collection (:concept-id coll1))
          _ (index/wait-until-indexed)
          _ (bootstrap/wait-for-rebalance-to-complete coll1 {})
          _ (bootstrap/assert-rebalance-status {:small-collections 1 :separate-index 1 :rebalancing-status "COMPLETE"} coll1)
          _ (bootstrap/finalize-rebalance-collection (:concept-id coll1))
          _ (index/wait-until-indexed)
          _ (search/clear-caches)

          ;; remove a gran index set required field from the index-set to trigger a db-migrate
          new-index-set-without-required-field (update-in (index/get-index-set-by-id 1)
                                                          [:index-set :granule :indexes]
                                                          (fn [indexes]
                                                            ;; Remove the map where :name is "small_collections"
                                                            ;; and wrap in `vec` to keep it as a vector instead of a list
                                                            (vec (remove #(= (:name %) "small_collections") indexes))))

          update-resp (index/update-index-set new-index-set-without-required-field 1)
          _ (is (= 200 (:status update-resp)))

          ;; force db-migrate
          _ (index/db-migrate {})

          ;; check index-set is correct
          curr-index-set (index/get-index-set-by-id 1)
          _ (is (some? (get-in curr-index-set [:index-set :concepts :granule (keyword coll-concept-id)])))
          _ (is (boolean (some #(= (get % :name) coll-concept-id)
                               (get-in curr-index-set [:index-set :granule :indexes]))))

          ;; check db has correct combined index-set
          index-set-concept-id (mdb/get-concept-id :index-set "CMR" "1")
          index-set-in-db-resp (mdb/get-concept index-set-concept-id)
          index-set-in-db (when-let [metadata (:metadata index-set-in-db-resp)]
                            (json/parse-string metadata true))
          _ (is (= (:index-set curr-index-set) (assoc (:index-set index-set-in-db)
                                                 :deleted (:deleted index-set-in-db-resp)
                                                 :revision-id (:revision-id index-set-in-db-resp))))]))

  (testing "DB migrate on non granule cluster captures all the existing non-granule indexes correctly and saves to db correctly"
    (let [;; remove a required non-gran index set field from the index-set to trigger a db-migrate
          new-index-set-without-required-field (update-in (index/get-index-set-by-id 1)
                                                          [:index-set :tag :indexes]
                                                          (fn [indexes]
                                                            ;; Remove the map where :name is "small_collections"
                                                            ;; and wrap in `vec` to keep it as a vector instead of a list
                                                            (vec (remove #(= (:name %) "tags") indexes))))

          update-resp (index/update-index-set new-index-set-without-required-field 1)
          _ (is (= 200 (:status update-resp)))

          ;; force db-migrate
          _ (index/db-migrate {})

          ;; check index-set is correct
          curr-index-set (index/get-index-set-by-id 1)
          _ (is (some? (get-in curr-index-set [:index-set :concepts :tag :tags])))
          _ (is (boolean (some #(= (get % :name) "tags")
                               (get-in curr-index-set [:index-set :tag :indexes]))))

          ;; check db has correct combined index-set
          index-set-concept-id (mdb/get-concept-id :index-set "CMR" "1")
          index-set-in-db-resp (mdb/get-concept index-set-concept-id)
          index-set-in-db (when-let [metadata (:metadata index-set-in-db-resp)]
                            (json/parse-string metadata true))
          _ (is (= (:index-set curr-index-set) (assoc (:index-set index-set-in-db)
                                                 :deleted (:deleted index-set-in-db-resp)
                                                 :revision-id (:revision-id index-set-in-db-resp))))])
    )
  )