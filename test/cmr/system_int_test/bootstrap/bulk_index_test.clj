(ns cmr.system-int-test.bootstrap.bulk_index_test
  "Integration test for CMR bulk indexing."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.umm.echo10.collection :as c]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.config :as config]
            [cmr.dev-system.system :as sys]
            [user :as user]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest bulk-index-after-ingest
  ;; only run this test with the external db
  (when (= (type (get-in user/system [:apps :metadata-db :db]))
           cmr.oracle.connection.OracleStore)
    (let [coll1 (dc/collection {:concept-id "C1-PROV1" :short-name "coll1"})
          coll2 (dc/collection {:concept-id "C2-PROV1" :native-id "coll2"})
          coll3 (dc/collection {:concept-id "C3-PROV1" :native-id "coll3"})
          c1 (d/ingest "PROV1" coll1)
          c11 (d/ingest "PROV1" coll1)
          _ (index/bulk-index-provider "PROV1")]
      (index/refresh-elastic-index)
      ;; We need to wait for bulk indexing to have time to finish since it is asynchronous and we
      ;; don't get a notice when it's done. FIXME - This is problematic as it is inherently a
      ;; race condition. It also slows down the test. Not sure what to do about this right now as
      ;; fixing this would likely involve a lot of work.
      (Thread/sleep 5000)
      (testing "retrieval after bulk indexing returns the latest revision."
        (let [response (search/find-refs :collection {:short-name "coll1"})
              revision-id (:revision-id (first (:refs response)))]
          (is (= 2 revision-id)))))))

