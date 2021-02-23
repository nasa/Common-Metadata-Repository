(ns cmr.system-int-test.ingest.collection-direct-distribution-fields-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.umm-spec.models.umm-collection-models :as umm-coll]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(deftest direct-distribution-information-fields-test
  (let [raw-concept (data-umm-c/collection-concept
                     {:DirectDistributionInformation
                      (umm-coll/map->DirectDistributionInformationType
                       {:Region "us-east-1"
                        :S3BucketAndObjectPrefixNames ["s3.example.com" "ddi"]
                        :S3CredentialsAPIEndpoint "https://api.example.com"
                        :S3CredentialsAPIDocumentationURL "https://docs.example.com"})})
        {:keys [concept-id status]} (ingest/ingest-concept raw-concept)]

    (index/wait-until-indexed)

    (is (= 201 status))
    (is (and (string? concept-id)
             (not (nil? concept-id))))

    (testing "direct distribution information exists on returned value"
      (let [{:keys [body]} (search/retrieve-concept concept-id)]
        (is (re-find #"<S3BucketAndObjectPrefixName>s3.example.com</S3BucketAndObjectPrefixName>" body))
        (is (re-find #"<S3BucketAndObjectPrefixName>ddi</S3BucketAndObjectPrefixName>" body))))

    (testing "direct distribution information exists in elasticsearch"
      (let [es-doc (index/get-collection-es-doc-by-concept-id concept-id)
            es-source (:_source es-doc)]
        (is (= concept-id (:concept-id es-source)))
        (is (= ["s3.example.com" "ddi"]
               (get-in es-source [:s3-bucket-and-object-prefix-names])))

        (testing "ddi sub-document"
          (is (map? (get-in es-source [:direct-distribution-information])))
          (is (= ["s3.example.com" "ddi"]
                 (get-in es-source [:direct-distribution-information
                                    :s3-bucket-and-object-prefix-names]))))))))

(deftest no-direct-distribution-information-fields-test
  (let [raw-concept (data-umm-c/collection-concept {})
        {:keys [concept-id status]} (ingest/ingest-concept raw-concept)]

    (index/wait-until-indexed)

    (is (= 201 status))
    (is (and (string? concept-id)
             (not (nil? concept-id))))

    (testing "direct distribution information exists in elasticsearch"
      (let [es-doc (index/get-collection-es-doc-by-concept-id concept-id)
            es-source (:_source es-doc)]
        (is (= concept-id (:concept-id es-source)))
        (is (contains? es-source :s3-bucket-and-object-prefix-names))

        (testing "ddi sub-document exists, even if empty"
          (is (map? (get-in es-source [:direct-distribution-information]))))))))
