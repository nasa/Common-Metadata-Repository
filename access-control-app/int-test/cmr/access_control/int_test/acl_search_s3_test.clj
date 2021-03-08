(ns cmr.access-control.int-test.acl-search-s3-test
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clj-time.core :as time]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.access-control.data.access-control-index :as access-control-index]
   [cmr.access-control.int-test.fixtures :as fixtures]
   [cmr.access-control.test.util :as test-util]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.transmit.access-control :as access-control]
   [ring.mock.request :refer [request]]))


(def coll1 (atom nil))
(def coll2 (atom nil))

(defn create-collections
  [f]
  (reset! coll1 (test-util/save-collection
                 {:native-id "coll-1"
                  :provider-id "PROV1"
                  :entry-title "E1"
                  :short-name "S1"
                  :direct-distribution-information
                  {:Region "us-east-1"
                   :S3BucketAndObjectPrefixNames ["s3://aws.example-1.com" "s3-1-1"]
                   :S3CredentialsAPIEndpoint "https://api.example.com"
                   :S3CredentialsAPIDocumentationURL "https://docs.example.com"}}))

  (reset! coll2 (test-util/save-collection
                 {:native-id "coll-2"
                  :provider-id "PROV2"
                  :entry-title "E2"
                  :short-name "S2"
                  :direct-distribution-information
                  {:Region "us-east-1"
                   :S3BucketAndObjectPrefixNames ["s3://aws.example-1.com" "s3-1-2"]
                   :S3CredentialsAPIEndpoint "https://api.example.com"
                   :S3CredentialsAPIDocumentationURL "https://docs.example.com"}}))
  (f))

(use-fixtures :once (fixtures/int-test-fixtures))

(use-fixtures :each
  (join-fixtures [(fixtures/reset-fixture {"prov1guid" "PROV1"
                                           "prov2guid" "PROV2"}
                                          ["user1" "user2"])
                  (fixtures/grant-admin-group-fixture ["PROV1" "PROV2"])
                  (fixtures/grant-all-acl-fixture)
                  create-collections]))

(deftest s3-search-by-collection-id-test
  (let [{:keys [status body]} (client/get "http://localhost:3011/s3-buckets"
                                          {:query-params {:user_id "user1"
                                                          :concept_id [@coll1 @coll2]}})
        buckets (json/parse-string body true)]

    (is (= ["s3-1-1" "s3-1-2" "s3://aws.example-1.com"]
           buckets))))

(deftest s3-search-by-provider-id-test
  (are3 [provider expected]
        (let [{:keys [status body]} (client/get "http://localhost:3011/s3-buckets"
                                                {:query-params {:user_id "user1"
                                                                :concept_id [@coll1 @coll2]}})
              buckets (json/parse-string body true)]
          (is (= expected buckets)))

        "PROV1"
        "prov1" ["s3-1-1" "s3://aws.example-1.com"]

        "PROV2"
        "prov2" ["s3-1-2" "s3://aws.example-1.com"]))
