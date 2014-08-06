(ns cmr.system-int-test.search.collection-multiple-conditions-aql-search-test
  "Integration test for collection AQL search with multiple conditions"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "PROV1" "PROV2"))

(deftest aql-search-with-multiple-conditions
  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset1"
                                                :short-name "SHORT"}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset2"
                                                :short-name "Long"}))
        coll3 (d/ingest "PROV2" (dc/collection {:entry-title "Dataset1"
                                                :short-name "Short"}))
        coll4 (d/ingest "PROV2" (dc/collection {:entry-title "Dataset2"
                                                :short-name "LongOne"}))]
    (index/refresh-elastic-index)

    (testing "multiple conditions with aql"
      (are [items conditions data-center-condition]
           (d/refs-match? items
                          (search/find-refs-with-aql :collection conditions data-center-condition))

           [coll1 coll3] [{:dataSetId "Dataset1"} {:shortName "SHORT"}] {}
           [coll1 coll3] [{:dataSetId "Dataset1"} {:shortName "SHORT" :ignore-case true}] {}
           [coll1] [{:dataSetId "Dataset1"} {:shortName "SHORT" :ignore-case false}] {}
           [] [{:dataSetId "Dataset2"} {:shortName "Long%"}] {}
           [] [{:dataSetId "Dataset2"} {:shortName "Long%" :pattern false}] {}
           [coll2 coll4] [{:dataSetId "Dataset2"} {:shortName "Long%" :pattern true}] {}
           [coll1] [{:dataSetId "Dataset1"} {:shortName "SHORT"}] {:dataCenterId "PROV1"}))))