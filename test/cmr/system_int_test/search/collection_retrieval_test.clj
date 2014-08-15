(ns cmr.system-int-test.search.collection-retrieval-test
  "Integration test for collection retrieval with cmr-concept-id"
  (:require [clojure.test :refer :all]
            [cmr.umm.echo10.collection :as c]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest retrieve-collection-by-cmr-concept-id
  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset1"
                                                    :projects (dc/projects "ESI_1")}))
        coll1 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset1"
                                                    :projects (dc/projects "ESI_2")}))
        umm-coll (dc/collection {:entry-title "Dataset1"
                                 :projects (dc/projects "ESI_3")})
        coll1 (d/ingest "PROV1" umm-coll)
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset2"}))]
    (index/refresh-elastic-index)
    (testing "retrieval by collection cmr-concept-id returns the latest revision."
      (let [response (search/get-concept-by-concept-id (:concept-id coll1))
            parsed-collection (c/parse-collection (:body response))]
        (is (= umm-coll parsed-collection))))
    (testing "retrieval by collection cmr-concept-id, not found."
      (let [response (search/get-concept-by-concept-id "C1111-PROV1")]
        (is (= 404 (:status response)))
        (re-find #"Failed to retrieve concept C1111-PROV1 from metadata-db:" (:body response))
        (re-find #"Concept with concept-id \[C1111-PROV1\] does not exist" (:body response))))))

(deftest search-with-slashes-in-dataset-id
  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset1"}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset/With/Slashes"}))
        coll3 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset3"}))
        coll4 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset/With/More/Slashes"}))
        coll5 (d/ingest "PROV1" (dc/collection {}))]

    (index/refresh-elastic-index)

    (testing "search for dataset with slashes"
      (are [dataset-id items] (d/refs-match? items (search/find-refs :collection {:dataset-id dataset-id}))
           "Dataset/With/Slashes" [coll2]
           "BLAH" []))))