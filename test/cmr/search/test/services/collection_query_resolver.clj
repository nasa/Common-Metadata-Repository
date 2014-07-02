(ns cmr.search.test.services.collection-query-resolver
  (:require [clojure.test :refer :all]
            [cmr.search.services.collection-query-resolver :as c]
            [cmr.search.models.query :as q]))
(defn query
  [condition]
  (q/query {:concept-type :granule
            :condition condition}))

(defn other
  "Creates a unique condition"
  [n]
  (q/string-conditions :foo (str "other" n)))

(defn and-conds
  [& conds]
  (q/and-conds conds))

(defn or-conds
  [& conds]
  (q/or-conds conds))

(defn coll-query-cond
  [condition]
  (q/->CollectionQueryCondition condition))

(deftest merge-collection-queries
  (testing "no collection query condition"
    (let [input (query (other 1))]
      (is (= input (c/merge-collection-queries input)))))

  (testing "a single collection query condition"
    (let [input (query (and-conds (coll-query-cond (other 2))
                                  (other 1)))]
      (is (= input (c/merge-collection-queries input)))))

  (testing "multiple query condition in AND"
    (is (= (query (and-conds (coll-query-cond (and-conds (other 2)
                                                         (other 3)))
                             (other 1)))
           (c/merge-collection-queries
             (query (and-conds (other 1)
                               (coll-query-cond (other 2))
                               (coll-query-cond (other 3))))))))
  (testing "multiple query condition in OR"
    (is (= (query (or-conds (coll-query-cond (or-conds (other 2)
                                                       (other 3)))
                            (other 1)))
           (c/merge-collection-queries
             (query (or-conds (other 1)
                              (coll-query-cond (other 2))
                              (coll-query-cond (other 3))))))))

  (testing "multiple nested query conditions that can't collapse"
    (is (= (query (and-conds (coll-query-cond (and-conds (other 2)
                                                         (other 3)))
                             (other 1)
                             (or-conds (coll-query-cond (or-conds (other 4)
                                                                  (other 5)))
                                       (other 6))))
           (c/merge-collection-queries
             (query (and-conds (other 1)
                               (coll-query-cond (other 2))
                               (coll-query-cond (other 3))
                               (or-conds (other 6)
                                         (coll-query-cond (other 4))
                                         (coll-query-cond (other 5)))))))))

  (testing "multiple nested query conditions that can collapse"
    (is (= (query (and-conds (coll-query-cond (and-conds (other 2)
                                                         (other 3)
                                                         (or-conds (other 4)
                                                                   (other 5))))
                             (other 1)))
           (c/merge-collection-queries
             (query (and-conds (other 1)
                               (coll-query-cond (other 2))
                               (coll-query-cond (other 3))
                               (or-conds (coll-query-cond (other 4))
                                         (coll-query-cond (other 5))))))))))

