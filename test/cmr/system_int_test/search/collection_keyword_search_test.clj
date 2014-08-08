(ns cmr.system-int-test.search.collection-keyword-search-test
  "Integration test for CMR collection search by keyword terms"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1" "CMR_PROV2"))

(deftest search-by-keywords
  (let [psa1 (dc/psa "alpha" :string "ab")
        psa2 (dc/psa "bravo" :string "bf")
        psa3 (dc/psa "charlie" :string "foo")
        psa4 (dc/psa "case" :string "up")
        p1 (dc/platform "platform_Sn B")
        p2 (dc/platform "platform_SnA")
        sk1 (dc/science-keyword {:category "Cat1"
                                 :topic "Topic1"
                                 :term "Term1"
                                 :variable-level-1 "Level1-1"
                                 :variable-level-2 "Level1-2"
                                 :variable-level-3 "Level1-3"})
        sk2 (dc/science-keyword {:category "Hurricane"
                                 :topic "Laser platform_SnA"
                                 :term "Extreme"
                                 :variable-level-1 "Level2-1"
                                 :variable-level-2 "Level2-2"
                                 :variable-level-3 "Level2-3"})
        tdcs1 (dc/two-d "XYZ")
        coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "ABC!XYZ"}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:collection-data-type "Foo"}))
        coll4 (d/ingest "CMR_PROV2" (dc/collection {:collection-data-type "Bar"}))
        coll5 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "ABC" :short-name "Space!Laser"}))
        coll6 (d/ingest "CMR_PROV2" (dc/collection {:organizations [(dc/org :archive-center "Some&Place")]}))
        coll7 (d/ingest "CMR_PROV2" (dc/collection {:version-id "Laser"}))
        coll8 (d/ingest "CMR_PROV2" (dc/collection {:processing-level-id "PDQ123"}))
        coll9 (d/ingest "CMR_PROV2" (dc/collection {:science-keywords [sk1 sk2]}))
        coll10 (d/ingest "CMR_PROV2" (dc/collection {:spatial-keywords ["in out"]}))
        coll11 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p1 p2]}))
        coll12 (d/ingest "CMR_PROV2" (dc/collection {:product-specific-attributes [psa1 psa2 psa3 psa4]}))
        coll13 (d/ingest "CMR_PROV2" (dc/collection {:two-d-coordinate-systems [tdcs1]}))
        coll14 (d/ingest "CMR_PROV2" (dc/collection {:long-name "platform_SnA laser"}))]

    (index/refresh-elastic-index)

    (testing "search by keywords."
      (are [keyword-str items] (d/refs-match? items (search/find-refs :collection {:keyword keyword-str}))
           "ABC" [coll2 coll5]
           "XYZ" [coll2 coll13]
           "place" [coll6]
           "Laser" [coll5 coll7 coll9 coll14]
           "ABC place Hurricane" [coll2 coll5 coll6 coll9]
           "BLAH" []))
    (testing "search by keywords using wildcard *."
      (are [keyword-str items] (d/refs-match? items (search/find-refs :collection {:keyword keyword-str}))
           "A*C" [coll2 coll5]
           "XY*" [coll2 coll13]
           "*aser" [coll5 coll7 coll9 coll14]
           "ABC p*ce Hurricane" [coll2 coll5 coll6 coll9]))
    (testing "search by keywords using wildcard ?."
      (are [keyword-str items] (d/refs-match? items (search/find-refs :collection {:keyword keyword-str}))
           "A?C" [coll2 coll5]
           "XY?" [coll2 coll13]
           "?aser" [coll5 coll7 coll9 coll14]
           "ABC ?lace Hurricane" [coll2 coll5 coll6 coll9]))
    (testing "sorted search by keywords."
      (are [keyword-str items] (d/refs-match-order? items (search/find-refs :collection {:keyword keyword-str}))
           "Laser platform_SnA" [coll14 coll11 coll9 coll5 coll7]
           "La?er platform_SnA" [coll14 coll11 coll9 coll5 coll7]
           "L*er platfor*_SnA" [coll14 coll11 coll9 coll5 coll7]
           "L?s* plat?o*_SnA" [coll14 coll11 coll9 coll5 coll7]))
    (testing "sorted search by keywords with sort keys."
      (are [keyword-str sort-key items] (d/refs-match-order? items
                                                              (search/find-refs
                                                                :collection {:keyword keyword-str
                                                                             :sort-key sort-key}))
           "Laser platform_SnA" "-entry-title" [coll14 coll11 coll9 coll7 coll5]
           "La?er platform_SnA" "score" [coll14 coll11 coll9 coll5 coll7]
           "Laser platfor*_SnA" "+score" [coll5 coll7 coll9 coll11 coll14]
           "L?s* plat?o*_SnA" "-score" [coll14 coll11 coll9 coll5 coll7]))))