(ns cmr.system-int-test.ingest.granule-bulk-update.granule-bulk-update-test
  "CMR bulk update. Test the actual update "
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.granule :as granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest bulk-granule-update-test
  (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
        coll1 (data-core/ingest-umm-spec-collection
               "PROV1" (data-umm-c/collection {:EntryTitle "coll1"
                                               :ShortName "short1"
                                               :Version "V1"
                                               :native-id "native1"}))
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
        ;; this granule will fail bulk update as it is in ISO-SMAP format
        gran3 (ingest/ingest-concept
               (data-core/item->concept
                (granule/granule-with-umm-spec-collection
                 coll1
                 (:concept-id coll1)
                 {:native-id "gran-native1-3"
                  :granule-ur "SC:AE_5DSno.002:30500513"})
                :iso-smap))
        ;; UMM-G granule
        gran4 (ingest/ingest-concept
               (data-core/item->concept
                (granule/granule-with-umm-spec-collection
                 coll1
                 (:concept-id coll1)
                 {:native-id "gran-native1-4"
                  :granule-ur "SC:AE_5DSno.002:30500514"})
                :umm-json))
        gran5 (ingest/ingest-concept
               (data-core/item->concept
                (granule/granule-with-umm-spec-collection
                 coll1
                 (:concept-id coll1)
                 {:native-id "gran-native1-5"
                  :granule-ur "SC:AE_5DSno.002:30500515"})))]

    (testing "OPeNDAP url granule bulk update"
      (testing "successful OPeNDAP url granule bulk update"
        (let [bulk-update {:name "add opendap links 1"
                           :operation "UPDATE_FIELD"
                           :update-field "OPeNDAPLink"
                           :updates [["SC:AE_5DSno.002:30500511" "https://url30500511"]
                                     ["SC:AE_5DSno.002:30500512" "https://url30500512"]
                                     ["SC:AE_5DSno.002:30500514" "https://url30500514"]]}
              response (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)
              {:keys [status task-id]} response]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          (is (= 200 status))
          (is (some? task-id))
          (let [status-response (ingest/granule-bulk-update-task-status task-id)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= "All granule updates completed successfully." status-message))
            (is (= [{:granule-ur "SC:AE_5DSno.002:30500511"
                     :status "UPDATED"}
                    {:granule-ur "SC:AE_5DSno.002:30500512"
                     :status "UPDATED"}
                    {:granule-ur "SC:AE_5DSno.002:30500514"
                     :status "UPDATED"}]
                   granule-statuses)))))

      (testing "failed OPeNDAP url granule bulk update : UPDATE_FIELD"
        (let [bulk-update {:name "add opendap links 2"
                           :operation "UPDATE_FIELD"
                           :update-field "OPeNDAPLink"
                           :updates [["SC:AE_5DSno.002:30500513" "https://url30500513"]]}
              response (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)
              {:keys [status task-id]} response]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          (is (= 200 status))
          (is (some? task-id))
          (let [status-response (ingest/granule-bulk-update-task-status task-id)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= "Task completed with 1 FAILED out of 1 total granule update(s)." status-message))
            (is (= [{:granule-ur "SC:AE_5DSno.002:30500513"
                     :status "FAILED"
                     :status-message "Adding OPeNDAP url is not supported for format [application/iso:smap+xml]"}]
                   granule-statuses)))))

      (testing "partial successful OPeNDAP url granule bulk update"
        (let [bulk-update {:name "add opendap links 3"
                           :operation "UPDATE_FIELD"
                           :update-field "OPeNDAPLink"
                           :updates [["SC:AE_5DSno.002:30500511" "https://url30500511"]
                                     ["SC:AE_5DSno.002:30500512" "https://url30500512"]
                                     ["SC:non-existent-ur" "https://url30500513"]]}
              response (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)
              {:keys [status task-id]} response]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          (is (= 200 status))
          (is (some? task-id))
          (let [status-response (ingest/granule-bulk-update-task-status task-id)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= "Task completed with 1 FAILED and 2 UPDATED out of 3 total granule update(s)."
                   status-message))
            (is (= [{:granule-ur "SC:AE_5DSno.002:30500511"
                     :status "UPDATED"}
                    {:granule-ur "SC:AE_5DSno.002:30500512"
                     :status "UPDATED"}
                    {:granule-ur "SC:non-existent-ur"
                     :status "FAILED"
                     :status-message (format "Granule UR [SC:non-existent-ur] in task-id [%s] does not exist."
                                             task-id)}]
                   granule-statuses)))))
      (testing "invalid OPeNDAP url value in instruction"
        (let [bulk-update {:name "add opendap links 4"
                           :operation "UPDATE_FIELD"
                           :update-field "OPeNDAPLink"
                           :updates [["SC:AE_5DSno.002:30500511" "https://foo,https://bar,https://baz"]
                                     ["SC:AE_5DSno.002:30500512" "https://foo, https://bar"]
                                     ["SC:AE_5DSno.002:30500514" "https://opendap.sit.earthdata.nasa.gov/foo,https://opendap.earthdata.nasa.gov/bar"]
                                     ["SC:AE_5DSno.002:30500515" "s3://opendap.sit.earthdata.nasa.gov/foo"]]}
              response (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)
              {:keys [status task-id]} response]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          (is (= 200 status))
          (is (some? task-id))
          (let [status-response (ingest/granule-bulk-update-task-status task-id)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= "Task completed with 4 FAILED out of 4 total granule update(s)."
                   status-message))
            (is (= [{:granule-ur "SC:AE_5DSno.002:30500511"
                     :status "FAILED"
                     :status-message "Invalid URL value, no more than two urls can be provided: https://foo,https://bar,https://baz"}
                    {:granule-ur "SC:AE_5DSno.002:30500512"
                     :status "FAILED"
                     :status-message "Invalid URL value, no more than one on-prem OPeNDAP url can be provided: https://foo, https://bar"}
                    {:granule-ur "SC:AE_5DSno.002:30500514"
                     :status "FAILED"
                     :status-message "Invalid URL value, no more than one Hyrax-in-the-cloud OPeNDAP url can be provided: https://opendap.sit.earthdata.nasa.gov/foo,https://opendap.earthdata.nasa.gov/bar"}
                    {:granule-ur "SC:AE_5DSno.002:30500515"
                     :status "FAILED"
                     :status-message "OPeNDAP URL value cannot start with s3://, but was s3://opendap.sit.earthdata.nasa.gov/foo"}]
                   granule-statuses))))))

    (testing "S3 url granule bulk update"
      (testing "successful S3 url granule bulk update"
        (let [bulk-update {:name "add S3 links 1"
                           :operation "UPDATE_FIELD"
                           :update-field "S3Link"
                           :updates [["SC:AE_5DSno.002:30500511" "s3://url30500511"]
                                     ["SC:AE_5DSno.002:30500512" "s3://url1, s3://url2,s3://url3"]
                                     ["SC:AE_5DSno.002:30500514" "s3://url30500514"]]}
              response (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)
              {:keys [status task-id]} response]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          (is (= 200 status))
          (is (some? task-id))
          (let [status-response (ingest/granule-bulk-update-task-status task-id)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= "All granule updates completed successfully." status-message))
            (is (= [{:granule-ur "SC:AE_5DSno.002:30500511"
                     :status "UPDATED"}
                    {:granule-ur "SC:AE_5DSno.002:30500512"
                     :status "UPDATED"}
                    {:granule-ur "SC:AE_5DSno.002:30500514"
                     :status "UPDATED"}]
                   granule-statuses)))))

      (testing "failed S3 link granule bulk update"
        (let [bulk-update {:name "add s3 links 2"
                           :operation "UPDATE_FIELD"
                           :update-field "S3Link"
                           :updates [["SC:AE_5DSno.002:30500513" "s3://url30500513"]]}
              response (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)
              {:keys [status task-id]} response]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          (is (= 200 status))
          (is (some? task-id))
          (let [status-response (ingest/granule-bulk-update-task-status task-id)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= "Task completed with 1 FAILED out of 1 total granule update(s)." status-message))
            (is (= [{:granule-ur "SC:AE_5DSno.002:30500513"
                     :status "FAILED"
                     :status-message "Adding s3 urls is not supported for format [application/iso:smap+xml]"}]
                   granule-statuses)))))

      (testing "partial successful S3 link granule bulk update"
        (let [bulk-update {:name "add s3 links 3"
                           :operation "UPDATE_FIELD"
                           :update-field "S3Link"
                           :updates [["SC:AE_5DSno.002:30500511" "s3://url30500511"]
                                     ["SC:AE_5DSno.002:30500512" "s3://url30500512"]
                                     ["SC:non-existent-ur" "s3://url30500513"]]}
              response (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)
              {:keys [status task-id]} response]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          (is (= 200 status))
          (is (some? task-id))
          (let [status-response (ingest/granule-bulk-update-task-status task-id)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= "Task completed with 1 FAILED and 2 UPDATED out of 3 total granule update(s)."
                   status-message))
            (is (= [{:granule-ur "SC:AE_5DSno.002:30500511"
                     :status "UPDATED"}
                    {:granule-ur "SC:AE_5DSno.002:30500512"
                     :status "UPDATED"}
                    {:granule-ur "SC:non-existent-ur"
                     :status "FAILED"
                     :status-message (format "Granule UR [SC:non-existent-ur] in task-id [%s] does not exist."
                                             task-id)}]
                   granule-statuses)))))

      (testing "invalid S3 url value in instruction"
        (let [bulk-update {:name "add S3 links 4"
                           :operation "UPDATE_FIELD"
                           :update-field "S3Link"
                           :updates [["SC:AE_5DSno.002:30500511" "https://foo"]
                                     ["SC:AE_5DSno.002:30500512" "s3://foo,S3://bar"]]}
              response (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)
              {:keys [status task-id]} response]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          (is (= 200 status))
          (is (some? task-id))
          (let [status-response (ingest/granule-bulk-update-task-status task-id)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= "Task completed with 2 FAILED out of 2 total granule update(s)."
                   status-message))
            (is (= [{:granule-ur "SC:AE_5DSno.002:30500511"
                     :status "FAILED"
                     :status-message "Invalid URL value, each S3 url must start with s3://, but was https://foo"}
                    {:granule-ur "SC:AE_5DSno.002:30500512"
                     :status "FAILED"
                     :status-message "Invalid URL value, each S3 url must start with s3://, but was S3://bar"}]
                   granule-statuses))))))))

