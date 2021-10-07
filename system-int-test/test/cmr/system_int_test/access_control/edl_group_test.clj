(ns cmr.system-int-test.access-control.edl-group-test
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [cmr.access-control.services.acl-validation :as acl-validation]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"
                                           "provguid3" "PROV3"}))

(deftest create-acl-with-edl-id
  (let [metadata (json/generate-string {:group_permissions [{:group_id "EDLGroupName1"
                                                             :permissions ["read" "order"]}]
                                        :catalog_item_identity {:name "test"
                                                                :provider_id "PROV1"
                                                                :granule_applicable true
                                                                :collection_applicable true}})
        params {:method :post
                :url (url/access-control-acls-url)
                :body metadata
                :headers {:Authorization "mock-echo-system-token"}
                :content-type "application/json"
                :connection-manager (system/conn-mgr)}]

    (testing "Can make an ACL with an EDL group ID when toggle set true"
      (dev-sys-util/eval-in-dev-sys `(acl-validation/set-allow-edl-groups! true))
      (is (re-find
            #"concept_id"
            (:body (client/request params)))))
    (testing "Error returned when try to ingest ACL with an EDL group ID when toggle set flase"
      (dev-sys-util/eval-in-dev-sys `(acl-validation/set-allow-edl-groups! false))
      (is (thrown? java.lang.Exception
                   (client/request params))))))
