(ns cmr.system-int-test.bootstrap.bulk-index.tools-no-permission-test
  "Integration test for CMR bulk index tool operations with no permissions."
  (:require
   [clojure.test :refer :all]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"
                                             "provguid2" "PROV2"
                                             "provguid3" "PROV3"}
                                            {:grant-all-ingest? true
                                             :grant-all-search? true
                                             :grant-all-access-control? false})]))

(deftest ^:oracle bulk-index-tools-no-permission
  (testing "Bulk index tools for a single provider without a token"
    (system/only-with-real-database
      (let [{:keys [status errors]} (bootstrap/bulk-index-tools "PROV1" nil)]
         ;; The above bulk-index-tools call with nil headers has no token
         (is (= [401 ["You do not have permission to perform that action."]]
                [status errors])))))
  (testing "Bulk index tools for all providers without a token"
    (system/only-with-real-database
      (let [{:keys [status errors]} (bootstrap/bulk-index-tools nil nil nil)]
         ;; The above bulk-index-tools call with nil headers has no token
         (is (= [401 ["You do not have permission to perform that action."]]
                [status errors]))))))
