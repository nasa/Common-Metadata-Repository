(ns cmr.system-int-test.ingest.bulk-update.bulk-update-endpoint-test
  "CMR bulk update endpoint integration tests"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def test-body
  "Default request body to use for testing"
  {:concept-ids ["C1", "C2", "C3"]
   :update-type "ADD_TO_EXISTING"
   :update-field "SCIENCE_KEYWORDS"
   :update-value "X"})

(deftest bulk-update-collection-endpoint
  (testing "Response in JSON"
    (let [response (ingest/bulk-update-collections "PROV1" test-body {:accept-format :json :raw? true})]
      (is (= "ABCDEF123"
             (:task-id (ingest/parse-bulk-update-body :json response))))))
  (testing "Response in XML"
    (let [response (ingest/bulk-update-collections "PROV1" test-body {:accept-format :xml :raw? true})]
      (is (= "ABCDEF123"
             (:task-id (ingest/parse-bulk-update-body :xml response)))))))

(deftest bulk-update-collection-endpoint-validation
  (testing "Invalid provider"
    (let [response (ingest/bulk-update-collections "PROV-X" {})
          {:keys [status errors]} response]
      (is (= 422 status))
      (is (= ["Provider with provider-id [PROV-X] does not exist."]
             errors)))))

(deftest bulk-update-collection-endpoint-body-validation
  (testing "Request body validation"
    (are3 [body status-code error-messages]
          (let [response (ingest/bulk-update-collections "PROV1" body)
                {:keys [status errors]} response]
            (is (= status-code status))
            (is (= error-messages errors)))

          "Missing collection identifiers"
          {:update-field "SCIENCE_KEYWORDS"
           :update-type "ADD_TO_EXISTING"}
          400
          ["instance failed to match exactly one schema (matched 0 out of 2)"
           "object has missing required properties ([\"concept-ids\"])"
           "object has missing required properties ([\"collection-ids\"])"]

          "0 concept-ids"
          {:concept-ids []
           :update-field "SCIENCE_KEYWORDS"
           :update-type "ADD_TO_EXISTING"}
          400
          ["/concept-ids array is too short: must have at least 1 elements but instance has 0 elements"]

          "0 collection ids"
          {:collection-ids []
           :update-field "SCIENCE_KEYWORDS"
           :update-type "ADD_TO_EXISTING"}
          400
          ["/collection-ids array is too short: must have at least 1 elements but instance has 0 elements"]

          "Missing short-name"
          {:collection-ids [{:version "V1"}]
           :update-field "SCIENCE_KEYWORDS"
           :update-type "ADD_TO_EXISTING"}
          400
          ["/collection-ids/0 object has missing required properties ([\"short-name\"])"]

          "Missing version"
          {:collection-ids [{:short-name "ABC"}]
           :update-field "SCIENCE_KEYWORDS"
           :update-type "ADD_TO_EXISTING"}
          400
          ["/collection-ids/0 object has missing required properties ([\"version\"])"]

          "Missing update field"
          {:concept-ids ["C1", "C2", "C3"]
           :update-type "ADD_TO_EXISTING"}
          400
          ["object has missing required properties ([\"update-field\"])"]

          "Missing update type"
          {:concept-ids ["C1", "C2", "C3"]
           :update-field "SCIENCE_KEYWORDS"}
          400
          ["object has missing required properties ([\"update-type\"])"]

          "Invalid update type"
          {:concept-ids ["C1", "C2", "C3"]
           :update-field "SCIENCE_KEYWORDS"
           :update-type "REPLACE"}
          400
          ["/update-type instance value (\"REPLACE\") not found in enum (possible values: [\"ADD_TO_EXISTING\",\"CLEAR_FIELD\",\"CLEAR_ALL_AND_REPLACE\",\"FIND_AND_REMOVE\",\"FIND_AND_REPLACE\"])"]

          "Missing update value"
          {:concept-ids ["C1", "C2", "C3"]
           :update-field "SCIENCE_KEYWORDS"
           :update-type "FIND_AND_REPLACE"}
          400
          ["An update value must be supplied when the update is of type FIND_AND_REPLACE"]

          "Missing find value"
          {:concept-ids ["C1", "C2", "C3"]
           :update-field "SCIENCE_KEYWORDS"
           :update-type "FIND_AND_REPLACE"
           :update-value "X"}
          400
          ["A find value must be supplied when the update is of type FIND_AND_REPLACE"])))

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
