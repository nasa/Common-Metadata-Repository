(ns cmr.system-int-test.search.concept-search-with-post-test
  "Integration tests for searching concepts through POST request"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-with-post
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:ShortName "OneShort"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:ShortName "OnlyShort"
                                                                              :EntryTitle "Dataset2"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:ShortName "With Space"}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 4 {:EntryTitle "Dataset4"}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule1"}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {:granule-ur "Granule2"}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll3 (:concept-id coll3) {:granule-ur "Granule 3"}))]

    (index/wait-until-indexed)

    (testing "search collection with post, single param."
      (are [param value items]
           (d/refs-match? items
                          (search/find-refs-with-post
                            :collection
                            {param value}))
           :short-name "OneShort" [coll1]
           :short-name "With Space" [coll3]
           :short-name ["OneShort" "OnlyShort"] [coll1 coll2]
           :entry-title "Dataset4" [coll4]
           :short-name "NON_EXISTENT" []))

    (testing "search collection with post, multiple params."
      (is (d/refs-match?
            [coll1 coll2]
            (search/find-refs-with-post :collection {"short_name" "On*"
                                                     "options[short_name][pattern]" "true"})))

      (is (d/refs-match?
            [coll2]
            (search/find-refs-with-post :collection {"short-name[]" ["OneShort" "OnlyShort"]
                                                     :entry-title "Dataset2"})))

      (is (d/refs-match?
            []
            (search/find-refs-with-post :collection {"short-name" ["OneShort"]
                                                     :entry-title "Dataset2"}))))

    (testing "search granule with post, single param."
      (are [param value items]
           (d/refs-match? items
                          (search/find-refs-with-post
                            :granule
                            {param value}))
           :granule-ur "Granule1" [gran1]
           :granule-ur "Granule 3" [gran3]
           :granule-ur ["Granule1" "Granule2"] [gran1 gran2]
           :entry-title "Dataset2" [gran2]
           :granule-ur "NON_EXISTENT" []))

    (testing "search granule with post, multiple params."
      (is (d/refs-match?
            [gran2]
            (search/find-refs-with-post :granule {"short-name[]" ["OneShort" "OnlyShort"]
                                                  :granule-ur "Granule2"})))
      (is (d/refs-match?
            []
            (search/find-refs-with-post :granule {:short-name "OneShort"
                                                  :granule-ur "Granule2"}))))))

(deftest search-with-post-legacy-style-range
  (let [psa1 (data-umm-cmn/additional-attribute {:Name "alpha" :DataType "STRING"})
        psa2 (data-umm-cmn/additional-attribute {:Name "bravo" :DataType "STRING"})
        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:AdditionalAttributes [psa1 psa2]}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:product-specific-attributes [(dg/psa "alpha" ["ab" "bc"])
                                                                                     (dg/psa "bravo" ["cd" "bf"])]}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:product-specific-attributes [(dg/psa "bravo" ["ab"])]}))]

    (index/wait-until-indexed)

    (testing "search granule with post, legacy style range parameters"
      (is (d/refs-match?
            [gran1]
            (search/find-refs-with-post :granule {"attribute[][name]" "alpha"
                                                  "attribute[][type]" "string"
                                                  "attribute[][minValue]" "aa"
                                                  "attribute[][maxValue]" "ac"}))))))
