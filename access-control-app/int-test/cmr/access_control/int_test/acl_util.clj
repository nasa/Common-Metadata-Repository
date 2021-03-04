(ns cmr.access-control.int-test.acl-util
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [clj-http.client :as client]
   [cmr.elastic-utils.connect :as es-connect]
   [cmr.access-control.int-test.fixtures :as fixtures]
   [cmr.access-control.services.acl-util :as acl-util]
   [cmr.access-control.test.util :as test-util]
   [cmr.common-app.services.search.elastic-search-index :as common-idx]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.transmit.config :as transmit-config]))

(use-fixtures :once (fixtures/int-test-fixtures))
(use-fixtures :each (fixtures/reset-fixture
                     {"prov1guid" "PROV1"}))

(deftest acl-log-message
  (let [token (e/login (test-util/conn-context) "admin")]
    (testing "Create, update, and delete ACL log message function"
      (are3 [new-acl existing-acl action expected-message]
            (is (= expected-message
                   (acl-util/acl-log-message
                    (merge (test-util/conn-context) {:token token}) new-acl existing-acl action)))

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


(deftest s3-bucket-and-prefixes-for-collection-ids-test
  (let [coll1 (test-util/save-collection
               {:native-id "coll-1"
                :provider-id "PROV1"
                :entry-title "E1"
                :short-name "S1"
                :direct-distribution-information
                {:Region "us-east-1"
                 :S3BucketAndObjectPrefixNames ["s3://aws.example-1.com" "s3-1"]
                 :S3CredentialsAPIEndpoint "https://api.example.com"
                 :S3CredentialsAPIDocumentationURL "https://docs.example.com"}})
        coll2 (test-util/save-collection
               {:native-id "coll-2"
                :provider-id "PROV1"
                :entry-title "E2"
                :short-name "S2"
                :direct-distribution-information
                {:Region "us-east-1"
                 :S3BucketAndObjectPrefixNames ["s3://aws.example-2.com" "s3-2"]
                 :S3CredentialsAPIEndpoint "https://api.example.com"
                 :S3CredentialsAPIDocumentationURL "https://docs.example.com"}})]

    (is (= {coll1 ["s3://aws.example-1.com" "s3-1"]
            coll2 ["s3://aws.example-2.com" "s3-2"]}
           (acl-util/s3-bucket-and-prefixes-for-collection-ids
            (test-util/conn-context)
            [coll1 coll2])))))
