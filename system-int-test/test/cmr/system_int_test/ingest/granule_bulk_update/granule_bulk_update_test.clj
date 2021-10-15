(ns cmr.system-int-test.ingest.granule-bulk-update.granule-bulk-update-test
  "CMR bulk update. Test the actual update "
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.granule :as granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
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

    (index/wait-until-indexed)

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
          (let [status-req-options {:query-params {:show_granules "true"}}
                status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
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
          (let [status-req-options {:query-params {:show_granules "true"}}
                status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
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
          (let [status-req-options {:query-params {:show_granules "true"}}
                status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
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
          (let [status-req-options {:query-params {:show_granules "true"}}
                status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
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
          (let [status-req-options {:query-params {:show_granules "true"}}
                status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
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
          (let [status-req-options {:query-params {:show_granules "true"}}
                status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
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
          (let [status-req-options {:query-params {:show_granules "true"}}
                status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
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
          (let [status-req-options {:query-params {:show_granules "true"}}
                status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
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
  ;; test adding OPeNDAP url with real granule file that is already in CMR code base
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
  ;; test adding S3 url with real granule file that is already in CMR code base
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

(deftest append-opendap-link
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
          bulk-update {:operation "APPEND_TO_FIELD"
                       :update-field "OPeNDAPLink"
                       :updates [["OMSO2.003:OMI-Aura_L2-OMSO2_2004m1001t2307-o01146_v003-2016m0615t191523.he5"
                                  "http://opendap-url.example.com"]]}
          {:keys [status] :as response} (ingest/bulk-update-granules
                                         "PROV1" bulk-update bulk-update-options)]
      (index/wait-until-indexed)
      (is (= 200 status))
      (is (= target-metadata
             (:metadata (mdb/get-concept concept-id (inc revision-id)))))

      (testing "append will not overwrite existing opendap links when one is already present"
        (let [bulk-update {:operation "APPEND_TO_FIELD"
                           :update-field "OPeNDAPLink"
                           :updates [["OMSO2.003:OMI-Aura_L2-OMSO2_2004m1001t2307-o01146_v003-2016m0615t191523.he5"
                                      "http://opendap-url.example.com2"]]}
              {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                     "PROV1" bulk-update bulk-update-options)]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          (let [status-response (ingest/granule-bulk-update-task-status task-id)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= "Task completed with 1 FAILED out of 1 total granule update(s)." status-message)))

          (testing "verify the metadata was not altered"
            (let [latest-metadata (:metadata (mdb/get-concept concept-id))]
              (is (= target-metadata
                     (:metadata (mdb/get-concept concept-id (inc revision-id)))))))))))

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
                       :update-field "OPeNDAPLink"
                       :updates [["Unique_Granule_UR_v1.6"
                                  "http://opendap.earthdata.nasa.gov/test1"]]}
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
        ;; We just verify the updated OPeNDAP url, type and description exists in the metadata.
        ;; The different scenarios of the RelatedUrls update are covered in unit tests.
        (is (= 1 (count (filter #(= "http://opendap.earthdata.nasa.gov/test1" (:URL %))
                                (:RelatedUrls (json/parse-string updated-metadata true))))))
        (is (not (string/includes? original-metadata "GET DATA VIA DIRECT ACCESS"))))

      (testing "append will not duplicate OPeNDAP urls"
        (let [bulk-update {:operation "APPEND_TO_FIELD"
                           :update-field "OPeNDAPLink"
                           :updates [["Unique_Granule_UR_v1.6"
                                      "http://opendap.earthdata.nasa.gov/test1"]]}
              {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                     "PROV1" bulk-update bulk-update-options)]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          (let [latest-metadata (:metadata (mdb/get-concept concept-id))]
            (is (= 1 (count (filter #(= "http://opendap.earthdata.nasa.gov/test1" (:URL %))
                                    (:RelatedUrls (json/parse-string latest-metadata true))))))))))))

(deftest update-opendap-type
  ;; test updating OPeNDAP type with real granule file that is already in CMR code base
  (testing "UMM-G granule update via url regex"
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
          granule2 (data-core/ingest-concept-with-metadata-file
                    "umm-g-samples/GranuleExample4.json"
                    {:provider-id "PROV1"
                     :concept-type :granule
                     :native-id "test-gran2"
                     :format "application/vnd.nasa.cmr.umm+json;version=1.6"})

          {:keys [concept-id revision-id]} granule
          bulk-update {:operation "UPDATE_TYPE"
                       :update-field "OPeNDAPLink"
                       :updates ["Unique_Granule_UR_v1.6"
                                 "Unique_Granule_UR_2_v1.6"]}
          {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                 "PROV1" bulk-update bulk-update-options)]
      (index/wait-until-indexed)
      (ingest/update-granule-bulk-update-task-statuses)

      ;; verify the granule status is UPDATED
      (is (= 200 status))
      (is (some? task-id))
      (let [status-req-options {:query-params {:show_granules "true"}}
            status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
            {:keys [task-status status-message granule-statuses]} status-response
            expected [{:granule-ur "Unique_Granule_UR_v1.6" :status "UPDATED"}
                      {:granule-ur "Unique_Granule_UR_2_v1.6" :status "UPDATED"}]]
        (is (= "COMPLETE" task-status))
        (is (= "All granule updates completed successfully." status-message))

        (doseq [item expected]
          (is (some #(= item %) granule-statuses)
              (format "does %s exist in granule-statuses regardless of order" item))))
      ;; verify the granule metadata is updated as expected
      (let [original-metadata (:metadata (mdb/get-concept concept-id revision-id))
            updated-metadata (:metadata (mdb/get-concept concept-id (inc revision-id)))]
        ;; since the metadata will be returned in the latest UMM-G format,
        ;; we can't compare the whole metadata to an expected string.
        ;; We just verify the updated OPeNDAP url, type and subtype exists in the metadata.
        ;; The different scenarios of the RelatedUrls update are covered in unit tests.
        (is (string/includes? original-metadata "OPENDAP DATA"))
        (is (not (string/includes? original-metadata "USE SERVICE API")))

        (is (string/includes? updated-metadata "OPENDAP DATA"))
        (is (string/includes? updated-metadata "USE SERVICE API")))))

  (testing "UMM-G granule update via supplied subtype"
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
          granule2 (data-core/ingest-concept-with-metadata-file
                    "umm-g-samples/GranuleExample4.json"
                    {:provider-id "PROV1"
                     :concept-type :granule
                     :native-id "test-gran2"
                     :format "application/vnd.nasa.cmr.umm+json;version=1.6"})

          {:keys [concept-id revision-id]} granule
          bulk-update {:operation "UPDATE_TYPE"
                       :update-field "OPeNDAPLink"
                       :updates [["Unique_Granule_UR_v1.6" "OPENDAP DATA"]
                                 "Unique_Granule_UR_2_v1.6"]} ;;can actually mix update types, if so inclined
          {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                 "PROV1" bulk-update bulk-update-options)]
      (index/wait-until-indexed)
      (ingest/update-granule-bulk-update-task-statuses)

      ;; verify the granule status is UPDATED
      (is (= 200 status))
      (is (some? task-id))
      (let [status-req-options {:query-params {:show_granules "true"}}
            status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
            {:keys [task-status status-message granule-statuses]} status-response
            expected [{:granule-ur "Unique_Granule_UR_v1.6" :status "UPDATED"}
                      {:granule-ur "Unique_Granule_UR_2_v1.6" :status "UPDATED"}]]
        (is (= "COMPLETE" task-status))
        (is (= "All granule updates completed successfully." status-message))

        (doseq [item expected]
          (is (some #(= item %) granule-statuses)
              (format "does %s exist in granule-statuses regardless of order" item))))
      ;; verify the granule metadata is updated as expected
      (let [original-metadata (:metadata (mdb/get-concept concept-id revision-id))
            updated-metadata (:metadata (mdb/get-concept concept-id (inc revision-id)))]
        ;; since the metadata will be returned in the latest UMM-G format,
        ;; we can't compare the whole metadata to an expected string.
        ;; We just verify the updated OPeNDAP url, type and subtype exists in the metadata.
        ;; The different scenarios of the RelatedUrls update are covered in unit tests.
        (is (string/includes? original-metadata "OPENDAP DATA"))
        (is (not (string/includes? original-metadata "USE SERVICE API")))

        (is (string/includes? updated-metadata "OPENDAP DATA"))
        (is (string/includes? updated-metadata "USE SERVICE API")))))

  (testing "Failure case - no opendap links to update"
    (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
          coll (data-core/ingest-concept-with-metadata-file
                "umm-g-samples/Collection.json"
                {:provider-id "PROV1"
                 :concept-type :collection
                 :native-id "test-coll1"
                 :format "application/vnd.nasa.cmr.umm+json;version=1.16"})
          granule (data-core/ingest-concept-with-metadata-file
                   "umm-g-samples/GranuleExample3.json"
                   {:provider-id "PROV1"
                    :concept-type :granule
                    :native-id "test-gran1"
                    :format "application/vnd.nasa.cmr.umm+json;version=1.6"})
          {:keys [concept-id revision-id]} granule
          bulk-update {:operation "UPDATE_TYPE"
                       :update-field "OPeNDAPLink"
                       :updates ["Unique_Granule_UR_v1.6"]}
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
                 :status-message "Granule update failed - there are no OPeNDAP Links to update."}]
               granule-statuses))))))

(deftest update-checksum
  "test updating checksum with real granule file that is already in CMR code base"
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
        granule-with-no-data-element
          (data-core/ingest-concept-with-metadata-file "CMR-4722/OMSO2.003-granule-no-data-granule.xml"
                                                       {:provider-id "PROV1"
                                                        :concept-type :granule
                                                        :native-id "OMSO2-granule-data-granule"
                                                        :format-key :echo10})
        granule-with-no-checksum-element
          (data-core/ingest-concept-with-metadata-file "CMR-4722/OMSO2.003-granule-no-checksum.xml"
                                                       {:provider-id "PROV1"
                                                        :concept-type :granule
                                                        :native-id "OMSO2-granule-no-checksum"
                                                        :format-key :echo10})]
    (testing "ECHO10 granule update for checksum value and algorithm"
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "Checksum"
                         :updates [["OMSO2.003:OMI-Aura_L2-OMSO2_2004m1001t2307-o01146_v003-2016m0615t191523.he5"
                                    "0987654321,MD5"]]}
            {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                   "PROV1" bulk-update bulk-update-options)
            {:keys [concept-id revision-id]} granule
            target-metadata (-> "CMR-4722/OMSO2.003-granule-checksum.xml" io/resource slurp)]
        (index/wait-until-indexed)
        (is (= 200 status))
        (is (some? task-id))
        (is (= target-metadata
               (:metadata (mdb/get-concept concept-id (inc revision-id)))))

        (ingest/update-granule-bulk-update-task-statuses)

        ;; verify the granule status is UPDATED
        (let [status-req-options {:query-params {:show_granules "true"}}
              status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
              {:keys [task-status status-message granule-statuses]} status-response]
          (is (= "COMPLETE" task-status))
          (is (= "All granule updates completed successfully." status-message))
          (is (= [{:granule-ur "OMSO2.003:OMI-Aura_L2-OMSO2_2004m1001t2307-o01146_v003-2016m0615t191523.he5"
                   :status "UPDATED"}]
                 granule-statuses)))))
    (testing "ECHO10 granule failure (no DataGranule element)"
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "Checksum"
                         :updates [["OMSO2.003:no-data-granule"
                                    "0987654321,MD5"]]}
            {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                   "PROV1" bulk-update bulk-update-options)
            {:keys [concept-id revision-id]} granule-with-no-data-element]
        (index/wait-until-indexed)
        (is (= 200 status))
        (is (some? task-id))

        (ingest/update-granule-bulk-update-task-statuses)

        ;; verify the granule status is FAILED
        (let [status-req-options {:query-params {:show_granules "true"}}
              status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
              {:keys [task-status status-message granule-statuses]} status-response]
          (is (= "COMPLETE" task-status))
          (is (= "Task completed with 1 FAILED out of 1 total granule update(s)." status-message))
          (is (= [{:granule-ur "OMSO2.003:no-data-granule"
                   :status "FAILED"
                   :status-message "Can't update <Checksum>: no parent <DataGranule> element"}]
                 granule-statuses)))

        (testing "verify the metadata was not altered"
          (is (not (:metadata (mdb/get-concept concept-id (inc revision-id))))))))

    (testing "ECHO10 granule failure: attempt to add new <Checksum> with no algorithm specified"
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "Checksum"
                         :updates [["OMSO2.003:no-checksum"
                                    "0987654321"]]}
            {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                   "PROV1" bulk-update bulk-update-options)
            {:keys [concept-id revision-id]} granule-with-no-checksum-element]
        (index/wait-until-indexed)
        (is (= 200 status))
        (is (some? task-id))


        (ingest/update-granule-bulk-update-task-statuses)

        ;; verify the granule status is FAILED
        (let [status-req-options {:query-params {:show_granules "true"}}
              status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
              {:keys [task-status status-message granule-statuses]} status-response]
          (is (= "COMPLETE" task-status))
          (is (= "Task completed with 1 FAILED out of 1 total granule update(s)." status-message))
          (is (= [{:granule-ur "OMSO2.003:no-checksum"
                   :status "FAILED"
                   :status-message "Cannot add new <Checksum> element: please specify a checksum value as well as an algorithm."}]
                 granule-statuses)))

        (testing "verify the metadata was not altered"
          (is (not (:metadata (mdb/get-concept concept-id (inc revision-id))))))))

    (testing "Successfully add new <Checksum> element"
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "Checksum"
                         :updates [["OMSO2.003:no-checksum"
                                    "0987654321,MD5"]]}
            {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                   "PROV1" bulk-update bulk-update-options)
            {:keys [concept-id revision-id]} granule
            target-metadata (-> "CMR-4722/OMSO2.003-granule-checksum.xml" io/resource slurp)]
        (index/wait-until-indexed)
        (is (= 200 status))
        (is (some? task-id))
        (is (= target-metadata
               (:metadata (mdb/get-concept concept-id (inc revision-id)))))

        (ingest/update-granule-bulk-update-task-statuses)

        ;; verify the granule status is UPDATED
        (let [status-req-options {:query-params {:show_granules "true"}}
              status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
              {:keys [task-status status-message granule-statuses]} status-response]
          (is (= "COMPLETE" task-status))
          (is (= "All granule updates completed successfully." status-message))
          (is (= [{:granule-ur "OMSO2.003:no-checksum"
                   :status "UPDATED"}]
                 granule-statuses)))))))

