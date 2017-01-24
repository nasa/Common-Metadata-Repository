(ns cmr.system-int-test.ingest.enable-disable-test
  "CMR Ingest Enable/Disable endpoint test"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.utils.metadata-db-util :as mdb]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest enable-disable-enable-ingest
  (testing "ingest of a new concept before disable"
    (let [concept (dc/collection-concept {})
          {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
      (index/wait-until-indexed)
      (is (mdb/concept-exists-in-mdb? concept-id revision-id))
      (is (= 1 revision-id))))

  (ingest/disable-ingest)
  
  (testing "ingest of a new concept after disable"
    (let [concept (dc/collection-concept {})
          {:keys [status]} (ingest/ingest-concept concept)]
      (is (= 503 status))))
  
  (ingest/enable-ingest)

  (testing "ingest of a new concept after enable"
    (let [concept (dc/collection-concept {})
          {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
      (index/wait-until-indexed)
      (is (mdb/concept-exists-in-mdb? concept-id revision-id))
      (is (= 1 revision-id)))))
  