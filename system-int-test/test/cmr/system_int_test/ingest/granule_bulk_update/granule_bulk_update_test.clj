(ns cmr.system-int-test.ingest.granule-bulk-update.granule-bulk-update-test
  "CMR bulk update. Test the actual update "
  (:require
   [clojure.java.io :as io]
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
        gran3 (ingest/ingest-concept
               (data-core/item->concept
                (granule/granule-with-umm-spec-collection
                 coll1
                 (:concept-id coll1)
                 {:native-id "gran-native1-3"
                  :granule-ur "SC:AE_5DSno.002:30500513"})
                :umm-json))]
    (testing "successful granule bulk update"
      (let [bulk-update {:name "add opendap links"
                         :operation "UPDATE_FIELD"
                         :update-field "OPeNDAPLink"
                         :updates [["SC:AE_5DSno.002:30500511" "https://url30500511"]
                                   ["SC:AE_5DSno.002:30500512" "https://url30500512"]]}
            response (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)]
        (index/wait-until-indexed)
        (is (= 200 (:status response)))
        (is (not (nil? (:task-id response))))
        ;; TODO: detailed status check will be added in CMR-7092
        ))
    (testing "partial successful granule bulk update"
      (let [bulk-update {:name "add opendap links"
                         :operation "UPDATE_FIELD"
                         :update-field "OPeNDAPLink"
                         :updates [["SC:AE_5DSno.002:30500513" "https://url30500513"]]}
            response (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)]
        (index/wait-until-indexed)
        (is (= 200 (:status response)))
        (is (not (nil? (:task-id response))))
        ;; TODO: detailed status check will be added in CMR-7092
        ))
    (testing "failed granule bulk update"
      (let [bulk-update {:name "add opendap links"
                         :operation "UPDATE_FIELD"
                         :update-field "OPeNDAPLink"
                         :updates [["SC:AE_5DSno.002:30500511" "https://url30500511"]
                                   ["SC:AE_5DSno.002:30500512" "https://url30500512"]
                                   ["SC:AE_5DSno.002:30500513" "https://url30500513"]]}
            response (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)]
        (index/wait-until-indexed)
        (is (= 200 (:status response)))
        (is (not (nil? (:task-id response))))
        ;; TODO: detailed status check will be added in CMR-7092
        ))))

(deftest add-opendap-url
  "test adding OPeNDAP url with real granule file that is already in CMR code base"
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
        {:keys [status] :as response} (ingest/bulk-update-granules "PROV1" bulk-update bulk-update-options)]
    (index/wait-until-indexed)
    (is (= 200 status))
    (is (= target-metadata
           (:metadata (mdb/get-concept concept-id (inc revision-id)))))))