(deftest update-size
  "test updating size with real granule file that is already in CMR code base"
  (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
        coll (data-core/ingest-concept-with-metadata-file "CMR-7504/7504-Coll.xml"
                                                          {:provider-id "PROV1"
                                                           :concept-type :collection
                                                           :native-id "MODIS_A-JPL-L2P-v2019.0"
                                                           :format-key :dif10})
        granule (data-core/ingest-concept-with-metadata-file "CMR-7504/7504-Both.xml"
                                                             {:provider-id "PROV1"
                                                              :concept-type :granule
                                                              :native-id "MODIS_A-JPL-L2P-Gran"
                                                              :format-key :echo10})
        granule-with-no-data-element
                (data-core/ingest-concept-with-metadata-file "CMR-4722/OMSO2.003-granule-no-data-granule.xml"
                                                             {:provider-id "PROV1"
                                                              :concept-type :granule
                                                              :native-id "OMSO2-granule-data-granule"
                                                              :format-key :echo10})]
    (testing "ECHO10 granule update for Size in MB and bytes"
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "Size"
                         :updates [["7504BothSizeFields"
                                    "1.0,1024"]]}
            {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                   "PROV1" bulk-update bulk-update-options)
            {:keys [concept-id revision-id]} granule
            target-metadata (-> "CMR-7504/7504-Both-Updated.xml" io/resource slurp)]
        (index/wait-until-indexed)
        (is (= 200 status))
        (is (some? task-id))
        (is (= target-metadata
               (:metadata (mdb/get-concept concept-id (inc revision-id)))))

        (ingest/update-granule-bulk-update-task-statuses)

        ;; verify the granule status is UPDATED
        (let [status-req-options {:query-params {:show_granules "true"}}
              status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
              {:keys [task-status status-message granule-statuses]} status-response]
          (is (= "COMPLETE" task-status))
          (is (= "All granule updates completed successfully." status-message))
          (is (= [{:granule-ur "7504BothSizeFields"
                   :status "UPDATED"}]
                 granule-statuses)))))

    (testing "ECHO10 size BGU failure (no DataGranule element)"
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "Size"
                         :updates [["OMSO2.003:no-data-granule"
                                    "0987654321"]]}
            {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                   "PROV1" bulk-update bulk-update-options)
            {:keys [concept-id revision-id]} granule-with-no-data-element]
        (index/wait-until-indexed)
        (is (= 200 status))
        (is (some? task-id))

        (ingest/update-granule-bulk-update-task-statuses)

        ;; verify the granule status is FAILED
        (let [status-req-options {:query-params {:show_granules "true"}}
              status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
              {:keys [task-status status-message granule-statuses]} status-response]
          (is (= "COMPLETE" task-status))
          (is (= "Task completed with 1 FAILED out of 1 total granule update(s)." status-message))
          (is (= [{:granule-ur "OMSO2.003:no-data-granule"
                   :status "FAILED"
                   :status-message "Can't update Size: no parent <DataGranule> element"}]
                 granule-statuses)))

        (testing "verify the metadata was not altered"
          (is (not (:metadata (mdb/get-concept concept-id (inc revision-id))))))))

    (testing "ECHO10 granule failures for bad input"
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "Size"
                         :updates [["7504BothSizeFields"
                                    "123,ABC"]]}
            {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                   "PROV1" bulk-update bulk-update-options)
            {:keys [concept-id revision-id]} granule-with-no-data-element]
        (index/wait-until-indexed)
        (is (= 200 status))
        (is (some? task-id))

        (ingest/update-granule-bulk-update-task-statuses)

        ;; verify the granule status is FAILED
        (let [status-req-options {:query-params {:show_granules "true"}}
              status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
              {:keys [task-status status-message granule-statuses]} status-response]
          (is (= "COMPLETE" task-status))
          (is (= "Task completed with 1 FAILED out of 1 total granule update(s)." status-message))
          (is (= [{:granule-ur "7504BothSizeFields"
                   :status "FAILED"
                   :status-message
                          (str "Can't update Size: invalid data specified. Please include at most one value for "
                            "DataGranuleSizeInBytes, and one value for SizeMBDataGranule, seperated by a comma. "
                            "DataGranuleSizeInBytes must be an integer value, while SizeMBDataGranule must be a "
                            "double value with a decimal point.")}]
                 granule-statuses)))

        (testing "verify the metadata was not altered"
          (is (not (:metadata (mdb/get-concept concept-id (inc revision-id)))))))
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "Size"
                         :updates [["7504BothSizeFields"
                                    "123,456"]]}
            {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                   "PROV1" bulk-update bulk-update-options)
            {:keys [concept-id revision-id]} granule-with-no-data-element]
        (index/wait-until-indexed)
        (is (= 200 status))
        (is (some? task-id))

        (ingest/update-granule-bulk-update-task-statuses)

        ;; verify the granule status is FAILED
        (let [status-req-options {:query-params {:show_granules "true"}}
              status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
              {:keys [task-status status-message granule-statuses]} status-response]
          (is (= "COMPLETE" task-status))
          (is (= "Task completed with 1 FAILED out of 1 total granule update(s)." status-message))
          (is (= [{:granule-ur "7504BothSizeFields"
                   :status "FAILED"
                   :status-message
                          (str "Can't update Size: invalid data specified. Please include at most one value for "
                            "DataGranuleSizeInBytes, and one value for SizeMBDataGranule, seperated by a comma. "
                            "DataGranuleSizeInBytes must be an integer value, while SizeMBDataGranule must be a "
                            "double value with a decimal point.")}]
                 granule-statuses)))))))

