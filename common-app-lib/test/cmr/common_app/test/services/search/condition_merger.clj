(ns cmr.common-app.test.services.search.condition-merger
  (:require [clojure.test :refer :all]
            [cmr.common-app.services.search.condition-merger :as c]
            [cmr.common-app.services.search.query-model :as q]
            [cmr.common-app.test.services.search.helpers :refer :all]))


(defn does-not-merge
  [op conditions]
  (is (= conditions
         (c/merge-conditions op conditions))))

(defn does-merge
  [op expected conditions]
  (is (= expected
         (c/merge-conditions op conditions))))

(def does-not-merge-or
  (partial does-not-merge :or))

(def does-merge-or
  (partial does-merge :or))

(def does-not-merge-and
  (partial does-not-merge :and))

(def does-merge-and
  (partial does-merge :and))

(defn processor-fn1 [])
(defn processor-fn2 [])
(defn processor-fn3 [])

(defn related-item-cond
  "Helper for creating related item conditions"
  ([concept-type fields processor-fn]
   (related-item-cond concept-type fields processor-fn (generic :foo)))
  ([concept-type fields processor-fn condition]
   (q/map->RelatedItemQueryCondition
     {:concept-type concept-type
      :condition condition
      :result-fields fields
      :results-to-condition-fn processor-fn})))

(deftest merge-conditions-or-test
  (testing "Merging related item conditions"
    (testing "Does not merge if all three fields do not match"
      (testing "Concept types doesn't match"
        (does-not-merge-or [(related-item-cond :collection [:a :b] processor-fn1)
                            (related-item-cond :granule [:a :b] processor-fn1)]))

      (testing "Field types don't match"
        (does-not-merge-or [(related-item-cond :collection [:a :b] processor-fn1)
                            (related-item-cond :collection [:a :b :c] processor-fn1)]))
      (testing "Processor functions doesn't match"
        (does-not-merge-or [(related-item-cond :collection [:a :b] processor-fn1)
                            (related-item-cond :collection [:a :b] processor-fn2)])))

    (testing "Merges if all three fields match"
      (let [cond1 (generic :a)
            cond2 (generic :b)]
        (does-merge-and [(related-item-cond :collection [:a :b] processor-fn1 (and-conds cond1 cond2))]
                        [(related-item-cond :collection [:a :b] processor-fn1 cond1)
                         (related-item-cond :collection [:a :b] processor-fn1 cond2)])
        (does-merge-or [(related-item-cond :collection [:a :b] processor-fn1 (or-conds cond1 cond2))]
                       [(related-item-cond :collection [:a :b] processor-fn1 cond1)
                        (related-item-cond :collection [:a :b] processor-fn1 cond2)])))

    (testing "Some merge and some don't"
      (let [cond1 (generic :a)
            cond2 (generic :b)
            ric1 (related-item-cond :collection [:a] processor-fn1 cond1)
            ric2 (related-item-cond :collection [:a] processor-fn1 cond2)
            merged-ric1-2 (related-item-cond :collection [:a] processor-fn1 (or-conds cond1 cond2))
            ric3 (related-item-cond :collection [:a] processor-fn2)
            other (generic :bar)
            sc1 (q/string-condition :entry-title 1)
            sc2 (q/string-condition :entry-title 2)
            merged-sc (q/string-conditions :entry-title [1 2])]
        (does-merge-or [merged-ric1-2 ric3 other merged-sc]
                       [ric1 ric2 ric3 other sc1 sc2]))))

  (testing "Merging String Conditions"
    (testing "does not merge two conditions with different fields"
      (does-not-merge-or [(q/string-condition :entry-title 5)
                          (q/string-condition :concept-id 5)]))

    (testing "merge two conditions of same field"
      (does-merge-or [(q/string-condition :entry-title 4)]
                     [(q/string-condition :entry-title 4)
                      (q/string-condition :entry-title 4)])

      (does-merge-or [(q/string-conditions :entry-title [4 5])]
                     [(q/string-condition :entry-title 4)
                      (q/string-condition :entry-title 5)]))

    (testing "merges string and strings condition"
      (does-merge-or [(q/string-conditions :entry-title [4 6 5 7 9])]
                     [(q/string-conditions :entry-title [4 6])
                      (q/string-condition :entry-title 5)
                      (q/string-conditions :entry-title [7 9])]))

    (testing "conditions that are not merged"
      (testing "pattern"
        (does-not-merge-or [(q/string-condition :entry-title 4)
                            (q/string-condition :entry-title 6 false true)]))
      (testing "different case sensitive values"
        (does-not-merge-or [(q/string-condition :entry-title 4 true false)
                            (q/string-condition :entry-title 6 false false)]))
      (testing "non-string conditions"
        (does-not-merge-or [(q/string-condition :entry-title 4)
                            (q/numeric-value-condition :entry-title 5)])))

    (testing "combination case with multiple merges and non-merges"
      (does-merge-or
        [(q/string-conditions :entry-title [1 9] false)
         (q/string-condition :concept-id 1 false false)
         (q/string-conditions :entry-title [2 3 4] true)
         (q/string-condition :entry-title 5 false true)
         (q/string-condition :entry-title 6 false true)
         (q/numeric-value-condition :entry-title 7)
         (q/numeric-value-condition :entry-title 8)]
        [(q/string-condition :entry-title 1 false false)
         (q/string-condition :entry-title 9 false false)
         (q/string-condition :concept-id 1 false false)
         ;; matching case senstive merged together
         (q/string-condition :entry-title 2 true false)
         (q/string-conditions :entry-title [3 4] true)
         ;; pattern not merged
         (q/string-condition :entry-title 5 false true)
         (q/string-condition :entry-title 6 false true)
         (q/numeric-value-condition :entry-title 7)
         (q/numeric-value-condition :entry-title 8)
         (q/string-condition :entry-title 9 false false)]))))