(deftest add-opendap-url
  "test adding OPeNDAP url with real granule file that is already in CMR code base"
  (testing "ECHO10 granule"
    (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
          coll (data-core/ingest-concept-with-metadata-file "CMR-4722/OMSO2.003-collection.xml"
                                                            {:provider-id "PROV1"
                                                             :concept-type :collection
                                                             :native-id "OMSO2-collection"
                                                             :format-key :dif10})
          granule (data-core/ingest-concept-with-metadata-file "CMR-4722/OMSO2.003-granule.xml"
                                                               {:provider-id "PROV1"
                                                                :concept-type :granule
                                                                :native-id "OMSO2-granule"
                                                                :format-key :echo10})
          {:keys [concept-id revision-id]} granule
          target-metadata (-> "CMR-4722/OMSO2.003-granule-opendap-url.xml" io/resource slurp)
          bulk-update {:operation "UPDATE_FIELD"
                       :update-field "OPeNDAPLink"
                       :updates [["OMSO2.003:OMI-Aura_L2-OMSO2_2004m1001t2307-o01146_v003-2016m0615t191523.he5"
                                  "http://opendap-url.example.com"]]}
          {:keys [status] :as response} (ingest/bulk-update-granules
                                         "PROV1" bulk-update bulk-update-options)]
      (index/wait-until-indexed)
      (is (= 200 status))
      (is (= target-metadata
             (:metadata (mdb/get-concept concept-id (inc revision-id)))))))

  (testing "UMM-G granule"
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
          {:keys [concept-id revision-id]} granule
          bulk-update {:operation "UPDATE_FIELD"
                       :update-field "OPeNDAPLink"
                       :updates [["Unique_Granule_UR_v1.6"
                                  "https://opendap.earthdata.nasa.gov/test-gran1"]]}
          {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                 "PROV1" bulk-update bulk-update-options)]
      (index/wait-until-indexed)
      (ingest/update-granule-bulk-update-task-statuses)

      ;; verify the granule status is UPDATED
      (is (= 200 status))
      (is (some? task-id))
      (let [status-response (ingest/granule-bulk-update-task-status task-id)
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
        ;; We just verify the updated OPeNDAP url, type and subtype exists in the metadata.
        ;; The different scenarios of the RelatedUrls update are covered in unit tests.
        (is (string/includes? original-metadata "https://opendap.uat.earthdata.nasa.gov/erbe_albedo_monthly_xdeg"))
        (is (string/includes? original-metadata "OPENDAP DATA"))
        (is (not (string/includes? original-metadata "https://opendap.earthdata.nasa.gov/test-gran1")))
        (is (not (string/includes? original-metadata "USE SERVICE API")))

        (is (not (string/includes? updated-metadata "https://opendap.uat.earthdata.nasa.gov/erbe_albedo_monthly_xdeg")))
        (is (string/includes? updated-metadata "OPENDAP DATA"))
        (is (string/includes? updated-metadata "https://opendap.earthdata.nasa.gov/test-gran1"))
        (is (string/includes? updated-metadata "USE SERVICE API"))))))