(deftest update-format
  "test updating format with real granule file that is already in CMR code base"
  (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
        coll (data-core/ingest-concept-with-metadata-file "CMR-7504/7504-Coll.xml"
                                                          {:provider-id "PROV1"
                                                           :concept-type :collection
                                                           :native-id "MODIS_A-JPL-L2P-v2019.0"
                                                           :format-key :dif10})
        granule (data-core/ingest-concept-with-metadata-file "CMR-7505/7505.xml"
                                                             {:provider-id "PROV1"
                                                              :concept-type :granule
                                                              :native-id "MODIS_A-JPL-L2P-Gran"
                                                              :format-key :echo10})]
    (testing "ECHO10 granule update for Size in MB and bytes"
      (let [bulk-update {:operation "UPDATE_FIELD"
                         :update-field "Format"
                         :updates [["7505" "NET-CDF"]]}
            {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                   "PROV1" bulk-update bulk-update-options)
            {:keys [concept-id revision-id]} granule
            target-metadata (-> "CMR-7505/7505-Updated.xml" io/resource slurp)]
        (index/wait-until-indexed)
        (is (= 200 status))
        (is (some? task-id))
        (is (= target-metadata
               (:metadata (mdb/get-concept concept-id (inc revision-id)))))

        (ingest/update-granule-bulk-update-task-statuses)

        ;; verify the granule status is UPDATED
        (let [status-req-options {:query-params {:show_granules "true"}}
              status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
              {:keys [task-status status-message granule-statuses]} status-response]
          (is (= "COMPLETE" task-status))
          (is (= "All granule updates completed successfully." status-message))
          (is (= [{:granule-ur "7505"
                   :status "UPDATED"}]
                 granule-statuses)))))))

