(ns cmr.system-int-test.search.concept-search-with-post-test
  "Integration tests for searching concepts through POST request"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-with-post
  (let [coll1 (d/ingest "PROV1" (dc/collection {:short-name "OneShort"}))
        coll2 (d/ingest "PROV1" (dc/collection {:short-name "OnlyShort"
                                                    :entry-title "Dataset2"}))
        coll3 (d/ingest "PROV1" (dc/collection {:short-name "With Space"}))
        coll4 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset4"}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))
        gran2 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "Granule2"}))
        gran3 (d/ingest "PROV1" (dg/granule coll3 {:granule-ur "Granule 3"}))]

    (index/refresh-elastic-index)

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
  (let [psa1 (dc/psa "alpha" :string)
        psa2 (dc/psa "bravo" :string)
        coll1 (d/ingest "PROV1" (dc/collection {:product-specific-attributes [psa1 psa2]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "alpha" ["ab" "bc"])
                                                                                     (dg/psa "bravo" ["cd" "bf"])]}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:product-specific-attributes [(dg/psa "bravo" ["ab"])]}))]

    (index/refresh-elastic-index)

    (testing "search granule with post, legacy style range parameters"
      (is (d/refs-match?
            [gran1]
            (search/find-refs-with-post :granule {"attribute[][name]" "alpha"
                                                  "attribute[][type]" "string"
                                                  "attribute[][minValue]" "aa"
                                                  "attribute[][maxValue]" "ac"}))))))
