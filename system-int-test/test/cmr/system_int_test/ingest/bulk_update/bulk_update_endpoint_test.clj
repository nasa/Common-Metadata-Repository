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

(deftest bulk-update-status-endpoint
 (testing "Response in JSON"
  (let [response (ingest/bulk-update-provider-status "PROV1" {:accept-format :json})]
   (is (= [{:task-id "ABCDEF123"
            :status "In Progress"}
           {:task-id "12345678"
            :status "Partial Fail"
            :status-message "The following collections had errors: C-1, C-2"}
           {:task-id "XYZ123456"
            :status "Complete"}]
          (:tasks response)))))
 (testing "Response in XML"
  (let [response (ingest/bulk-update-provider-status "PROV1" {:accept-format :xml})]
   (is (= [{:task-id "ABCDEF123"
            :status "In Progress"}
           {:task-id "12345678"
            :status "Partial Fail"
            :status-message "The following collections had errors: C-1, C-2"}
           {:task-id "XYZ123456"
            :status "Complete"}]
          (:tasks response))))))

(deftest bulk-update-status-endpoint-validation
 (testing "Invalid provider"
  (let [response (ingest/bulk-update-provider-status "PROV-X")
        {:keys [status errors]} response]
    (is (= 422 status))
    (is (= ["Provider with provider-id [PROV-X] does not exist."]
           errors)))))

(deftest bulk-update-task-status-endpoint
 (testing "Response in JSON"
  (let [response (ingest/bulk-update-task-status "PROV1" "1" {:accept-format :json})]
   (is (= {:status 200
           :task-status "Partial Fail"
           :status-message "The following collections had errors: C-1, C-2"
           :collection-statuses [{:concept-id "C1-PROV"
                                  :status "Failed"
                                  :status-message "Missing required properties"}
                                 {:concept-id "C2-PROV"
                                  :status "Failed"
                                  :status-message "Invalid XML"}]}
          response))))
 (testing "Response in XML"
  (let [response (ingest/bulk-update-task-status "PROV1" "1" {:accept-format :xml})]
   (is (= {:status 200
           :task-status "Partial Fail"
           :status-message "The following collections had errors: C-1, C-2"
           :collection-statuses [{:concept-id "C1-PROV"
                                  :status "Failed"
                                  :status-message "Missing required properties"}
                                 {:concept-id "C2-PROV"
                                  :status "Failed"
                                  :status-message "Invalid XML"}]}
          response)))))

(deftest bulk-update-task-status-endpoint-validation
 (testing "Invalid provider"
  (let [response (ingest/bulk-update-task-status "PROV-X" "1")
        {:keys [status errors]} response]
    (is (= 422 status))
    (is (= ["Provider with provider-id [PROV-X] does not exist."]
           errors)))))
