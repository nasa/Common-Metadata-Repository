(ns cmr.system-int-test.search.collection-search-s3-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as umm-c]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest collection-s3-search-test
  (d/ingest-umm-spec-collection
   "PROV1"
   (umm-c/collection
    {:EntryTitle "one"
     :ShortName "one"
     :DirectDistributionInformation
     {:Region "us-west-2"
      :S3BucketAndObjectPrefixNames ["s3://aws.example-1.com"]
      :S3CredentialsAPIEndpoint "http://api.example.com"
      :S3CredentialsAPIDocumentationURL "http://docs.example.com"}}))
  (d/ingest-umm-spec-collection
   "PROV1"
   (umm-c/collection
    {:EntryTitle "two"
     :ShortName "two"
     :DirectDistributionInformation
     {:Region "us-west-2"
      :S3BucketAndObjectPrefixNames ["s3://aws.example-1.com/1" "s3://aws.example-2.com/2"]
      :S3CredentialsAPIEndpoint "http://api.example.com"
      :S3CredentialsAPIDocumentationURL "http://docs.example.com"}}))
  (d/ingest-umm-spec-collection
   "PROV1"
   (umm-c/collection
    {:EntryTitle "none"
     :ShortName "none"}))

  (index/wait-until-indexed)

  (are3 [query s3-links]
        (let [result (-> (search/find-concepts-umm-json :collection query)
                         :body
                         (json/parse-string true)
                         :items
                         first)]
          (is (= s3-links (get-in result [:meta :s3-links]))))

        "with single s3-link available"
        {:entry-title "one"} ["s3://aws.example-1.com"]

        "with multiple s3-links available"
        {:entry-title "two"} ["s3://aws.example-1.com/1" "s3://aws.example-2.com/2"]

        "with no s3-links available"
        {:entry-title "none"} nil))