(deftest merge-conditions-and-test
  (testing "does not merge two conditions with different fields"
    (does-not-merge-and [(q/string-condition :entry-title 5)
                         (q/string-condition :concept-id 5)]))

  (testing "merge two conditions of same field"
    (does-merge-and [(q/string-condition :entry-title 4)]
                    [(q/string-condition :entry-title 4)
                     (q/string-condition :entry-title 4)]))

  (testing "different values is intersection of all values"
    (does-merge-and [q/match-none]
                    [(q/string-condition :entry-title 4)
                     (q/string-condition :entry-title 5)])

    (does-merge-and [(q/string-condition :entry-title 5)]
                    [(q/string-conditions :entry-title [4 6 7 5])
                     (q/string-condition :entry-title 5)
                     (q/string-conditions :entry-title [7 5 9])]))

  (testing "conditions that are not merged"
    (testing "pattern"
      (does-not-merge-and [(q/string-condition :entry-title 4)
                           (q/string-condition :entry-title 6 false true)]))
    (testing "different case sensitive values"
      (does-not-merge-and [(q/string-condition :entry-title 4 true false)
                           (q/string-condition :entry-title 6 false false)]))
    (testing "non-string conditions"
      (does-not-merge-and [(q/string-condition :entry-title 4)
                           (q/numeric-value-condition :entry-title 5)])))

  (testing "combination case with multiple merges and non-merges"
    (does-merge-and
      [;; 7 is intersection of [1 7 9] and [7]
       (q/string-condition :entry-title 7 false false)
       ;; b intersection matched none
       q/match-none
       ;; [2 3 4] is intersection of [2 3 4 5] and [1 2 3 4]
       (q/string-conditions :entry-title [4 3 2] true)

       ;; Conditions that were the same
       (q/string-condition :entry-title 5 false true)
       (q/string-condition :entry-title 6 false true)
       (q/numeric-value-condition :entry-title 7)
       (q/numeric-value-condition :entry-title 8)]

      [(q/string-conditions :entry-title [1 7 9] false)
       (q/string-condition :entry-title 7 false false)
       (q/string-condition :concept-id 1 false false)
       (q/string-condition :concept-id 2 false false)
       ;; case sensitive
       (q/string-conditions :entry-title [2 3 4 5] true)
       (q/string-conditions :entry-title [1 2 3 4] true)
       ;; pattern ignored
       (q/string-condition :entry-title 5 false true)
       (q/string-condition :entry-title 6 false true)
       ;; non string conditiosn ignored
       (q/numeric-value-condition :entry-title 7)
       (q/numeric-value-condition :entry-title 8)])))

