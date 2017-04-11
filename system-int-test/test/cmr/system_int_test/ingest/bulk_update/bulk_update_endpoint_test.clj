(ns cmr.system-int-test.ingest.bulk-update.bulk-update-endpoint-test
  "CMR bulk update endpoint integration tests"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest bulk-update-collection-endpoint
 (testing "Response in JSON"
  (let [response (ingest/bulk-update-collections "PROV1" {} {:accept-format :json :raw? true})]
   (is (= "ABCDEF123"
          (:task-id (ingest/parse-bulk-update-body :json response))))))
 (testing "Response in XML"
  (let [response (ingest/bulk-update-collections "PROV1" {} {:accept-format :xml :raw? true})]
   (is (= "ABCDEF123"
          (:task-id (ingest/parse-bulk-update-body :xml response)))))))

(deftest bulk-update-collection-endpoint-validation
 (testing "Invalid provider"
  (let [response (ingest/bulk-update-collections "PROV-X" {})
        {:keys [status errors]} response]
    (is (= 422 status))
    (is (= ["Provider with provider-id [PROV-X] does not exist."]
           errors)))))
