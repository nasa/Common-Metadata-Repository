(ns cmr.system-int-test.access-control.s3-bucket-route-test
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as ac-util]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.config :as transmit-config]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"
                                           "provguid3" "PROV3"}
                                          {:grant-all-search? false
                                           :grant-all-ingest? false}))

(deftest s3-buckets-route-test
  (let [all-prov-update-group-id (echo-util/get-or-create-group
                                  (system/context)
                                  "all-prov-admin-update-group")

        prov2-update-group-id (echo-util/get-or-create-group
                               (system/context)
                               "prov2-admin-update-group")

        user1-token (echo-util/login
                     (system/context) "user1"
                     [all-prov-update-group-id])

        user2-token (echo-util/login
                     (system/context) "user2"
                     [prov2-update-group-id])

        _ (echo-util/grant-group-provider-admin
           (system/context) all-prov-update-group-id "PROV1" :update)

        _ (echo-util/grant-group-provider-admin
           (system/context) all-prov-update-group-id "PROV2" :update)

        _ (echo-util/grant-group-provider-admin
           (system/context) prov2-update-group-id "PROV2" :update)

        _ (echo-util/grant-group-provider-admin
           (system/context) all-prov-update-group-id "PROV3" :update)

        _ (echo-util/add-user-to-group (system/context)
                                       all-prov-update-group-id
                                       "user1"
                                       user1-token)

        concept-1 (data-core/ingest-umm-spec-collection
                   "PROV1"
                   (data-umm-c/collection
                    {:EntryTitle "s3-PROV1"
                     :ShortName "s3 bucket test collection"
                     :DirectDistributionInformation
                     {:Region "us-east-1"
                      :S3BucketAndObjectPrefixNames ["s3://aws.example-1.com" "s3"]
                      :S3CredentialsAPIEndpoint "http://api.example.com"
                      :S3CredentialsAPIDocumentationURL "http://docs.example.com"}})
                   {:token user1-token}                   )
        concept-2 (data-core/ingest-umm-spec-collection
                   "PROV2"
                   (data-umm-c/collection
                    {:EntryTitle "s3-PROV2"
                     :ShortName "s3 bucket test collection"
                     :DirectDistributionInformation
                     {:Region "us-east-1"
                      :S3BucketAndObjectPrefixNames ["s3://aws.example-2.com" "s3"]
                      :S3CredentialsAPIEndpoint "http://api.example.com"
                      :S3CredentialsAPIDocumentationURL "http://docs.example.com"}})
                   {:token user1-token})
        concept-3 (data-core/ingest-umm-spec-collection
                   "PROV3"
                   (data-umm-c/collection
                    {:EntryTitle "s3-PROV3"
                     :ShortName "s3 bucket test collection"
                     :DirectDistributionInformation
                     {:Region "us-east-1"
                      :S3BucketAndObjectPrefixNames ["s3://aws.example-3.com" "s3"]
                      :S3CredentialsAPIEndpoint "http://api.example.com"
                      :S3CredentialsAPIDocumentationURL "http://docs.example.com"}})
                   {:token user1-token})]

    (index/wait-until-indexed)

    (testing "s3 buckets list returned for admin user"
      (are3
       [query buckets]
       (let [{:keys [status body]} (client/get
                                    (url/access-control-s3-buckets-url)
                                    {:query-params query
                                     :multi-param-style :array
                                     :headers {transmit-config/token-header transmit-config/mock-echo-system-token}
                                     :accept :json})
             response (json/parse-string body true)]
         (is (= 200 status))
         (is (= buckets response)))

       ;; Admin user cases
       "user with :update permission with all providers"
       {:user "user1"}
       ["s3"
        "s3://aws.example-1.com"
        "s3://aws.example-2.com"
        "s3://aws.example-3.com"]

       "non-existant provider"
       {:user "user1"
        :provider ["fake"]} []

       "user with :update permission on a single provider"
       {:user "user1"
        :provider ["PROV1"]}
       ["s3" "s3://aws.example-1.com"]

       "user with :update permission on multiple providers"
       {:user "user1"
        :provider ["PROV2" "PROV3"]}
       ["s3" "s3://aws.example-2.com" "s3://aws.example-3.com"]

       ;; Limited user cases
       "user with :update permission with all providers"
       {:user "user2"}
       ["s3"
        "s3://aws.example-2.com"]

       "non-existant provider"
       {:user "user2"
        :provider ["fake"]} []

       "user without :update permission on a single provider"
       {:user "user2"
        :provider ["PROV1"]}
       []

       "user with :update permission on a single provider"
       {:user "user2"
        :provider ["PROV2"]}
       ["s3" "s3://aws.example-2.com"]

       "user with :update permission on multiple providers"
       {:user "user2"
        :provider ["PROV2" "PROV3"]}
       ["s3" "s3://aws.example-2.com"]

       ;; Guest user cases
       "user without :update permissions on all providers"
       {:user "guest"}
       []

       "user without :update permissions on single provider"
       {:user "guest"
        :provider ["PROV1"]}
       []

       "invalid user"
       {:user "guoost"
        :provider ["PROV1"]}
       []))))
