(ns cmr.system-int-test.ingest.granule-bulk-update.granule-bulk-update-test
  "CMR bulk update. Test the actual update "
  (:require
   [clojure.test :refer :all]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                       (search/freeze-resume-time-fixture)]))

(deftest bulk-granule-update-test
 (testing "valid request is accepted "
   (let [bulk-update {:name "bulk update prov1 granules"
                      :update-field "foo"
                      :operation "UPDATE_FIELD"
                      :updates [["bar" "baz"]]}
         response (ingest/bulk-update-granules "PROV1"
                                               bulk-update
                                               {:raw? true})]
     (is (= 422 (:status response)))))

 (testing "invalid schema is rejected"
   (let [bulk-update {:name "bulk update prov1 granules"
                      :update-field "foo"
                      :updates [["bar" "baz"]]}
         {:keys [status body]} (ingest/bulk-update-granules "PROV1"
                                                            bulk-update
                                                            {:raw? true})]
     ;; valdation error is returned
     (is (= 422 status))
     (is (re-find #"required key \[operation\] not found" body) body))))
