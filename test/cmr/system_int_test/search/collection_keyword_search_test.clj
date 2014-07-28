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
        sk1 (dc/science-keyword {:category "Cat1"
                                 :topic "Topic1"
                                 :term "Term1"
                                 :variable-level-1 "Level1-1"
                                 :variable-level-2 "Level1-2"
                                 :variable-level-3 "Level1-3"
                                 :detailed-variable "Detail1"})
        sk2 (dc/science-keyword {:category "Hurricane"
                                 :topic "Popular"
                                 :term "Extreme"
                                 :variable-level-1 "Level2-1"
                                 :variable-level-2 "Level2-2"
                                 :variable-level-3 "Level2-3"
                                 :detailed-variable "UNIVERSAL"})
        coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:entry-id "ABC!XYZ"}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:collection-data-type "Foo"}))
        coll4 (d/ingest "CMR_PROV2" (dc/collection {:collection-data-type "Bar"}))
        coll5 (d/ingest "CMR_PROV2" (dc/collection {:short-name "Short name"}))
        coll6 (d/ingest "CMR_PROV2" (dc/collection {:archive-center "Some$Place"}))
        coll7 (d/ingest "CMR_PROV2" (dc/collection {:version-id "ABC123"}))
        coll8 (d/ingest "CMR_PROV2" (dc/collection {:processing-level-id "PDQ123"}))
        coll9 (d/ingest "CMR_PROV2" (dc/collection {:science-keywords [sk1 sk2]}))
        coll10 (d/ingest "CMR_PROV2" (dc/collection {:spatial-keywords ["in out"]}))
        coll11 (d/ingest "CMR_PROV2" (dc/collection {:platform-sn "Space Laser"}))
        coll12 (d/ingest "CMR_PROV2" (dc/collection {:product-specific-attributes [psa1 psa2 psa3 psa4]}))
        coll13 (d/ingest "CMR_PROV2" (dc/collection {:two-d-coord-name "XY"}))]

    (index/refresh-elastic-index)

    (testing "search by keywords."
      (are [keyword-str items] (d/refs-match? items (search/find-refs :collection {:keyword keyword-str}))
           "ABC" [coll2]
           "XYZ" [coll2 coll13]
           "place" [coll6]
           "Laser" [coll11]
           "ABC place Huricane" [coll2 coll6 coll9]
           "BLAH" []))
    ; (testing "search by spatial keywords using wildcard *."
    ;   (is (d/refs-match? [coll3 coll4 coll5 coll7]
    ;                      (search/find-refs :collection
    ;                                        {:spatial-keyword "D*"
    ;                                         "options[spatial-keyword][pattern]" "true"}))))
    ; (testing "search by spatial keywords using wildcard ?."
    ;   (is (d/refs-match? [coll4 coll6]
    ;                      (search/find-refs :collection
    ;                                        {:spatial-keyword "L?"
    ;                                         "options[spatial-keyword][pattern]" "true"}))))
    ; (testing "search by spatial keywords case default is ignore case true."
    ;   (is (d/refs-match? [coll5 coll7]
    ;                      (search/find-refs :collection
    ;                                        {:spatial-keyword "detroit"}))))
    ; (testing "search by spatial keywords ignore case false."
    ;   (is (d/refs-match? [coll7]
    ;                      (search/find-refs :collection
    ;                                        {:spatial-keyword "detroit"
    ;                                         "options[spatial-keyword][ignore-case]" "false"}))))
    ; (testing "search by spatial keywords ignore case true."
    ;   (is (d/refs-match? [coll5 coll7]
    ;                      (search/find-refs :collection
    ;                                        {:spatial-keyword "detroit"
    ;                                         "options[spatial-keyword][ignore-case]" "true"}))))
    ; (testing "search by spatial keywords, options :or."
    ;   (is (d/refs-match? [coll3 coll4]
    ;                      (search/find-refs :collection {"spatial-keyword[]" ["DC" "LA"]
    ;                                                     "options[spatial-keyword][or]" "true"}))))
    ; (testing "search by spatial keywords, options :and."
    ;   (is (d/refs-match? [coll4]
    ;                      (search/find-refs :collection {"spatial-keyword[]" ["DC" "LA"]
    ;                                                     "options[spatial-keyword][and]" "true"}))))))
    ))