(deftest update-additional-file
  ;; test updating files and filepackages with real granule files that are already in CMR code base
  (testing "UMM-G granule"
    (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
          coll (data-core/ingest-concept-with-metadata-file
                "umm-g-samples/Collection.json"
                {:provider-id "PROV1"
                 :concept-type :collection
                 :native-id "test-coll1"
                 :format "application/vnd.nasa.cmr.umm+json;version=1.16"})
          granule (data-core/ingest-concept-with-metadata-file
                   "umm-g-samples/GranuleExample2.json"
                   {:provider-id "PROV1"
                    :concept-type :granule
                    :native-id "test-gran1"
                    :format "application/vnd.nasa.cmr.umm+json;version=1.6"})
          {:keys [concept-id revision-id]} granule
          bulk-update {:operation "UPDATE_FIELD"
                       :update-field "AdditionalFile"
                       :updates [{:GranuleUR "Unique_Granule_UR_v1.6"
                                  :Files [{:Name "GranuleFileName1"
                                           :SizeInBytes 20000
                                           :Size 20}
                                          {:Name "GranuleZipFile2"
                                           :Format "ASCII"}
                                          {:Name "SupportedGranuleFileNotInPackage"
                                           :Checksum {:Value "123foobar"
                                                      :Algorithm "SHA-256"}}]}]}

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
            updated-metadata (:metadata (mdb/get-concept concept-id (inc revision-id)))
            expected-metadata (string/replace
                               (slurp (io/resource "additional-file-results/GranEx2UpdateSize.json"))
                               #"\s+" "")]

        (is (string/includes? updated-metadata expected-metadata)))))

  (testing "Failure cases"
    (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
          coll (data-core/ingest-concept-with-metadata-file
                "umm-g-samples/Collection.json"
                {:provider-id "PROV1"
                 :concept-type :collection
                 :native-id "test-coll1"
                 :format "application/vnd.nasa.cmr.umm+json;version=1.16"})
          granule (data-core/ingest-concept-with-metadata-file
                   "umm-g-samples/GranuleExample2.json"
                   {:provider-id "PROV1"
                    :concept-type :granule
                    :native-id "test-gran1"
                    :format "application/vnd.nasa.cmr.umm+json;version=1.6"})
          {:keys [concept-id revision-id]} granule]
      (testing "Add size with no sizeunit"
        (let [bulk-update {:operation "UPDATE_FIELD"
                           :update-field "AdditionalFile"
                           :updates [{:GranuleUR "Unique_Granule_UR_v1.6"
                                      :Files [{:Name "GranuleFileName3"
                                               :Size 20}]}]}

              {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                     "PROV1" bulk-update bulk-update-options)]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          ;; verify the granule status is FAILED
          (is (= 200 status))
          (is (some? task-id))
          (let [status-req-options {:query-params {:show_granules "true"}}
                status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= [{:granule-ur "Unique_Granule_UR_v1.6"
                     :status "FAILED"
                     :status-message "Can't update granule: Size value supplied with no SizeUnit present for File or FilePackage with name [GranuleFileName3]"}]
                   granule-statuses))
            ;;verify metadata is not altered
            (is (not (:metadata (mdb/get-concept concept-id (inc revision-id))))))))

      (testing "Add sizeunit with no size"
        (let [bulk-update {:operation "UPDATE_FIELD"
                           :update-field "AdditionalFile"
                           :updates [{:GranuleUR "Unique_Granule_UR_v1.6"
                                      :Files [{:Name "GranuleFileName3"
                                               :SizeUnit "KB"}]}]}

              {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                     "PROV1" bulk-update bulk-update-options)]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          ;; verify the granule status is FAILED
          (is (= 200 status))
          (is (some? task-id))
          (let [status-req-options {:query-params {:show_granules "true"}}
                status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= [{:granule-ur "Unique_Granule_UR_v1.6"
                     :status "FAILED"
                     :status-message "Can't update granule: SizeUnit value supplied with no Size present for File or FilePackage with name [GranuleFileName3]"}]
                   granule-statuses))
            ;;verify metadata is not altered
            (is (not (:metadata (mdb/get-concept concept-id (inc revision-id))))))))

      (testing "Update checksum algorithm with no value"
        (let [bulk-update {:operation "UPDATE_FIELD"
                           :update-field "AdditionalFile"
                           :updates [{:GranuleUR "Unique_Granule_UR_v1.6"
                                      :Files [{:Name "GranuleZipFile2"
                                               :Checksum {:Algorithm "SHA-256"}}]}]}

              {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                     "PROV1" bulk-update bulk-update-options)]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          ;; verify the granule status is FAILED
          (is (= 200 status))
          (is (some? task-id))
          (let [status-req-options {:query-params {:show_granules "true"}}
                status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= [{:granule-ur "Unique_Granule_UR_v1.6"
                     :status "FAILED"
                     :status-message "Can't update granule: checksum algorithm update requested without new checksum value for file with name [GranuleZipFile2]"}]
                   granule-statuses))
            ;;verify metadata is not altered
            (is (not (:metadata (mdb/get-concept concept-id (inc revision-id))))))))

      (testing "Update checksum value with no algorithm"
        (let [bulk-update {:operation "UPDATE_FIELD"
                           :update-field "AdditionalFile"
                           :updates [{:GranuleUR "Unique_Granule_UR_v1.6"
                                      :Files [{:Name "GranuleFileName4"
                                               :Checksum {:Value "foobar123"}}]}]}

              {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                     "PROV1" bulk-update bulk-update-options)]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          ;; verify the granule status is FAILED
          (is (= 200 status))
          (is (some? task-id))
          (let [status-req-options {:query-params {:show_granules "true"}}
                status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= [{:granule-ur "Unique_Granule_UR_v1.6"
                     :status "FAILED"
                     :status-message "Can't update granule: checksum value supplied with no algorithm present for file with name [GranuleFileName4]"}]
                   granule-statuses))
            ;;verify metadata is not altered
            (is (not (:metadata (mdb/get-concept concept-id (inc revision-id))))))))

      (testing "Attempt to update with nonexistent input file"
        (let [bulk-update {:operation "UPDATE_FIELD"
                           :update-field "AdditionalFile"
                           :updates [{:GranuleUR "Unique_Granule_UR_v1.6"
                                      :Files [{:Name "GranuleZipFile4"
                                               :Checksum {:Value "foobar123"}}]}]}

              {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                     "PROV1" bulk-update bulk-update-options)]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          ;; verify the granule status is FAILED
          (is (= 200 status))
          (is (some? task-id))
          (let [status-req-options {:query-params {:show_granules "true"}}
                status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= [{:granule-ur "Unique_Granule_UR_v1.6"
                     :status "FAILED"
                     :status-message "Update failed - please only specify Files or FilePackages contained in the existing granule metadata"}]
                   granule-statuses))
            ;;verify metadata is not altered
            (is (not (:metadata (mdb/get-concept concept-id (inc revision-id))))))))

      (testing "Attempt to update a granule with duplicate files"
        (let [granule2 (data-core/ingest-concept-with-metadata-file
                        "umm-g-samples/GranuleExampleWithDuplicates.json"
                        {:provider-id "PROV1"
                         :concept-type :granule
                         :native-id "test-gran2"
                         :format "application/vnd.nasa.cmr.umm+json;version=1.6"})
              {:keys [concept-id revision-id]} granule2
              bulk-update {:operation "UPDATE_FIELD"
                           :update-field "AdditionalFile"
                           :updates [{:GranuleUR "Gran_With_Dupes"
                                      :Files [{:Name "GranuleFileName4"
                                               :Format "ASCII"}
                                              {:Name "GranuleFileName4"
                                               :Format "BINARY"}]}]}

              {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                     "PROV1" bulk-update bulk-update-options)]
          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

         ;; verify the granule status is FAILED
          (is (= 200 status))
          (is (some? task-id))
          (let [status-req-options {:query-params {:show_granules "true"}}
                status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= [{:granule-ur "Gran_With_Dupes"
                     :status "FAILED"
                     :status-message "Update failed - this operation is not available for granules with duplicate FilePackage/File names in the granule metadata."}]
                   granule-statuses))))

        (testing "Validation failures to non-UMM enum values"
          (let [bulk-update {:operation "UPDATE_FIELD"
                             :update-field "AdditionalFile"
                             :updates [{:GranuleUR "Unique_Granule_UR_v1.6"
                                        :Files [{:Name "GranuleZipFile2"
                                                 :Format "fakeformat"
                                                 :Checksum {:Value "12345" :Algorithm "SHA-123"}}
                                                {:Name "GranuleFileName2"
                                                 :MimeType "application/bogus"}]}]}

                {:keys [status task-id] :as response} (ingest/bulk-update-granules
                                                       "PROV1" bulk-update bulk-update-options)]
            (index/wait-until-indexed)
            (ingest/update-granule-bulk-update-task-statuses)

           ;; verify the granule status is FAILED
            (is (= 200 status))
            (is (some? task-id))
            (let [status-req-options {:query-params {:show_granules "true"}}
                  status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
                  {:keys [task-status status-message granule-statuses]} status-response]
              (is (= "COMPLETE" task-status))
              (is (= [{:granule-ur "Unique_Granule_UR_v1.6"
                       :status "FAILED"
                       :status-message (str "#/DataGranule/ArchiveAndDistributionInformation/2/Checksum/Algorithm: SHA-123 is not a valid enum value; "
                                            "#/DataGranule/ArchiveAndDistributionInformation/2/Format: Format [fakeformat] was not a valid keyword.; "
                                            "#/DataGranule/ArchiveAndDistributionInformation/0/Files/1/MimeType: application/bogus is not a valid enum value")}]
                     granule-statuses))
             ;;verify metadata is not altered
              (is (not (:metadata (mdb/get-concept concept-id (inc revision-id))))))))))))

