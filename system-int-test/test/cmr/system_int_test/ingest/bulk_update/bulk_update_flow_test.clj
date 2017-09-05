(ns cmr.system-int-test.ingest.bulk-update.bulk-update-flow-test
  "CMR bulk update queueing flow integration tests. Endpoint validation is handled"
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(defn- generate-concept-id
  [index provider]
  (format "C120000000%s-%s" index provider))

(deftest bulk-update-success
  (let [concept-ids (doall (for [x (range 3)]
                             (:concept-id (ingest/ingest-concept
                                            (assoc
                                              (data-umm-c/collection-concept
                                                (data-umm-c/collection x {}))
                                              :concept-id
                                              (generate-concept-id x "PROV1"))))))
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids concept-ids
                          :update-type "ADD_TO_EXISTING"
                          :update-field "SCIENCE_KEYWORDS"
                          :update-value {:Category "EARTH SCIENCE"
                                         :Topic "HUMAN DIMENSIONS"
                                         :Term "ENVIRONMENTAL IMPACTS"
                                         :VariableLevel1 "HEAVY METALS CONCENTRATION"}}
        json-body (json/generate-string bulk-update-body)]

    (testing "Bulk update response")
    (let [response-json (ingest/parse-bulk-update-body :json
                          (ingest/bulk-update-collections "PROV1" bulk-update-body
                            {:accept-format :json :raw? true}))
          response-xml (ingest/parse-bulk-update-body :xml
                         (ingest/bulk-update-collections "PROV1" bulk-update-body
                           {:accept-format :xml :raw? true}))
          task-id-1 (str (:task-id response-json))
          task-id-2 (:task-id response-xml)]
      (is (= 200 (:status response-json)))
      (is (= 200 (:status response-xml)))

      (testing "Provider status response"
        ;; Create another bulk update event with PROV2 to make sure we're just
        ;; getting PROV1 statuses
        (ingest/bulk-update-collections "PROV2" bulk-update-body)
        (qb-side-api/wait-for-terminal-states)

        (are3 [accept-format]
          (let [response (ingest/bulk-update-provider-status "PROV1"
                          {:accept-format accept-format})]
            (is (= (set [{:task-id task-id-1,
                          :status-message "All collection updates completed successfully.",
                          :status "COMPLETE",
                          :request-json-body json-body}
                         {:task-id task-id-2,
                          :status-message "All collection updates completed successfully.",
                          :status "COMPLETE",
                          :request-json-body json-body}])
                   (set (:tasks response)))))
          "JSON" :json
          "XML" :xml))

     (testing "Provider task status response"
       (are3 [accept-format]
         (let [response (ingest/bulk-update-task-status "PROV1" task-id-1
                         {:accept-format accept-format})]
           (is (= {:status-message "All collection updates completed successfully.",
                   :status 200,
                   :task-status "COMPLETE",
                   :request-json-body json-body
                   :collection-statuses [{:status-message nil,
                                          :status "COMPLETE",
                                          :concept-id "C1200000000-PROV1"}
                                         {:status-message nil,
                                          :status "COMPLETE",
                                          :concept-id "C1200000001-PROV1"}
                                         {:status-message nil,
                                          :status "COMPLETE",
                                          :concept-id "C1200000002-PROV1"}]}
                  response)))
         "JSON" :json
         "XML" :xml)))))

(deftest bulk-update-invalid-concept-id
  (let [bulk-update-body {:concept-ids ["C1200000100-PROV1" "C111"]
                          :update-type "ADD_TO_EXISTING"
                          :update-field "SCIENCE_KEYWORDS"
                          :update-value {:Category "EARTH SCIENCE"
                                         :Topic "HUMAN DIMENSIONS"
                                         :Term "ENVIRONMENTAL IMPACTS"
                                         :VariableLevel1 "HEAVY METALS CONCENTRATION"}}
        json-body (json/generate-string bulk-update-body)
        {:keys [task-id]} (ingest/bulk-update-collections "PROV1" bulk-update-body)
        _ (qb-side-api/wait-for-terminal-states)
        status-response (ingest/bulk-update-task-status "PROV1" task-id)]
    (is (= {:status-message "Task completed with 2 collection update failures out of 2",
            :status 200,
            :request-json-body json-body
            :task-status "COMPLETE",
            :collection-statuses (set [{:status-message "Concept-id [C1200000100-PROV1] is not valid.",
                                        :status "FAILED",
                                        :concept-id "C1200000100-PROV1"}
                                       {:status-message "Concept-id [C111] is not valid.",
                                        :status "FAILED",
                                        :concept-id "C111"}])}
           (update status-response :collection-statuses set)))))
