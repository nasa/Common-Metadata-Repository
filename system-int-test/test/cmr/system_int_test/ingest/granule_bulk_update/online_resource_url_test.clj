(ns cmr.system-int-test.ingest.granule-bulk-update.online-resource-url-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest testing is use-fixtures join-fixtures]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]))

(def test-atom (atom {}))

(defn ingest-granule-fixture
  [f]
  (let [coll (data-core/ingest-concept-with-metadata-file
              "echo10-samples/collection.echo10"
              {:provider-id "PROV1"
               :concept-type :collection
               :native-id "online-resource-update-test-coll2"
               :short-name "echo10-collection"
               :format "application/echo10+xml"})
        gran (data-core/ingest-concept-with-metadata-file
                 "echo10-samples/granule-bulk-granule-mime-type-update.echo10"
                 {:provider-id "PROV1"
                  :concept-type :granule
                  :native-id "online-resource-url-update-test-gran2"
                  :format "application/echo10+xml"})]
    (swap! test-atom merge {:collection coll
                            :granule gran})
    (f)
    (reset! test-atom {})))

(use-fixtures :each (join-fixtures [(ingest/reset-fixture {"provguid1" "PROV1"})
                                    ingest-granule-fixture]))

(def no-hits? (complement string/includes?))

(deftest online-resource-url-update-test-echo10-error-cases
  (testing "invalid update json provided fails"
    (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
          bulk-update {:operation "UPDATE_FIELD"
                       :update-field "OnlineResourceURL"
                       :updoots [{"GranuleUR" "mime-type-update-gran-ur"
                                  "Links" [{:to "http://missing-from"}]}]}
          {:keys [status task-id]} (ingest/bulk-update-granules
                                    "PROV1" bulk-update bulk-update-options)
          {:keys [concept-id revision-id]} (:granule @test-atom)]
      (index/wait-until-indexed)
      (ingest/update-granule-bulk-update-task-statuses)

      (is (= 400 status))))

  (testing "no matching url given"
    (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
          bulk-update {:operation "UPDATE_FIELD"
                       :update-field "OnlineResourceURL"
                       :updates [{"GranuleUR" "mime-type-update-gran-ur"
                                  "Links" [{:from "https://no-matching-url"
                                            :to "ftp://link-1-updated"}]}]}
          {:keys [status task-id]} (ingest/bulk-update-granules
                                    "PROV1" bulk-update bulk-update-options)
          {:keys [concept-id revision-id]} (:granule @test-atom)]
      (index/wait-until-indexed)
      (ingest/update-granule-bulk-update-task-statuses)

      ;; verify the granule status is UPDATED
      (is (= 200 status))
      (is (some? task-id))
      (let [status-req-options {:query-params {:show_granules "true"}}
            status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
            {:keys [task-status status-message granule-statuses]} status-response]
        (is (= "COMPLETE" task-status))
        (is (= "Task completed with 1 FAILED out of 1 total granule update(s)." status-message))
        (is (= [{:granule-ur "mime-type-update-gran-ur"
                 :status "FAILED"
                 :status-message "Update failed - please only specify URLs contained in the existing granule OnlineResources [https://no-matching-url] were not found"}]
               granule-statuses))))))

(deftest online-resource-url-update-test-echo10-success-case
  (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
        bulk-update {:operation "UPDATE_FIELD"
                     :update-field "OnlineResourceURL"
                     :updates [{"GranuleUR" "mime-type-update-gran-ur"
                                "Links" [{:from "https://link-1"
                                          :to "ftp://link-1-updated"}]}]}
        {:keys [status task-id]} (ingest/bulk-update-granules
                                  "PROV1" bulk-update bulk-update-options)
        {:keys [concept-id revision-id]} (:granule @test-atom)]
    (index/wait-until-indexed)
    (ingest/update-granule-bulk-update-task-statuses)

    ;; verify the granule status is UPDATED
    (is (= 200 status))
    (is (some? task-id))
    (let [status-req-options {:query-params {:show_granules "true"}}
          status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
          {:keys [task-status status-message granule-statuses]} status-response]
      (is (= "COMPLETE" task-status))
      (is (= "All granule updates completed successfully." status-message))
      (is (= [{:granule-ur "mime-type-update-gran-ur"
               :status "UPDATED"}]
             granule-statuses)))
    ;; verify the granule metadata is updated as expected
    (let [original-metadata (:metadata (mdb/get-concept concept-id revision-id))
          updated-metadata (:metadata (mdb/get-concept concept-id (inc revision-id)))]
      (is (no-hits? original-metadata "ftp://link-1-updated"))
      (is (string/includes? updated-metadata "<URL>ftp://link-1-updated</URL>")))))
