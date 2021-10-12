(ns cmr.system-int-test.access-control.edl-group-test
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [cmr.access-control.services.acl-validation :as acl-validation]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.ingest-util :as ingest]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"
                                           "provguid3" "PROV3"}))

(deftest create-acl-with-edl-id
  (let [acl {:group_permissions [{:group_id "EDLGroupName1"
                                  :permissions ["read" "order"]}]
             :catalog_item_identity {:name "test"
                                     :provider_id "PROV1"
                                     :granule_applicable true
                                     :collection_applicable true}}]

    (testing "Can make an ACL with an EDL group ID when toggle set true"
      (dev-sys-util/eval-in-dev-sys `(acl-validation/set-allow-edl-groups! true))
      (is (= 200 (:status (data-core/create-acl acl)))))

    (testing "Error returned when try to ingest ACL with an EDL group ID when toggle set flase"
      (dev-sys-util/eval-in-dev-sys `(acl-validation/set-allow-edl-groups! false))
      (is (thrown? java.lang.Exception (data-core/create-acl acl))))))
