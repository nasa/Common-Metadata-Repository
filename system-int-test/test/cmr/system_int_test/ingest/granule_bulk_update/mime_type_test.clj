(ns cmr.system-int-test.ingest.granule-bulk-update.mime-type-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest testing is use-fixtures]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest mime-type-update-umm-g-test
  (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
        coll (data-core/ingest-concept-with-metadata-file
              "umm-g-samples/Collection.json"
              {:provider-id "PROV1"
               :concept-type :collection
               :native-id "test-coll1"
               :format "application/vnd.nasa.cmr.umm+json;version=1.16"})
        granule (data-core/ingest-concept-with-metadata-file
                 "umm-g-samples/GranuleExample.json"
                 {:provider-id "PROV1"
                  :concept-type :granule
                  :native-id "test-gran1"
                  :format "application/vnd.nasa.cmr.umm+json;version=1.6"})
        {:keys [concept-id revision-id]} granule]
    (testing "failure from invalid mime type"
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "MimeType"
                         :updates [{"GranuleUR" "Unique_Granule_UR_v1.6"
                                    "Links" [{:URL (str "https://daac.ornl.gov/daacdata/islscp_ii/"
                                                        "vegetation/erbe_albedo_monthly_xdeg/data/"
                                                        "erbe_albedo_1deg_1986.zip")
                                              :MimeType "application/foobar"}]}]}
            {:keys [status task-id]} (ingest/bulk-update-granules
                                      "PROV1" bulk-update bulk-update-options)]
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
          (is (= [{:granule-ur "Unique_Granule_UR_v1.6"
                   :status "FAILED"
                   :status-message "#/RelatedUrls/0/MimeType: application/foobar is not a valid enum value"}]
                 granule-statuses)))
        ;; verify the granule metadata is NOT updated
        (is (not (:metadata (mdb/get-concept concept-id (inc revision-id)))))))
    (testing "failure from URL not in granule"
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "MimeType"
                         :updates [{"GranuleUR" "Unique_Granule_UR_v1.6"
                                    "Links" [{:URL "www.example.com/1"
                                              :MimeType "application/zip"}]}]}
            {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                   "PROV1" bulk-update bulk-update-options)]
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
          (is (= [{:granule-ur "Unique_Granule_UR_v1.6"
                   :status "FAILED"
                   :status-message "Update failed - please only specify URLs contained in the existing granule RelatedURLs"}]
                 granule-statuses)))
        ;; verify the granule metadata is NOT updated
        (is (not (:metadata (mdb/get-concept concept-id (inc revision-id)))))))
    (testing "failure from duplicate links"
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "MimeType"
                         :updates [{"GranuleUR" "Unique_Granule_UR_v1.6"
                                    "Links" [{:URL (str "https://daac.ornl.gov/daacdata/islscp_ii/"
                                                        "vegetation/erbe_albedo_monthly_xdeg/data/"
                                                        "erbe_albedo_1deg_1986.zip")
                                              :MimeType "application/zip"}
                                             {:URL (str "https://daac.ornl.gov/daacdata/islscp_ii/"
                                                        "vegetation/erbe_albedo_monthly_xdeg/data/"
                                                        "erbe_albedo_1deg_1986.zip")
                                              :MimeType "application/json"}]}]}
            {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                   "PROV1" bulk-update bulk-update-options)]
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
          (is (= [{:granule-ur "Unique_Granule_UR_v1.6"
                   :status "FAILED"
                   :status-message "Update failed - duplicate URLs provided for granule update"}]
                 granule-statuses)))
        ;; verify the granule metadata is NOT updated
        (is (not (:metadata (mdb/get-concept concept-id (inc revision-id)))))))
    (testing "actually update granule"
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "MimeType"
                         :updates [{"GranuleUR" "Unique_Granule_UR_v1.6"
                                    "Links" [{:URL (str "https://daac.ornl.gov/daacdata/islscp_ii/"
                                                        "vegetation/erbe_albedo_monthly_xdeg/data/"
                                                        "erbe_albedo_1deg_1986.zip")
                                              :MimeType "application/gzip"}]}]}
            {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                   "PROV1" bulk-update bulk-update-options)]
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
          (is (= [{:granule-ur "Unique_Granule_UR_v1.6"
                   :status "UPDATED"}]
                 granule-statuses)))
        ;; verify the granule metadata is updated as expected
        (let [original-metadata (:metadata (mdb/get-concept concept-id revision-id))
              updated-metadata (:metadata (mdb/get-concept concept-id (inc revision-id)))]
          ;; since the metadata will be returned in the latest UMM-G format,
          ;; we can't compare the whole metadata to an expected string.
          ;; We just verify the updated S3 url, type and description exists in the metadata.
          ;; The different scenarios of the RelatedUrls update are covered in unit tests.
          (is (not (string/includes? original-metadata "application/gzip")))
          (is (string/includes? updated-metadata "application/gzip")))))))

(deftest mime-type-update-echo10-test
  (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
        coll (data-core/ingest-concept-with-metadata-file
              "echo10-samples/collection.echo10"
              {:provider-id "PROV1"
               :concept-type :collection
               :native-id "mime-type-test-coll2"
               :short-name "echo10-collection"
               :format "application/echo10+xml"})
        granule (data-core/ingest-concept-with-metadata-file
                 "echo10-samples/granule-bulk-granule-mime-type-update.echo10"
                 {:provider-id "PROV1"
                  :concept-type :granule
                  :native-id "mime-type-test-gran2"
                  :format "application/echo10+xml"})
        {:keys [concept-id revision-id]} granule]

    (testing "failure from duplicate links"
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "MimeType"
                         :updates [{"GranuleUR" "mime-type-update-gran-ur"
                                    "Links" [{:URL "https://link-1"
                                              :MimeType "application/zip"}
                                             {:URL "https://link-1"
                                              :MimeType "application/json"}]}]}
            {:keys [status task-id]} (ingest/bulk-update-granules
                                      "PROV1" bulk-update bulk-update-options)]
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
                   :status-message (str "Update failed - duplicate URLs provided for granule update"
                                        " [https://link-1]")}]
                 granule-statuses)))
        ;; verify the granule metadata is NOT updated
        (is (not (:metadata (mdb/get-concept concept-id (inc revision-id)))))))

    (testing "failure from unmatched links"
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "MimeType"
                         :updates [{"GranuleUR" "mime-type-update-gran-ur"
                                    "Links" [{:URL "https://unmatched-link"
                                              :MimeType "application/zip"}
                                             {:URL "https://wrong-link"
                                              :MimeType "application/zip"}]}]}
            {:keys [status task-id]} (ingest/bulk-update-granules
                                      "PROV1" bulk-update bulk-update-options)]
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
                   :status-message (str "Update failed - please only specify URLs contained"
                                        " in the existing granule OnlineResources or OnlineAccessURLs"
                                        " [https://wrong-link, https://unmatched-link] were not found")}]
                 granule-statuses)))
        ;; verify the granule metadata is NOT updated
        (is (not (:metadata (mdb/get-concept concept-id (inc revision-id)))))))

    (testing "actually update granule"
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "MimeType"
                         :updates [{"GranuleUR" "mime-type-update-gran-ur"
                                    "Links" [{:URL "https://link-1"
                                              :MimeType "application/gzip"}]}]}
            {:keys [status task-id]} (ingest/bulk-update-granules
                                      "PROV1" bulk-update bulk-update-options)]
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
          (is (not (string/includes? original-metadata "application/gzip")))
          (is (string/includes? updated-metadata "application/gzip")))))))
