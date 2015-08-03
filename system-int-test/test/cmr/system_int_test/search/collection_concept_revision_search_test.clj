(ns cmr.system-int-test.search.collection-concept-revision-search-test
  "Integration test for collection all revisions search"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.util :refer [are2] :as util]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-collection-all-revisions
  (let [coll1-1 (d/ingest "PROV1" (dc/collection {:entry-title "et1"}))
        concept {:provider-id "PROV1"
                 :concept-type :collection
                 :native-id (:entry-title coll1-1)}
        coll1-2 (merge (ingest/delete-concept concept) concept {:deleted true})
        coll1-3 (d/ingest "PROV1" (dc/collection {:entry-title "et1"}))]
    (index/wait-until-indexed)

    (testing "find-references-with-all-revisions"
      (are2 [collections params]
            (d/refs-match? collections (search/find-refs :collection params))

            "all-revisions=false"
            [coll1-3]
            {:provider-id "PROV1" :all-revisions false}

            "all-revisions=true"
            [coll1-1 coll1-2 coll1-3]
            {:provider-id "PROV1" :all-revisions true}))))

(deftest search-granule-all-revisions
  (testing "granule search with all_revisions parameter is not supported"
    (let [{:keys [status errors]} (search/get-search-failure-data
                                    (search/find-refs :granule {:provider-id "PROV1"
                                                                :all-revisions false}))]
      (is (= [400 ["Parameter [all_revisions] was not recognized."]]
             [status errors])))))

