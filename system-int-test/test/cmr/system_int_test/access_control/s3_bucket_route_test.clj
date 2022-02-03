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
                                          {:grant-all-search? false}))

(deftest s3-buckets-route-test
  (let [[user1-token user2-token] (for [user ["user1" "user2"]]
                                    (echo-util/login (system/context) user))

        concept-1 (data-core/ingest-umm-spec-collection
                   "PROV1"
                   (data-umm-c/collection
                    {:EntryTitle "s3-PROV1"
                     :ShortName "s3 bucket test collection"
                     :DirectDistributionInformation
                     {:Region "us-east-1"
                      :S3BucketAndObjectPrefixNames ["s3://aws.example-1.com" "s3"]
                      :S3CredentialsAPIEndpoint "http://api.example.com"
                      :S3CredentialsAPIDocumentationURL "http://docs.example.com"}}))
        concept-2 (data-core/ingest-umm-spec-collection
                   "PROV2"
                   (data-umm-c/collection
                    {:EntryTitle "s3-PROV2"
                     :ShortName "s3 bucket test collection"
                     :DirectDistributionInformation
                     {:Region "us-east-1"
                      :S3BucketAndObjectPrefixNames ["s3://aws.example-2.com" "s3"]
                      :S3CredentialsAPIEndpoint "http://api.example.com"
                      :S3CredentialsAPIDocumentationURL "http://docs.example.com"}}))
        concept-3 (data-core/ingest-umm-spec-collection
                   "PROV3"
                   (data-umm-c/collection
                    {:EntryTitle "s3-PROV3"
                     :ShortName "s3 bucket test collection"
                     :DirectDistributionInformation
                     {:Region "us-east-1"
                      :S3BucketAndObjectPrefixNames ["s3://aws.example-3.com" "s3"]
                      :S3CredentialsAPIEndpoint "http://api.example.com"
                      :S3CredentialsAPIDocumentationURL "http://docs.example.com"}}))

        ;; create all access group
        all-prov-group-id (echo-util/get-or-create-group
                           (system/context)
                           "all-prov-group")

        ;; create limited group
        prov2-group-id (echo-util/get-or-create-group
                        (system/context)
                        "prov2-group")]

    (index/wait-until-indexed)

    ;; Setup group access to providers
    (echo-util/add-user-to-group (system/context)
                                 all-prov-group-id
                                 "user1"
                                 user1-token)

    (echo-util/add-user-to-group (system/context)
                                 prov2-group-id
                                 "user2"
                                 user2-token)


    ;; Grant access to providers to groups
    (doseq [prov ["PROV1" "PROV2" "PROV3"]]
      (echo-util/grant-group (system/context)
                             all-prov-group-id
                             (echo-util/coll-catalog-item-id prov)))

    (echo-util/grant-group (system/context)
                           prov2-group-id
                           (echo-util/coll-catalog-item-id "PROV2"))

    (index/wait-until-indexed)

    ;; Update permitted groups index value for all collections
    (ingest/reindex-collection-permitted-groups (transmit-config/echo-system-token))
    (index/wait-until-indexed)

    (testing "s3 buckets list returned"
      (are3
       [query buckets]
       (let [{:keys [status body]} (client/get
                                    (url/access-control-s3-buckets-url)
                                    {:query-params query
                                     :multi-param-style :array
                                     :headers {transmit-config/token-header transmit-config/echo-system-token}
                                     :accept :json})
             response (sort (json/parse-string body true))]
         (is (= 200 status))
         (is (= buckets response)))

       "User with access to all collections"
       {:user_id "user1"}
       ["s3"
        "s3://aws.example-1.com"
        "s3://aws.example-2.com"
        "s3://aws.example-3.com"]

       "single provider filtering"
       {:user_id "user1"
        :provider ["PROV1"]}
       ["s3"
        "s3://aws.example-1.com"]

       "multiple provider filtering"
       {:user_id "user1"
        :provider ["PROV2" "PROV3"]}
       ["s3" "s3://aws.example-2.com" "s3://aws.example-3.com"]

       "User with restricted access"
       {:user_id "user2"}
       ["s3"
        "s3://aws.example-2.com"]

       "user filtering by provider with no accessible collections"
       {:user_id "user2"
        :provider ["PROV1"]}
       []

       "user filtering on provider they have access to collections"
       {:user_id "user2"
        :provider ["PROV2"]}
       ["s3" "s3://aws.example-2.com"]

       "user filtering on mixed access to provider collections"
       {:user_id "user2"
        :provider ["PROV2" "PROV3"]}
       ["s3" "s3://aws.example-2.com"]))

    (testing "validation"
      (are3
       [query response]
       (let [{:keys [status body]} (client/get
                                    (url/access-control-s3-buckets-url)
                                    {:query-params query
                                     :multi-param-style :array
                                     :headers {transmit-config/token-header transmit-config/echo-system-token}
                                     :accept :json
                                     :throw-exceptions false})]
         (is (= 400 status))
         (is (= response
                (json/parse-string body true))))

       "missing user_id"
       nil
       {:errors ["Parameter [user_id] is required."]}

       "blank user_id"
       {:user_id ""}
       {:errors ["Parameter [user_id] is required."]}

       "invalid user"
       {:user_id "fakeuser"}
       {:errors ["The following users do not exist [fakeuser]"]}

       "invalid provider"
       {:user_id "user1"
        :provider ["PROV1" "PROV42"]}
       {:errors ["Provider with provider-id [PROV42] does not exist."]}

       "unsupported param"
       {:user_id "user1"
        :unexpected "param"}
       {:errors ["Parameter [unexpected] was not recognized."]}))))
