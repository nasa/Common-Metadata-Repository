(ns cmr.system-int-test.search.concept-special-character-search-test
  "Integration tests for searching with special characters"
  (:require 
    [clojure.test :refer :all]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-collection-special-characters
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                      {:EntryTitle "Dataset with degree and smiley face symbols"
                                       :ShortName "ShortName with degree ° and smiley face ☺ symbols"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                                      {:EntryTitle "Dataset with degree ° and smiley face ☺ symbols"
                                       :ShortName "ShortName2"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 
                                      {:EntryTitle "E3"
                                       :ShortName "S3"}))]

    (index/wait-until-indexed)

    (testing "search fields with special characters."
      (are [key value items] (d/refs-match? items
                                            (search/find-refs :collection {key value}))
           :entry-title "Dataset with degree and smiley face symbols" [coll1]
           :entry-title "Dataset with degree ° and smiley face ☺ symbols" [coll2]
           :short-name "ShortName2" [coll2]
           :short-name "ShortName with degree ° and smiley face ☺ symbols" [coll1]))))

(deftest search-granule-special-characters
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 
                                      {:EntryTitle "E1"
                                       :ShortName "ShortName with degree ° and smiley face ☺ symbols"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 
                                      {:EntryTitle "Dataset with degree ° and smiley face ☺ symbols"
                                       :ShortName "S2"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 
                                      {:EntryTitle "E3"
                                       :ShortName "S3"}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "GranuleUR with degree ° and smiley face ☺ symbols"}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {:granule-ur "Granule2"}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll3 (:concept-id coll3) {:granule-ur "Granule3"}))]

    (index/wait-until-indexed)

    (testing "search fields with special characters."
      (are [key value items] (d/refs-match? items
                                            (search/find-refs :granule {key value}))
           :short-name "ShortName with degree ° and smiley face ☺ symbols" [gran1]
           :entry-title "Dataset with degree ° and smiley face ☺ symbols" [gran2]
           :granule-ur "GranuleUR with degree ° and smiley face ☺ symbols" [gran1]))))
