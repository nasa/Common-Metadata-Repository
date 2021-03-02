(ns cmr.system-int-test.ingest.granule-bulk-update.granule-bulk-update-test
  "CMR bulk update. Test the actual update "
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.granule :as granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       (search/freeze-resume-time-fixture)]))

(defn- grant-permissions-create-token
 "Test setup to create read/update ingest permissions for bulk update and
 return a token. Bulk update uses update permissions for the actual bulk update
 and read permissions for checking status."
 []
 (let [prov-admin-read-update-group-concept-id (echo-util/get-or-create-group (system/context) "prov-admin-read-update-group")]
   (echo-util/grant-group-provider-admin (system/context) prov-admin-read-update-group-concept-id "PROV1" :read :update)
   ;; Create and return token
   (echo-util/login (system/context) "prov-admin-read-update" [prov-admin-read-update-group-concept-id])))

(deftest bulk-granule-update-test
 (testing "valid request is accepted"
   (let [token (grant-permissions-create-token)
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
                   :granule-ur "SC:AE_5DSno.002:30500512"})))
         gran2 (ingest/ingest-concept
                (data-core/item->concept
                 (granule/granule-with-umm-spec-collection
                  coll1
                  (:concept-id coll1)
                  {:native-id "gran-native1-1"
                   :granule-ur "SC:AE_5DSno.002:30500511"})))
         bulk-update {:name "add opendap links"
                      :operation "UPDATE_FIELD"
                      :update-field "OPEnDAPLink"
                      :updates [["SC:AE_5DSno.002:30500511" "url1234"]
                                ["SC:AE_5DSno.002:30500512" "url3456"]]}
         response (ingest/bulk-update-granules "PROV1"
                                               bulk-update
                                               {:raw? true
                                                :token token})]
     (index/wait-until-indexed)
     (is (= 200 (:status response)))
     (is (not= nil? (:task-id response))))))
