(ns cmr.system-int-test.indexer.indexer-test
  "Tests indexer APIs"
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.url-helper :as url]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(deftest db-migrate-collections-index-test
  "Given that index-set needs an update for non-gran cluster, an update to collections index collections_v2 creates or updates the correct index-alias pair"
  (let [elastic-name "elastic"
        ;; we create the initial index-set during reset-fixture
        curr-index-set (index/get-index-set-by-id 1)
        _ (println "curr-index-set = " curr-index-set)
        coll-aliases ["collection_search_alias" "1_collections_v2_alias"]

        alias-to-index-map
        (into {}
              (for [alias-name coll-aliases
                    :let [mapped-indexes (index/get-indexes-mapped-to-alias alias-name elastic-name)]]
                (do
                  (println "alias after migration " alias-name " is mapped to " mapped-indexes)
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

        ;; do a db-migrate that will trigger a required update to non-gran ES
        _ (index/db-migrate {:force true})

        alias-to-index-map-after-db-migrate
        (into {}
              (for [alias-name coll-aliases
                    :let [mapped-indexes (index/get-indexes-mapped-to-alias alias-name elastic-name)]]
                (do
                  (println "alias after migration " alias-name " is mapped to " mapped-indexes)
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
                  (println "alias after migration " alias-name " is mapped to " mapped-indexes)
                  [(keyword alias-name) mapped-indexes])))

        _ (is (= alias-to-index-map-after-unmapping-alias
                 {:collection_search_alias [:1_collections_v2_2_shards]
                  :1_collections_v2_alias [:1_collections_v2_2_shards]}))

        ;; delete the collections index mapping in index-set
        index-set-with-deleted-coll-index (update-in (index/get-index-set-by-id 1)
                                                     [:concepts :collection]
                                                     dissoc
                                                     "collections-v2")
        _ (index/update-index-set index-set-with-deleted-coll-index elastic-name)

        ;; db-migrate force, expect an exception
        ;; TODO fix, this one not working as expected
        _ (is (thrown? Exception (index/db-migrate {:force true})))

        ]
    ))