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


(deftest bulk-update-sorting
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
                                         :VariableLevel1 "HEAVY METALS CONCENTRATION"}}]
    (testing "Provider task status response sorting"
       (let [response-json1 (ingest/parse-bulk-update-body :json
                             (ingest/bulk-update-collections "PROV1"
                              (assoc bulk-update-body :name "TEST NAME 1")
                              {:accept-format :json :raw? true}))
             response-json2 (ingest/parse-bulk-update-body :json
                             (ingest/bulk-update-collections "PROV1"
                              (assoc bulk-update-body :name "TEST NAME 2")
                              {:accept-format :json :raw? true}))
             response-json3 (ingest/parse-bulk-update-body :json
                             (ingest/bulk-update-collections "PROV1"
                              (assoc bulk-update-body :name "TEST NAME 3")
                              {:accept-format :json :raw? true}))
             response-json4 (ingest/parse-bulk-update-body :json
                             (ingest/bulk-update-collections "PROV1"
                              (assoc bulk-update-body :name "TEST NAME 4")
                              {:accept-format :json :raw? true}))
             response-json5 (ingest/parse-bulk-update-body :json
                             (ingest/bulk-update-collections "PROV1"
                              (assoc bulk-update-body :name "TEST NAME 5")
                              {:accept-format :json :raw? true}))
             response-json6 (ingest/parse-bulk-update-body :json
                             (ingest/bulk-update-collections "PROV1"
                              (assoc bulk-update-body :name "TEST NAME 6")
                              {:accept-format :json :raw? true}))
             response-json7 (ingest/parse-bulk-update-body :json
                             (ingest/bulk-update-collections "PROV1"
                              (assoc bulk-update-body :name "TEST NAME 7")
                              {:accept-format :json :raw? true}))
             response-json8 (ingest/parse-bulk-update-body :json
                             (ingest/bulk-update-collections "PROV1"
                              (assoc bulk-update-body :name "TEST NAME 8")
                              {:accept-format :json :raw? true}))
             response-json9 (ingest/parse-bulk-update-body :json
                             (ingest/bulk-update-collections "PROV1"
                              (assoc bulk-update-body :name "TEST NAME 9")
                              {:accept-format :json :raw? true}))
             response-json10 (ingest/parse-bulk-update-body :json
                             (ingest/bulk-update-collections "PROV1"
                              (assoc bulk-update-body :name "TEST NAME 10")
                              {:accept-format :json :raw? true}))
             response-json11 (ingest/parse-bulk-update-body :json
                             (ingest/bulk-update-collections "PROV1"
                              (assoc bulk-update-body :name "TEST NAME 11")
                              {:accept-format :json :raw? true}))
             _ (println "response-json10: " response-json11)
             response (ingest/bulk-update-provider-status
                       "PROV1"
                       {:accept-format :json})
             {:keys [status tasks]} response]
         (is (= 200 status))
         (is (= ["11" "10" "9" "8" "7" "6" "5" "4" "3" "2" "1"]
                (map :task-id tasks)))))))

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
        json-body1 (json/generate-string (assoc bulk-update-body :name "TEST NAME 1"))
        json-body2 (json/generate-string (assoc bulk-update-body :name "TEST NAME 2"))]

    (testing "Bulk update response")
    (let [response-json (ingest/parse-bulk-update-body :json
                          (ingest/bulk-update-collections "PROV1" 
                            (assoc bulk-update-body :name "TEST NAME 1")
                            {:accept-format :json :raw? true}))
          response-xml (ingest/parse-bulk-update-body :xml
                         (ingest/bulk-update-collections "PROV1" 
                           (assoc bulk-update-body :name "TEST NAME 2")
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
                          :name "TEST NAME 1"
                          :status-message "All collection updates completed successfully.",
                          :status "COMPLETE",
                          :request-json-body json-body1}
                         {:task-id task-id-2,
                          :name "TEST NAME 2"
                          :status-message "All collection updates completed successfully.",
                          :status "COMPLETE",
                          :request-json-body json-body2}])
                   (set (map #(dissoc % :created-at) (:tasks response))))))
          "JSON" :json
          "XML" :xml))

     (testing "Provider task status response"
       (are3 [accept-format]
         (let [response (ingest/bulk-update-task-status "PROV1" task-id-1
                         {:accept-format accept-format})
               status "Collection was updated successfully, but translating the collection to UMM-C had the following issues: [:ScienceKeywords 1] Science keyword Category [EARTH SCIENCE], Topic [HUMAN DIMENSIONS], Term [ENVIRONMENTAL IMPACTS], and Variable Level 1 [HEAVY METALS CONCENTRATION] was not a valid keyword combination."]
           (is (= {:status-message "All collection updates completed successfully.",
                   :status 200,
                   :name "TEST NAME 1"
                   :task-status "COMPLETE",
                   :request-json-body json-body1
                   :collection-statuses [{:status-message status,
                                          :status "UPDATED",
                                          :concept-id "C1200000000-PROV1"}
                                         {:status-message status,
                                          :status "UPDATED",
                                          :concept-id "C1200000001-PROV1"}
                                         {:status-message status,
                                          :status "UPDATED",
                                          :concept-id "C1200000002-PROV1"}]}
                  (dissoc response :created-at))))
         "JSON" :json
         "XML" :xml)))))

(deftest bulk-update-invalid-concept-id
  (let [bulk-update-body {:concept-ids ["C1200000100-PROV1" "C111-PROV2"]
                          :name "TEST NAME"
                          :update-type "ADD_TO_EXISTING"
                          :update-field "SCIENCE_KEYWORDS"
                          :update-value {:Category "EARTH SCIENCE"
                                         :Topic "HUMAN DIMENSIONS"
                                         :Term "ENVIRONMENTAL IMPACTS"
                                         :VariableLevel1 "HEAVY METALS CONCENTRATION"}}
        json-body (json/generate-string bulk-update-body)
        {:keys [task-id]} (ingest/bulk-update-collections "PROV1" bulk-update-body)
        _ (qb-side-api/wait-for-terminal-states)
        status-response (ingest/bulk-update-task-status "PROV1" task-id)
        status-response (dissoc status-response :created-at)]
    (is (= {:status-message "Task completed with 2 FAILED out of 2 total collection update(s).",
            :status 200,
            :name "TEST NAME"
            :request-json-body json-body
            :task-status "COMPLETE",
            :collection-statuses (set [{:status-message "Concept-id [C1200000100-PROV1] does not exist.",
                                        :status "FAILED",
                                        :concept-id "C1200000100-PROV1"}
                                       {:status-message (str "Concept-id [C111-PROV2] is not associated "
                                                             "with provider-id [PROV1]."),
                                        :status "FAILED",
                                        :concept-id "C111-PROV2"}])}
           (update status-response :collection-statuses set)))))
