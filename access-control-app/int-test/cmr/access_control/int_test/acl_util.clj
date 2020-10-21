(ns cmr.access-control.int-test.acl-util
  (:require
   [clojure.test :refer :all]
   [cmr.access-control.int-test.fixtures :as fixtures]
   [cmr.access-control.services.acl-util :as acl-util]
   [cmr.access-control.test.util :as u]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]))

(use-fixtures :each (fixtures/reset-fixture))

(deftest acl-log-message
  (let [token (e/login (u/conn-context) "admin")]
    (testing "Create, update, and delete ACL log message function"
      (are3 [new-acl existing-acl action expected-message]
            (is (= expected-message
                   (acl-util/acl-log-message
                     (merge (u/conn-context) {:token token}) new-acl existing-acl action)))

            "Create ACL log message"
            {:group-permissions [{:user-type "guest", :permissions ["create" "delete"]}], :system-identity {:target "TAG_GROUP"}}
            nil
            :create
            "User: [admin] Created ACL [{:group-permissions [{:user-type \"guest\", :permissions [\"create\" \"delete\"]}], :system-identity {:target \"TAG_GROUP\"}}]"

            "Update ACL log message"
            {:group-permissions [{:user-type "guest", :permissions ["create" "delete"]}], :system-identity {:target "TAG_GROUP"}}
            {:group-permissions [{:user-type "guest", :permissions ["create"]}], :system-identity {:target "TAG_GROUP"}}
            :update
            "User: [admin] Updated ACL,\n before: [{:group-permissions [{:user-type \"guest\", :permissions [\"create\"]}], :system-identity {:target \"TAG_GROUP\"}}]\n after: [{:group-permissions [{:user-type \"guest\", :permissions [\"create\" \"delete\"]}], :system-identity {:target \"TAG_GROUP\"}}]"

            "Delete ACL log message"
            "new-acl"
            {:group-permissions [{:user-type "guest", :permissions ["create" "delete"]}], :system-identity {:target "TAG_GROUP"}}
            :delete
            "User: [admin] Deleted ACL [{:group-permissions [{:user-type \"guest\", :permissions [\"create\" \"delete\"]}], :system-identity {:target \"TAG_GROUP\"}}]"))))
