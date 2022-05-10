(ns cmr.system-int-test.search.collection-cloud-hosted-search-test
  "Integration tests for searching for records that are cloud hosted"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util]
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
  (let [coll1 (data2/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:EntryTitle "Dataset1"}))
        coll2 (data2/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {}))
        coll3 (data2/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 (create-direct-dist-info)))]
    (index/wait-until-indexed)

    (testing "Search by the cloud_hosted flag only"
      (util/are3
        [expected items]
        (data2/refs-match? items (search/find-refs :collection {:cloud-hosted expected}))
        "Found using metadata" true [coll3]
        "Not found" false [coll1 coll2]))

    (testing "coll1 cloud_hosted is false in ATOM and JSON formats before tagging"
      (let [{atom-status :status atom-results :results} (search/find-concepts-atom
                                                         :collection {:dataset-id "Dataset1"})
            {json-status :status json-results :results} (search/find-concepts-json
                                                         :collection {:dataset-id "Dataset1"})
            atom-cloud-hosted (-> atom-results :entries first :cloud-hosted)
            json-cloud-hosted (-> json-results :entries first :cloud-hosted)]
        (is (= 200 atom-status json-status))
        (is (= false atom-cloud-hosted json-cloud-hosted))))

    (testing "Search for collections tagged as cloud hosted"
      (let [user1-token (echo/login (system/context) "user1")
            tag1 (tags/make-tag {:tag-key itag/earthdata-cloud-s3-tag})
            tag_record (tags/save-tag user1-token tag1 [coll1])]
        (index/wait-until-indexed)

        (util/are3
          [expected items]
          (is (data2/refs-match? items (search/find-refs :collection {:cloud-hosted expected})))
          "Found using metadata or tags" true [coll1 coll3]
          "Not found" false [coll2])))

    (testing "coll1 cloud_hosted is true in ATOM and JSON formats after tagging"
      (let [{atom-status :status atom-results :results} (search/find-concepts-atom
                                                         :collection {:dataset-id "Dataset1"})
            {json-status :status json-results :results} (search/find-concepts-json
                                                         :collection {:dataset-id "Dataset1"})
            atom-cloud-hosted (-> atom-results :entries first :cloud-hosted)
            json-cloud-hosted (-> json-results :entries first :cloud-hosted)]
        (is (= 200 atom-status json-status))
        (is (= true atom-cloud-hosted json-cloud-hosted))))))
