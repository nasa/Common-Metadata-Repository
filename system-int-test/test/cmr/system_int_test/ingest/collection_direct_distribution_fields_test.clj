(ns cmr.system-int-test.ingest.collection-direct-distribution-fields-test
  (:require
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as test-util]
   [cmr.common-app.config :as common-config]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.date-time-parser :as date-time-parser]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.mime-types :as mime-types]
   [cmr.common.util :as util]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.granule :as granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.config :as transmit-config]
   [cmr.umm-spec.models.umm-collection-models :as umm-coll]
   [cmr.umm-spec.models.umm-common-models :as umm-cmn]
   [cmr.umm-spec.test.expected-conversion :as expected-conversion]
   [cmr.umm-spec.test.location-keywords-helper :as location-keywords-helper]
   [cmr.umm-spec.umm-spec-core :as umm-spec]))

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