(deftest status-verbosity-test
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
        gran3 (ingest/ingest-concept
               (data-core/item->concept
                (granule/granule-with-umm-spec-collection
                 coll1
                 (:concept-id coll1)
                 {:native-id "gran-native1-4"
                  :granule-ur "SC:AE_5DSno.002:30500514"})
                :umm-json))
        bulk-update {:name "add S3 links 1"
                     :operation "UPDATE_FIELD"
                     :update-field "S3Link"
                     :updates [["SC:AE_5DSno.002:30500511" "s3://url30500511"]
                               ["SC:AE_5DSno.002:30500512" "s3://url1, s3://url2,s3://url3"]
                               ["SC:AE_5DSno.002:30500514" "s3://url30500514"]]}
        response (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)
        {:keys [status task-id]} response]

    (index/wait-until-indexed)
    (ingest/update-granule-bulk-update-task-statuses)

    (testing "ship off bulk granule request, then check status"
     (is (= 200 status))
     (is (some? task-id))

     (testing "least verbose status, no parameters"
       (let [status-response (ingest/granule-bulk-update-task-status task-id)
             {:keys [progress request-json-body granule-statuses]} status-response]
         ;these three are all unincluded by default (least verbose)
         (is (= nil granule-statuses))
         (is (= nil request-json-body))
         (is (= nil progress)))
      (testing "status with show_progress=true"
        (let [status-req-options {:query-params {:show_progress "true"}}
              status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
              {:keys [progress request-json-body granule-statuses]} status-response]
          (is (= nil granule-statuses))
          (is (= nil request-json-body))
          (is (= "Complete." progress))))
      (testing "status with show_granules=true, show_progress=false"
        (let [status-req-options {:query-params {:show_granules "true" :show_progress "false"}}
              status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
              {:keys [progress request-json-body granule-statuses]} status-response]
          (is (= [{:granule-ur "SC:AE_5DSno.002:30500511"
                   :status "UPDATED"}
                  {:granule-ur "SC:AE_5DSno.002:30500512"
                   :status "UPDATED"}
                  {:granule-ur "SC:AE_5DSno.002:30500514"
                   :status "UPDATED"}]
                 granule-statuses))
          (is (= nil request-json-body))
          (is (= nil progress))))
      (testing "status with show_request=true"
        (let [status-req-options {:query-params {:show_request "true"}}
              status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
              {:keys [progress request-json-body granule-statuses]} status-response]
          (is (= nil granule-statuses))
          (is (string/includes? request-json-body "{\"name\":\"add S3 links 1\",\"operation\":\"UPDATE_FIELD\""))
          (is (= nil progress))))
      (testing "maximum verbosity, all parameters set to true"
        (let [status-req-options {:query-params {:show_granules "true" :show_request "true" :show_progress "true"}}
              status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
              {:keys [progress request-json-body granule-statuses]} status-response]
          (is (= [{:granule-ur "SC:AE_5DSno.002:30500511"
                   :status "UPDATED"}
                  {:granule-ur "SC:AE_5DSno.002:30500512"
                   :status "UPDATED"}
                  {:granule-ur "SC:AE_5DSno.002:30500514"
                   :status "UPDATED"}]
                 granule-statuses))
          (is (string/includes? request-json-body "{\"name\":\"add S3 links 1\",\"operation\":\"UPDATE_FIELD\""))
          (is (= "Complete." progress))))))))

