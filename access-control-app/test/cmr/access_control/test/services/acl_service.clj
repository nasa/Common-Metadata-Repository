(ns cmr.access-control.test.services.acl-service
  (:require
    [clojure.test :refer :all]
    [cmr.access-control.services.acl-search-service :as acl-search]
    [cmr.access-control.services.acl-service :as acl-service]
    [cmr.access-control.test.util :as u]
    [cmr.common.util :as util :refer [are3]]
    [cmr.mock-echo.client.echo-util :as e]))

(deftest valid-permitted-group?
  (testing "Valid permitted groups"
    (are [group]
         (#'acl-search/valid-permitted-group? group)

         "guest"
         "registered"
         "GUEST"
         "Registered"
         "AG1234-CMR"
         "ag1234-CMR"
         "Ag1234-CMR"
         "aG1234-CMR"
         "AG1234-PROV1"))

  (testing "Invalid permitted groups"
    (are [group]
         (not (#'acl-search/valid-permitted-group? group))

         ""
         "gust"
         "A1234-PROV1"
         "G1234-PROV1"
         "AG1234PROV1"
         "AG*-PROV1"
         "AG123?-PROV1")))

(deftest acl-log-message
  (let [token (e/login (u/conn-context) "admin")]
    (testing "Create, update, and delete ACL log message function"
      (are3 [new-acl existing-acl action expected-message]
            (is (= expected-message (acl-service/acl-log-message (merge (u/conn-context) {:token token}) new-acl existing-acl action)))

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