(deftest add-s3-url
  "test adding S3 url with real granule file that is already in CMR code base"
  (testing "ECHO10 granule"
    (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
          coll (data-core/ingest-concept-with-metadata-file "CMR-4722/OMSO2.003-collection.xml"
                                                            {:provider-id "PROV1"
                                                             :concept-type :collection
                                                             :native-id "OMSO2-collection"
                                                             :format-key :dif10})
          granule (data-core/ingest-concept-with-metadata-file "CMR-4722/OMSO2.003-granule.xml"
                                                               {:provider-id "PROV1"
                                                                :concept-type :granule
                                                                :native-id "OMSO2-granule"
                                                                :format-key :echo10})
          {:keys [concept-id revision-id]} granule
          target-metadata (-> "CMR-4722/OMSO2.003-granule-s3-url.xml" io/resource slurp)
          bulk-update {:operation "UPDATE_FIELD"
                       :update-field "S3Link"
                       :updates [["OMSO2.003:OMI-Aura_L2-OMSO2_2004m1001t2307-o01146_v003-2016m0615t191523.he5"
                                  "s3://abcdefg/2016m0615t191523"]]}
          {:keys [status] :as response} (ingest/bulk-update-granules
                                         "PROV1" bulk-update bulk-update-options)]
      (index/wait-until-indexed)
      (is (= 200 status))
      (is (= target-metadata
             (:metadata (mdb/get-concept concept-id (inc revision-id)))))))

  (testing "UMM-G granule"
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
          {:keys [concept-id revision-id]} granule
          bulk-update {:operation "UPDATE_FIELD"
                       :update-field "S3Link"
                       :updates [["Unique_Granule_UR_v1.6"
                                  "s3://abcdefg/test-gran1"]]}
          {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                 "PROV1" bulk-update bulk-update-options)]
      (index/wait-until-indexed)
      (ingest/update-granule-bulk-update-task-statuses)

      ;; verify the granule status is UPDATED
      (is (= 200 status))
      (is (some? task-id))
      (let [status-response (ingest/granule-bulk-update-task-status task-id)
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
        (is (not (string/includes? original-metadata "s3://abcdefg/test-gran1")))
        (is (not (string/includes? original-metadata "GET DATA VIA DIRECT ACCESS")))
        (is (not (string/includes? original-metadata "This link provides direct download access via S3 to the granule.")))

        (is (string/includes? updated-metadata "s3://abcdefg/test-gran1"))
        (is (string/includes? updated-metadata "GET DATA VIA DIRECT ACCESS"))
        (is (string/includes? updated-metadata "This link provides direct download access via S3 to the granule."))))))