(deftest input-format-test
  "test updating with incorrect combinations of update-field and update format"
  (testing "Give new :updates format with update-field that isn't AdditionalFile"
    (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
          coll (data-core/ingest-concept-with-metadata-file
                "umm-g-samples/Collection.json"
                {:provider-id "PROV1"
                 :concept-type :collection
                 :native-id "test-coll1"
                 :format "application/vnd.nasa.cmr.umm+json;version=1.16"})
          granule (data-core/ingest-concept-with-metadata-file
                   "umm-g-samples/GranuleExample2.json"
                   {:provider-id "PROV1"
                    :concept-type :granule
                    :native-id "test-gran1"
                    :format "application/vnd.nasa.cmr.umm+json;version=1.6"})
          {:keys [concept-id revision-id]} granule
          bulk-update {:operation "UPDATE_FIELD"
                       :update-field "S3Link"
                       :updates [{:GranuleUR "Unique_Granule_UR_v1.6"
                                  :Files [{:Name "GranuleFileName1"
                                           :SizeInBytes 20000
                                           :Size 20}]}]}



          {:keys [status task-id errors] :as response} (ingest/bulk-update-granules
                                                        "PROV1" bulk-update bulk-update-options)]
      (index/wait-until-indexed)
      (ingest/update-granule-bulk-update-task-statuses)

      ;; verify the granule status is UPDATED
      (is (= 400 status))
      (is (not (some? task-id)))
      (is (= (str "Bulk Granule Update failed - invalid update format specified for the "
                  "operation:update-field combination [update_field:s3link].")
             (first errors)))))

  (testing "Give new :updates format with update-field that isn't AdditionalFile"
    (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
          coll (data-core/ingest-concept-with-metadata-file
                "umm-g-samples/Collection.json"
                {:provider-id "PROV1"
                 :concept-type :collection
                 :native-id "test-coll1"
                 :format "application/vnd.nasa.cmr.umm+json;version=1.16"})
          granule (data-core/ingest-concept-with-metadata-file
                   "umm-g-samples/GranuleExample2.json"
                   {:provider-id "PROV1"
                    :concept-type :granule
                    :native-id "test-gran1"
                    :format "application/vnd.nasa.cmr.umm+json;version=1.6"})
          {:keys [concept-id revision-id]} granule
          bulk-update {:operation "UPDATE_FIELD"
                       :update-field "AdditionalFile"
                       :updates [["GranuleUR1" "s3://foo/bar"]]}



          {:keys [status task-id errors] :as response} (ingest/bulk-update-granules
                                                        "PROV1" bulk-update bulk-update-options)]
      (index/wait-until-indexed)
      (ingest/update-granule-bulk-update-task-statuses)

      ;; verify the granule status is UPDATED
      (is (= 400 status))
      (is (not (some? task-id)))
      (is (= (str "Bulk Granule Update failed - invalid update format specified for the "
                  "operation:update-field combination [update_field:additionalfile].")
             (first errors)))))

  (testing "Invalid operation:update_field combination"
    (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
          coll (data-core/ingest-concept-with-metadata-file
                "umm-g-samples/Collection.json"
                {:provider-id "PROV1"
                 :concept-type :collection
                 :native-id "test-coll1"
                 :format "application/vnd.nasa.cmr.umm+json;version=1.16"})
          granule (data-core/ingest-concept-with-metadata-file
                   "umm-g-samples/GranuleExample2.json"
                   {:provider-id "PROV1"
                    :concept-type :granule
                    :native-id "test-gran1"
                    :format "application/vnd.nasa.cmr.umm+json;version=1.6"})
          {:keys [concept-id revision-id]} granule
          bulk-update {:operation "UPDATE_TYPE"
                       :update-field "Size"
                       :updates [["Unique_Granule_UR_v1.6" "123"]]}
          {:keys [status task-id errors] :as response} (ingest/bulk-update-granules
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
        (is (= [{:granule-ur "Unique_Granule_UR_v1.6"
                 :status "FAILED"
                 :status-message (str "Bulk Granule Update failed - the operation:update-field "
                                      "combination [update_type:size] is invalid.")}]
               granule-statuses))
        ;;verify metadata is not altered
        (is (not (:metadata (mdb/get-concept concept-id (inc revision-id)))))))))

