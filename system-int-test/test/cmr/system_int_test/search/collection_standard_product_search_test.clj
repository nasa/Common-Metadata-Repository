(ns cmr.system-int-test.search.collection-standard-product-search-test
  "Integration tests for searching for records that are standard products."
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

(deftest search-collections-that-are-standard-products
  (ingest/delete-provider "PROV1")  
  (ingest/create-provider {:provider-guid "provguid_consortium1" :provider-id "PROV1" :consortiums "eosdis"})

  (let [coll1 (data2/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {}))
        coll2 (data2/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:StandardProduct false}))
        coll3 (data2/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:StandardProduct true}))]
    (index/wait-until-indexed)

    (testing "Search by the StandardProduct flag only"
      (util/are3
       [expected items]
       (data2/refs-match? items (search/find-refs :collection {:standard-product expected}))
       "Found using metadata" true [coll3]
       "Not found" false [coll1 coll2]))

    (testing "Search for collections tagged as standard product"
      (let [user1-token (echo/login (system/context) "user1")
            tag1 (tags/make-tag {:tag-key itag/standard-product-tag})
            tag_record (tags/save-tag user1-token tag1 [coll1 coll2])]
        (index/wait-until-indexed)

        (util/are3
         [expected items]
         (is (data2/refs-match? items (search/find-refs :collection {:standard-product expected})))
         "Found using metadata or tags" true [coll1 coll3]
         "Not found" false [coll2])))))