(deftest append-s3-url
  "test appending S3 url with real granule file that is already in CMR code base"
  (testing "ECHO10 granule"
    (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
          coll (data-core/ingest-concept-with-metadata-file "CMR-4722/OMSO2.003-collection.xml"
                                                            {:provider-id "PROV1"
                                                             :concept-type :collection
                                                             :native-id "OMSO2-collection"
                                                             :format-key :dif10})
          granule (data-core/ingest-concept-with-metadata-file "CMR-4722/OMSO2.003-granule.xml"
                                                               {:provider-id "PROV1"
                                                                :concept-type :granule
                                                                :native-id "OMSO2-granule"
                                                                :format-key :echo10})
          {:keys [concept-id revision-id]} granule
          target-metadata (-> "CMR-4722/OMSO2.003-granule-s3-url.xml" io/resource slurp)
          bulk-update {:operation "APPEND_TO_FIELD"
                       :update-field "S3Link"
                       :updates [["OMSO2.003:OMI-Aura_L2-OMSO2_2004m1001t2307-o01146_v003-2016m0615t191523.he5"
                                  "s3://abcdefg/2016m0615t191523"]]}
          {:keys [status] :as response} (ingest/bulk-update-granules
                                         "PROV1" bulk-update bulk-update-options)]
      (index/wait-until-indexed)
      (is (= 200 status))
      (is (= target-metadata
             (:metadata (mdb/get-concept concept-id (inc revision-id)))))

      (testing "append preserves existing S3 urls"
        (let [bulk-update {:operation "APPEND_TO_FIELD"
                           :update-field "S3Link"
                           :updates [["OMSO2.003:OMI-Aura_L2-OMSO2_2004m1001t2307-o01146_v003-2016m0615t191523.he5"
                                      "s3://zyxwvut/2016m0615t191523"]]}
              {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                     "PROV1" bulk-update bulk-update-options)]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          (let [latest-metadata (:metadata (mdb/get-concept concept-id))]
            (is (string/includes? latest-metadata "s3://abcdefg/2016m0615t191523"))
            (is (string/includes? latest-metadata "s3://zyxwvut/2016m0615t191523"))
            (is (string/includes? latest-metadata "This link provides direct download access via S3 to the granule.")))))

      (testing "append will not duplicate S3 urls"
        (let [bulk-update {:operation "APPEND_TO_FIELD"
                           :update-field "S3Link"
                           :updates [["OMSO2.003:OMI-Aura_L2-OMSO2_2004m1001t2307-o01146_v003-2016m0615t191523.he5"
                                      "s3://zyxwvut/2016m0615t191523"]]}
              {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                     "PROV1" bulk-update bulk-update-options)]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          (let [latest-metadata (:metadata (mdb/get-concept concept-id))]
            (is (= 1 (count (re-seq #"s3://zyxwvut/2016m0615t191523" latest-metadata)))))))))

  (testing "UMM-G granule"
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
          {:keys [concept-id revision-id]} granule
          bulk-update {:operation "APPEND_TO_FIELD"
                       :update-field "S3Link"
                       :updates [["Unique_Granule_UR_v1.6"
                                  "s3://abcdefg/test-gran1"]]}
          {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                 "PROV1" bulk-update bulk-update-options)]
      (index/wait-until-indexed)
      (ingest/update-granule-bulk-update-task-statuses)

      ;; verify the granule status is UPDATED
      (is (= 200 status))
      (is (some? task-id))
      (let [status-response (ingest/granule-bulk-update-task-status task-id)
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
        (is (not (string/includes? original-metadata "s3://abcdefg/test-gran1")))
        (is (not (string/includes? original-metadata "GET DATA VIA DIRECT ACCESS")))
        (is (not (string/includes? original-metadata "This link provides direct download access via S3 to the granule.")))

        (is (string/includes? updated-metadata "s3://abcdefg/test-gran1"))
        (is (string/includes? updated-metadata "GET DATA VIA DIRECT ACCESS"))
        (is (string/includes? updated-metadata "This link provides direct download access via S3 to the granule.")))

      (testing "append preserves existing S3 urls"
        (let [bulk-update {:operation "APPEND_TO_FIELD"
                           :update-field "S3Link"
                           :updates [["Unique_Granule_UR_v1.6"
                                      "s3://zyxwvut/test-gran1"]]}
              {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                     "PROV1" bulk-update bulk-update-options)]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          (let [latest-metadata (:metadata (mdb/get-concept concept-id))]
            (is (string/includes? latest-metadata "s3://abcdefg/test-gran1"))
            (is (string/includes? latest-metadata "s3://zyxwvut/test-gran1"))
            (is (string/includes? latest-metadata "GET DATA VIA DIRECT ACCESS"))
            (is (string/includes? latest-metadata "This link provides direct download access via S3 to the granule.")))))

      (testing "append will not duplicate S3 urls"
        (let [bulk-update {:operation "APPEND_TO_FIELD"
                           :update-field "S3Link"
                           :updates [["Unique_Granule_UR_v1.6"
                                      "s3://zyxwvut/test-gran1"]]}
              {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                     "PROV1" bulk-update bulk-update-options)]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          (let [latest-metadata (:metadata (mdb/get-concept concept-id))]
            (is (= 1 (count (re-seq #"s3://zyxwvut/test-gran1" latest-metadata))))))))))
