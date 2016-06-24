(ns cmr.system-int-test.search.granule-search-readable-granule-name-test
  "Integration tests for searching by readable granule name"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn assert-too-many-wildcards
  [response]
  (is (.contains ^String (first (:errors response))
                  "conditions which used a leading wildcard")))

(defn assert-not-too-many-wildcards
  [response]
  (is (= 0 (:hits response))))

;; Validates that query validation prevents too many leading wildcard patterns from being searched.
(deftest number-of-leading-wildcard-patterns-validation
  (testing "Too many patterns single field"
    ;; readable-granule-name searches two fields so there are twice as many patterns in the query
    (assert-too-many-wildcards
     (search/find-refs :granule {:readable-granule-name ["*1" "*2" "*3"]
                                 "options[readable-granule-name][pattern]" "true"}))
    (assert-too-many-wildcards
     (search/find-refs :granule {:producer-granule-id ["*1" "*2" "*3" "*4" "*5" "*6"]
                                 "options[producer-granule-id][pattern]" "true"}))
    (assert-too-many-wildcards
     (search/find-refs :granule {:producer-granule-id ["?1" "?2" "?3" "?4" "?5" "?6"]
                                 "options[producer-granule-id][pattern]" "true"})))
  (testing "Too many patterns multiple fields"
    (assert-too-many-wildcards
     (search/find-refs :granule {:readable-granule-name ["*1"]
                                 "options[readable-granule-name][pattern]" "true"
                                 :producer-granule-id ["*3" "*4" "*5" "*6"]
                                 "options[producer-granule-id][pattern]" "true"})))
  (testing "Nonleading wildcards are ok"
    (assert-not-too-many-wildcards
     (search/find-refs :granule {:producer-granule-id ["f*1" "f*2" "f*3" "f*4" "f*5" "f*6"]
                                 "options[producer-granule-id][pattern]" "true"})))
  (testing "Non-patterns are ok"
    (assert-not-too-many-wildcards
     (search/find-refs :granule {:producer-granule-id ["*1" "*2" "*3" "*4" "*5" "*6"]})))
  (testing "Less than max wildcards are ok"
    (assert-not-too-many-wildcards
     (search/find-refs :granule {:producer-granule-id ["*1" "*2" "*3" "*4" "*5"]
                                 "options[producer-granule-id][pattern]" "true"}))
    (assert-not-too-many-wildcards
     (search/find-refs :granule {:readable-granule-name ["*1" "*2"]
                                 "options[readable-granule-name][pattern]" "true"}))))

(deftest search-by-producer-granule-id
  (let [coll1 (d/ingest "PROV1" (dc/collection {}))
        coll2 (d/ingest "PROV1" (dc/collection {}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"
                                                       :producer-gran-id "SpecialOne"}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule2"
                                                       :producer-gran-id "SpecialTwo"}))
        gran3 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "SpecialOne"
                                                       :producer-gran-id "NotSoSpecial"}))
        gran4 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "Granule44"
                                                       :producer-gran-id "SuperSpecial"}))
        gran5 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "SuperSpecial"
                                                       :producer-gran-id "Granule2"}))]
    (index/wait-until-indexed)
    (testing "search by non-existent readable granule name."
      (let [references (search/find-refs :granule {:readable-granule-name "NON_EXISTENT"})]
        (is (d/refs-match? [] references))))
    (testing "search by existing readable granule name matching granule-ur."
      (let [references (search/find-refs :granule {:readable-granule-name "Granule1"})]
        (is (d/refs-match? [gran1] references))))
    (testing "search by existing readable granule name matching producer-granule-id"
      (let [references (search/find-refs :granule {:readable-granule-name "SpecialTwo"})]
        (is (d/refs-match? [gran2] references))))
    (testing "search by multiple readable granule names."
      (let [references (search/find-refs :granule {"readable-granule-name[]" ["Granule1", "Granule2"]})]
        (is (d/refs-match? [gran1 gran2 gran5] references))))
    (testing "search by readable granule name using wildcard *."
      (let [references (search/find-refs :granule
                                         {:readable-granule-name "Gran*"
                                          "options[readable-granule-name][pattern]" "true"})]
        (is (d/refs-match? [gran1 gran2 gran4 gran5] references))))
    (testing "search by readable granule name using wildcard ?."
      (let [references (search/find-refs :granule
                                         {:readable-granule-name "Granule?"
                                          "options[readable-granule-name][pattern]" "true"})]
        (is (d/refs-match? [gran1 gran2 gran5] references))))
    (testing "search by readable granule name default is ignore case true."
      (let [references (search/find-refs :granule {:readable-granule-name "granule1"})]
        (is (d/refs-match? [gran1] references))))
    (testing "search by readable granule name ignore case false."
      (let [references (search/find-refs :granule
                                         {:readable-granule-name "granule1"
                                          "options[readable-granule-name][ignore-case]" "false"})]
        (is (d/refs-match? [] references))))
    (testing "search by readable granule name ignore case true."
      (let [references (search/find-refs :granule
                                         {:readable-granule-name "granule1"
                                          "options[readable-granule-name][ignore-case]" "true"})]
        (is (d/refs-match? [gran1] references))))
    (testing "search by readable granule name default options."
      (let [references (search/find-refs :granule
                                         {:readable-granule-name ["Granule2", "SuperSpecial"]})]
        (is (d/refs-match? [gran2 gran4 gran5] references))))
    (testing "search by readable granule name options :and."
      (let [references (search/find-refs :granule
                                         {:readable-granule-name ["Granule2", "SuperSpecial"]
                                          "options[readable-granule-name][and]" "true"})]
        (is (d/refs-match? [gran5] references))))))

