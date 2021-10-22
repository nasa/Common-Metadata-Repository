(ns cmr.system-int-test.ingest.direct-distribution-ingest-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures join-fixtures]]
   [cmr.common.util :refer [are3]]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"})]))

(deftest direct-distribution-s3-validation
  (testing "S3BucketAndObjectPrefixNames are validated correctly"
    (are3 [s3-buckets status]
          (let [response (data-core/ingest-umm-spec-collection
                          "PROV1"
                          (data-umm-c/collection
                           {:EntryTitle "s3-PROV1"
                            :ShortName "s3 bucket test collection"
                            :DirectDistributionInformation
                            {:Region "us-east-1"
                             :S3BucketAndObjectPrefixNames s3-buckets
                             :S3CredentialsAPIEndpoint "http://api.example.com"
                             :S3CredentialsAPIDocumentationURL "http://docs.example.com"}})
                          {:allow-failure? true})]
            (is (= status (:status response))))
          
          "unescaped JSON array"
          ["[\"s3://aws.example-1.com\", \"s3\"]"] 422

          "csv string"
          ["s3://aws.example-1.com, s3"] 422

          "space delimited string"
          ["s3://aws.example-1.com s3"] 422

          "tab delimited string"
          ["s3://aws.example-1.com\ts3"] 422

          "semi-colon delimited string"
          ["s3://aws.example-1.com;s3"] 422

          "invalid protocol [http]"
          ["http://example-1.com"] 422

          "invalid protocol [https]"
          ["https://example-2.com"] 422

          "invalid protocol [ftp]"
          ["ftp://example-3.com"] 422
          
          "valid entry"
          ["s3://lp-prod-public/HLSL30.020" 
           "podaac-ops-cumulus-public/JASON_CS_S6A_L2_ALT_LR_STD_OST_NRT_F"] 201)))
