(ns cmr.access-control.test.services.acl-service
  (require [clojure.test :refer :all]
           [cmr.access-control.test.util :as u]
           [cmr.mock-echo.client.echo-util :as e]
           [cmr.common.util :as util :refer [are3]]
           [cmr.access-control.services.acl-service :as as]))

(deftest valid-permitted-group?
  (testing "Valid permitted groups"
    (are [group]
         (#'as/valid-permitted-group? group)

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
         (not (#'as/valid-permitted-group? group))

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
            (is (= expected-message (as/acl-log-message (merge (u/conn-context) {:token token}) new-acl existing-acl action)))

            "Create ACL log message"
            "new-acl"
            nil
            :create
            "User: [admin] Created ACL [new-acl]"

            "Update ACL log message"
            "new-acl"
            "existing-acl"
            :update
            "User: [admin] Updated ACL,\n before: [existing-acl]\n after: [new-acl]"

            "Delete ACL log message"
            "new-acl"
            "existing-acl"
            :delete
            "User: [admin] Deleted ACL,\n before: [existing-acl]\n after: [new-acl]"))))
