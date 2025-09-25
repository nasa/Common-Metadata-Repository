(ns cmr.system-int-test.bootstrap.reshard-index-test
  "Tests rebalancing granule indexes by moving collections's granules from the small collections
   index to separate collection indexes"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(deftest ^:oracle reshard-index-error-test
  (s/only-with-real-database
   (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}) {:validate-keywords false})
         _ (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran1"}))
         coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"}) {:validate-keywords false})
         _ (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "gran2"}))]
     (index/wait-until-indexed)
     (testing "no permission for start-reshard-index"
       (is (= {:status 401
               :errors ["You do not have permission to perform that action."]}
              (bootstrap/start-reshard-index "non_existing_index" {:headers {}}))))
     (testing "shard count cannot equal zero"
       (is (= {:status 400
               :errors ["Invalid num_shards [0]. Only integers greater than zero are allowed."]}
              (bootstrap/start-reshard-index "small_collections" {:synchronous false :num-shards 0}))))
     (testing "shard count cannot be less than zero"
       (is (= {:status 400
               :errors ["Invalid num_shards [-1]. Only integers greater than zero are allowed."]}
              (bootstrap/start-reshard-index "small_collections" {:synchronous false :num-shards -1}))))
     (testing "shard count must be an integer"
       (is (= {:status 400
               :errors ["Invalid num_shards [1.1]. Only integers greater than zero are allowed."]}
              (bootstrap/start-reshard-index "small_collections" {:synchronous false :num-shards 1.1}))))
     (testing "shard count must be a number"
       (is (= {:status 400
               :errors ["Invalid num_shards [abc]. Only integers greater than zero are allowed."]}
              (bootstrap/start-reshard-index "small_collections" {:synchronous false :num-shards "abc"}))))
     (testing "index must exist"
       (is (= {:status 404
               :errors ["Index [non-existing-index] does not exist."]}
              (bootstrap/start-reshard-index "non-existing-index" {:synchronous false :num-shards 1})))))))