(deftest update-online-resource-type-test
  (testing "echo10"
    (let [bulk-update-options {:token (echo-util/login (system/context) "user1")}
          collection (data-core/ingest-umm-spec-collection
                      "PROV1"
                      (data-umm-c/collection {:concept-id "C1-PROV1"
                                              :ShortName "MUR-JPL-L4-GLOB-v4.1"
                                              :SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})
                                              :Version "4.1"
                                              :TemporalExtents
                                              [(data-umm-cmn/temporal-extent
                                                {:beginning-date-time "1970-01-01T00:00:00Z"})]}))
          granule (data-core/ingest-concept-with-metadata-file
                   "CMR-7503-echo10-online-resource-type.xml"
                   {:provider-id "PROV1"
                    :concept-type :granule
                    :native-id "test-gran1"
                    :format-key :echo10})
          {:keys [concept-id revision-id]} granule
          bad-granule (data-core/ingest-concept-with-metadata-file
                       "CMR-7503-echo10-bad-data.xml"
                       {:provider-id "PROV1"
                        :concept-type :granule
                        :native-id "test-gran2"
                        :format-key :echo10})]

      (index/wait-until-indexed)

      (testing "using only the Granule UR to identify"
        (let [bulk-update {:name "update opendap link online resource type 1"
                           :operation "UPDATE_TYPE"
                           :update-field "OPeNDAPLink"
                           :updates ["20020602090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc"]}
              response (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)]

          (ingest/update-granule-bulk-update-task-statuses)
          (index/wait-until-indexed)

          (let [original-metadata (:metadata (mdb/get-concept concept-id revision-id))
                updated-metadata (:metadata (mdb/get-concept concept-id (inc revision-id)))]
            (is (= (string/trim (slurp (io/resource "CMR-7503-echo10-online-resource-type.xml")))
                   original-metadata))
            (is (= (slurp (io/resource "CMR-7503-echo10-online-resource-type_updated_by_granuleur.xml"))
                   updated-metadata)))))

      (testing "using the GranuleUR and link type to identify"
        (let [bulk-update {:name "update opendap link online resource type 2"
                           :operation "UPDATE_TYPE"
                           :update-field "OPeNDAPLink"
                           :updates [["20020602090000-JPL-L4_GHRSST-SSTfnd-MUR-GLOB-v02.0-fv04.1.nc" "OTHER LINK"]]}
              response (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)]

          (ingest/update-granule-bulk-update-task-statuses)
          (index/wait-until-indexed)

          (let [latest-metadata (:metadata (mdb/get-concept concept-id))]
            (is (= (slurp (io/resource "CMR-7503-echo10-online-resource-type_updated_by_granuleur_and_type.xml"))
                   latest-metadata)))))

      (testing "error due to duplicate link types in granule"
        (let [bulk-update {:name "update opendap link online resource type 3"
                           :operation "UPDATE_TYPE"
                           :update-field "OPeNDAPLink"
                           :updates ["granule_with_duplicate_opendap_types"]}
              {:keys [status task-id errors] :as response} (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)]

          (index/wait-until-indexed)
          (ingest/update-granule-bulk-update-task-statuses)

          (is (= 200 status))
          (is (some? task-id))

          (let [status-req-options {:query-params {:show_granules "true"}}
                status-response (ingest/granule-bulk-update-task-status task-id status-req-options)
                {:keys [task-status status-message granule-statuses]} status-response]
            (is (= "COMPLETE" task-status))
            (is (= [{:granule-ur "granule_with_duplicate_opendap_types"
                     :status "FAILED"
                     :status-message (str "Cannot update granule - more than one Hyrax-in-the-cloud "
                                          "or more than one on-prem OPeNDAP link was detected in the granule")}]
                   granule-statuses))))))))
