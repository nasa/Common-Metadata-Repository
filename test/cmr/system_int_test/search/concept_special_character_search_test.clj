(ns cmr.system-int-test.search.concept-special-character-search-test
  "Integration tests for searching with special characters"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-collection-special-characters
  (let [coll1 (d/ingest "PROV1" (dc/collection
                                      {:entry-title "Dataset with degree and smiley face symbols"
                                       :short-name "ShortName with degree ° and smiley face ☺ symbols"}))
        coll2 (d/ingest "PROV1" (dc/collection
                                      {:entry-title "Dataset with degree ° and smiley face ☺ symbols"
                                       :short-name "ShortName2"}))
        coll3 (d/ingest "PROV1" (dc/collection {}))]

    (index/refresh-elastic-index)

    (testing "search fields with special characters."
      (are [key value items] (d/refs-match? items
                                            (search/find-refs :collection {key value}))
           :entry-title "Dataset with degree and smiley face symbols" [coll1]
           :entry-title "Dataset with degree ° and smiley face ☺ symbols" [coll2]
           :short-name "ShortName2" [coll2]
           :short-name "ShortName with degree ° and smiley face ☺ symbols" [coll1]))))

(deftest search-granule-special-characters
  (let [coll1 (d/ingest "PROV1" (dc/collection {:short-name "ShortName with degree ° and smiley face ☺ symbols"}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset with degree ° and smiley face ☺ symbols"}))
        coll3 (d/ingest "PROV1" (dc/collection {}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "GranuleUR with degree ° and smiley face ☺ symbols"}))
        gran2 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "Granule2"}))
        gran3 (d/ingest "PROV1" (dg/granule coll3 {:granule-ur "Granule3"}))]

    (index/refresh-elastic-index)

    (testing "search fields with special characters."
      (are [key value items] (d/refs-match? items
                                            (search/find-refs :granule {key value}))
           :short-name "ShortName with degree ° and smiley face ☺ symbols" [gran1]
           :entry-title "Dataset with degree ° and smiley face ☺ symbols" [gran2]
           :granule-ur "GranuleUR with degree ° and smiley face ☺ symbols" [gran1]))))
