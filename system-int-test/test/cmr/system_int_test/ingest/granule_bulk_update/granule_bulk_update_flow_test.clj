(ns cmr.system-int-test.ingest.granule-bulk-update.granule-bulk-update-flow-test
  "CMR granule bulk update work flow integration tests."
  (:require
    [cmr.system-int-test.data2.granule :as granule]
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(deftest granule-bulk-update-workflow-test
  (let [bulk-update-options {:token (e/login (s/context) "user1")}
        ;; collection on PROV1
        coll1 (data-core/ingest-umm-spec-collection
               "PROV1" (data-umm-c/collection {:EntryTitle "coll1"
                                               :ShortName "short1"
                                               :Version "V1"
                                               :native-id "native1"}))
        coll2 (data-core/ingest-umm-spec-collection
               "PROV1" (data-umm-c/collection {:EntryTitle "coll2"
                                               :ShortName "short2"
                                               :Version "V2"
                                               :native-id "native2"}))
        gran1 (ingest/ingest-concept
               (data-core/item->concept
                (granule/granule-with-umm-spec-collection
                 coll1
                 (:concept-id coll1)
                 {:native-id "gran-native1-1"
                  :granule-ur "SC:AE_5DSno.002:30500511"})))
        gran2 (ingest/ingest-concept
               (data-core/item->concept
                (granule/granule-with-umm-spec-collection
                 coll1
                 (:concept-id coll1)
                 {:native-id "gran-native1-2"
                  :granule-ur "SC:AE_5DSno.002:30500512"})))
        gran3 (ingest/ingest-concept
               (data-core/item->concept
                (granule/granule-with-umm-spec-collection
                 coll2
                 (:concept-id coll2)
                 {:native-id "gran-native2-3"
                  :granule-ur "SC:coll2:30500513"})))
        ;; collection on PROV2
        coll3 (data-core/ingest-umm-spec-collection
               "PROV2" (data-umm-c/collection {:EntryTitle "coll3"
                                               :ShortName "short3"
                                               :Version "V3"
                                               :native-id "native3"}))
        ;; granule on PROV2
        gran4 (ingest/ingest-concept
               (data-core/item->concept
                (granule/granule-with-umm-spec-collection
                 coll3
                 (:concept-id coll3)
                 {:provider-id "PROV2"
                  :native-id "gran-native3-4"
                  :granule-ur "SC:coll3:30500514"})))]

    (testing "Granule bulk update response in xml/json format"
      (let [update1 {:name "add opendap links"
                     :operation "UPDATE_FIELD"
                     :update-field "OPeNDAPLink"
                     :updates [["SC:AE_5DSno.002:30500511" "https://url30500511"]
                               ["SC:AE_5DSno.002:30500512" "https://url30500512"]]}
            update1-json (json/generate-string update1)
            ;; intentionally made the following call verbose to make sure we are parsing xml
            update1-response (ingest/parse-bulk-update-body
                              :xml
                              (ingest/bulk-update-granules
                               "PROV1"
                               update1
                               (assoc bulk-update-options :accept-format :xml :raw? true)))
            task1-id (:task-id update1-response)
            update2 {:name "add opendap links"
                     :operation "UPDATE_FIELD"
                     :update-field "OPeNDAPLink"
                     :updates [["SC:AE_5DSno.002:30500511" "https://url30500511"]
                               ["SC:coll2:30500513" "https://url30500513"]]}
            update2-json (json/generate-string update2)
            ;; intentionally made the following call verbose to make sure we are parsing JSON
            update2-response (ingest/parse-bulk-update-body
                              :json
                              (ingest/bulk-update-granules
                               "PROV1"
                               update2
                               (assoc bulk-update-options :accept-format :json :raw? true)))
            task2-id (str (:task-id update2-response))
            ;; run granule bulk update on PROV2 to verify get task status works for given provider
            update3 {:operation "UPDATE_FIELD"
                     :update-field "OPeNDAPLink"
                     :updates [["SC:coll3:30500514" "https://url30500514"]
                               ["SC:non-existent" "https://url30500515"]]}
            update3-json (json/generate-string update3)
            update3-response (ingest/bulk-update-granules "PROV2" update3 bulk-update-options)
            task3-id (str (:task-id update3-response))]
        (qb-side-api/wait-for-terminal-states)
        ;; verify granule bulk update status can be retrieved successfully
        ;; which implies the responses are returned in the desired formats
        (is (= 200 (:status update1-response)))
        (is (= 200 (:status update2-response)))
        (is (= 200 (:status update3-response)))

        (testing "Granule bulk update tasks response"
          ;; PROV1
          (are3 [accept-format]
            (let [response (ingest/granule-bulk-update-tasks
                            "PROV1"
                            {:accept-format accept-format})
                  {:keys [status tasks]} response]
              (is (= 200 status))
              (is (= (set [{:task-id task1-id,
                            :name (str "add opendap links: " task1-id)
                            :status-message "All granule updates completed successfully.",
                            :status "COMPLETE",
                            :request-json-body update1-json}
                           {:task-id task2-id,
                            :name (str "add opendap links: " task2-id)
                            :status-message "All granule updates completed successfully.",
                            :status "COMPLETE",
                            :request-json-body update2-json}])
                     (set (map #(dissoc % :created-at) tasks)))))
            "JSON" :json
            "XML" :xml)

          ;; PROV2, also with failing granule
          (are3 [accept-format]
            (let [response (ingest/granule-bulk-update-tasks
                            "PROV2"
                            {:accept-format accept-format})
                  {:keys [status tasks]} response]
              (is (= 200 status))
              (is (= (set [{:task-id task3-id,
                            :name (str task3-id ": " task3-id)
                            :status-message "Task completed with 1 FAILED and 1 UPDATED out of 2 total granule update(s).",
                            :status "COMPLETE",
                            :request-json-body update3-json}])
                     (set (map #(dissoc % :created-at) tasks)))))
            "JSON" :json
            "XML" :xml))))))
