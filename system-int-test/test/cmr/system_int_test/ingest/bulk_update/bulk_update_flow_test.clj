(ns cmr.system-int-test.ingest.bulk-update.bulk-update-flow-test
  "CMR bulk update queueing flow integration tests. Endpoint validation is handled"
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(deftest bulk-update
  (let [concept-ids (for [x (range 3)]
                      (:concept-id (ingest/ingest-concept
                                     (data-umm-c/collection-concept
                                       (data-umm-c/collection x {})))))
        _ (index/wait-until-indexed)
        bulk-update-body {:concept-ids concept-ids
                          :update-type "ADD_TO_EXISTING"
                          :update-field "SCIENCE_KEYWORDS"
                          :update-value "X"}]

    (testing "Bulk update response"
      (are3 [accept-format task-id]
        (let [response (ingest/parse-bulk-update-body accept-format
                         (ingest/bulk-update-collections "PROV1" bulk-update-body
                           {:accept-format accept-format :raw? true}))]
          (is (= task-id (:task-id response))))
        "JSON" :json 1
        "XML" :xml 2))

    (testing "Provider status response"
      ;; Create another bulk update event with PROV2 to make sure we're just
      ;; getting PROV1 statuses
      (ingest/bulk-update-collections "PROV2" bulk-update-body)

      (are3 [accept-format]
        (let [response (ingest/bulk-update-provider-status "PROV1"
                        {:accept-format accept-format})
              json-body (json/generate-string bulk-update-body)]
          (is (= [{:task-id 1,
                   :status-message nil,
                   :status "COMPLETE",
                   :request-json-body json-body}
                  {:task-id 2,
                   :status-message nil,
                   :status "COMPLETE",
                   :request-json-body json-body}]
                 (:tasks response))))
        "JSON" :json
        "XML" :xml)

     (testing "Provider task status response"
       (are3 [accept-format]
         (let [response (ingest/bulk-update-task-status "PROV1" 1
                         {:accept-format accept-format})]
           (is (= {:status-message nil,
                   :status 200,
                   :task-status "COMPLETE",
                   ;; TO DO: Remove hardcoded concept ids
                   :collection-statuses [{:status-message nil,
                                          :status "COMPLETE",
                                          :concept-id "C1200000001-PROV1"}
                                         {:status-message nil,
                                          :status "COMPLETE",
                                          :concept-id "C1200000002-PROV1"}
                                         {:status-message nil,
                                          :status "COMPLETE",
                                          :concept-id "C1200000003-PROV1"}]}
                  response)))
         "JSON" :json
         "XML" :xml)))))
