(ns cmr.system-int-test.bootstrap.reshard-index-test
  "Tests the resharding API endpoint for index resharding operations."
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

(deftest reshard-index-error-test
  (s/only-with-real-database
   (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}) {:validate-keywords false})
         _ (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran1"}))
         coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"}) {:validate-keywords false})
         _ (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "gran2"}))]
     (index/wait-until-indexed)
     (testing "no permission for start-reshard-index"
       (is (= {:status 401
               :errors ["You do not have permission to perform that action."]}
              (bootstrap/start-reshard-index "1_small_collections" {:headers {}}))))
     (testing "shard count cannot equal zero"
       (is (= {:status 400
               :errors ["Invalid num_shards [0]. Only integers greater than zero are allowed."]}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous false :num-shards 0}))))
     (testing "shard count cannot be less than zero"
       (is (= {:status 400
               :errors ["Invalid num_shards [-1]. Only integers greater than zero are allowed."]}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous false :num-shards -1}))))
     (testing "shard count must be an integer"
       (is (= {:status 400
               :errors ["Invalid num_shards [1.1]. Only integers greater than zero are allowed."]}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous false :num-shards 1.1}))))
     (testing "acls are not supported"
       (is (= {:status 400
               :errors ["Resharding is not allowed for acls or groups."]}
              (bootstrap/start-reshard-index "acls" {:synchronous false :num-shards 1}))))
     (testing "groups are not supported"
       (is (= {:status 400
               :errors ["Resharding is not allowed for acls or groups."]}
              (bootstrap/start-reshard-index "groups" {:synchronous false :num-shards 1}))))
     (testing "shard count must be a number"
       (is (= {:status 400
               :errors ["Invalid num_shards [abc]. Only integers greater than zero are allowed."]}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous false :num-shards "abc"}))))
     (testing "index must exist"
       (is (= {:status 404
               :errors ["Index [1_non-existing-index] does not exist."]}
              (bootstrap/start-reshard-index "1_non-existing-index" {:synchronous false :num-shards 1}))))
     (testing "attempting to reshard an index that is already being resharded fails"
       (is (= {:status 200
               :message "Resharding started for index 1_small_collections"}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous false :num-shards 100})))
       (is (= {:status 400
               :errors ["The index set already contains resharding index [1_small_collections]"]}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous false :num-shards 100})))))))

(deftest reshard-index-success-test
  (s/only-with-real-database
   (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}) {:validate-keywords false})
         _ (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran1"}))
         coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"}) {:validate-keywords false})
         _ (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "gran2"}))]
     (index/wait-until-indexed)
     (testing "resharding an index that does exist"
       (is (= {:status 200
               :message "Resharding started for index 1_small_collections"}
              (bootstrap/start-reshard-index "1_small_collections" {:synchronous false :num-shards 100})))))))
