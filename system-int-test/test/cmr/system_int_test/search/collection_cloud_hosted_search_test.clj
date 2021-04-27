(ns cmr.system-int-test.search.collection-cloud-hosted-search-test
  "Integration tests for searching for records that are cloud hosted"
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo]
   [cmr.indexer.data.concepts.tag :as itag]
   [cmr.system-int-test.data2.core :as data2]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tag-util :as tags]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       tags/grant-all-tag-fixture]))

(defn- create-direct-dist-info
  "Create a Direct Distribution"
  []
  {:DirectDistributionInformation {:Region "us-east-1"
                                   :S3CredentialsAPIEndpoint "https://example.org"
                                   :S3CredentialsAPIDocumentationURL "https://example.org"}})

(deftest search-collections-that-are-cloud-hosted
  (let [coll1 (data2/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {}))
        coll2 (data2/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {}))
        coll3 (data2/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 (create-direct-dist-info)))]
    (index/wait-until-indexed)

    (testing "Search by the cloud_hosted flag"
      (are [items value]
           (data2/refs-match? items (search/find-refs :collection {:cloud-hosted value}))
           [coll3] true
           [coll1 coll2] false
           [coll1 coll2 coll3] "unset"))

    (testing "Search for collections tagged as cloud hosted"
      (let [user1-token (echo/login (system/context) "user1")
            tag1 (tags/make-tag {:tag-key itag/earthdata-cloud-s3-tag})
            tag_record (tags/save-tag user1-token tag1 [coll1])]
        (index/wait-until-indexed)
        (are [items value]
             (data2/refs-match? items (search/find-refs :collection {:cloud-hosted value}))
             [coll1 coll3] true
             [coll2] false
             [coll1 coll2 coll3] "unset")))))
