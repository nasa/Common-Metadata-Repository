(ns cmr.system-int-test.bootstrap.bulk-index.variables-test
  "Integration test for CMR bulk index variable operations."
  (:require
   [clojure.test :refer :all]
   [cmr.system-int-test.bootstrap.bulk-index.core :as core]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.variable-util :as variable]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"
                                             "provguid2" "PROV2"
                                             "provguid3" "PROV3"}
                                            {:grant-all-ingest? true
                                             :grant-all-search? true
                                             :grant-all-access-control? false})]))

(deftest bulk-index-variables-for-provider
  (testing "Bulk index variables for a single provider"
    (s/only-with-real-database
      ;; Disable message publishing so items are not indexed.
      (core/disable-automatic-indexing)
      ;; The following is saved, but not indexed due to the above call
      (let [var1 (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 1)]
        (is (= 0 (:hits (variable/search {}))))
        (bootstrap/bulk-index-variables "PROV1")
        (index/wait-until-indexed)
        (testing "Variable concepts are indexed."
          (let [{:keys [hits items]} (variable/search {})]
            (is (= 1 hits))
            (is (= (:concept-id var1)
                   (:concept-id (first items)))))))
      (testing "Bulk index multilpe variables for a single provider")
      (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 2)
      (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 3)
      (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 4)
      (is (= 1 (:hits (variable/search {}))))
      (bootstrap/bulk-index-variables "PROV1")
      (index/wait-until-indexed)
      (let [{:keys [hits items]} (variable/search {})]
        (is (= 4 hits))
        (is (= 4 (count items))))
      ;; Re-enable message publishing.
      (core/reenable-automatic-indexing))))

(deftest bulk-index-variables
  (testing "Bulk index variables for multiple providers, explicitly"
    (s/only-with-real-database
      ;; Disable message publishing so items are not indexed.
      (core/disable-automatic-indexing)
      ;; The following are saved, but not indexed due to the above call
      (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 1)
      (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 2)
      (variable/ingest-variable-with-attrs {:provider-id "PROV2"} {} 3)
      (variable/ingest-variable-with-attrs {:provider-id "PROV2"} {} 4)
      (variable/ingest-variable-with-attrs {:provider-id "PROV3"} {} 5)
      (variable/ingest-variable-with-attrs {:provider-id "PROV3"} {} 6)
      (is (= 0 (:hits (variable/search {}))))
      (bootstrap/bulk-index-variables "PROV1")
      (bootstrap/bulk-index-variables "PROV2")
      (bootstrap/bulk-index-variables "PROV3")
      (index/wait-until-indexed)
      (testing "Variable concepts are indexed."
        (let [{:keys [hits items]} (variable/search {})]
          (is (= 6 hits))
          (is (= 6 (count items)))))
      ;; Re-enable message publishing.
      (core/reenable-automatic-indexing))))

(deftest bulk-index-all-variables
  (testing "Bulk index variables for multiple providers, implicitly"
    (s/only-with-real-database
      ;; Disable message publishing so items are not indexed.
      (core/disable-automatic-indexing)
      ;; The following are saved, but not indexed due to the above call
      (variable/ingest-variable-with-attrs {:provider-id "PROV1"} {} 1)
      (variable/ingest-variable-with-attrs {:provider-id "PROV2"} {} 2)
      (variable/ingest-variable-with-attrs {:provider-id "PROV3"} {} 3)
      (is (= 0 (:hits (variable/search {}))))
      (bootstrap/bulk-index-variables)
      (index/wait-until-indexed)
      (testing "Variable concepts are indexed."
        (let [{:keys [hits items]} (variable/search {})]
          (is (= 3 hits))
          (is (= 3 (count items)))))
      ;; Re-enable message publishing.
      (core/reenable-automatic-indexing))))
