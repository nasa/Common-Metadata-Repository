(ns cmr.search.test.services.query-walkers.collection-query-resolver
  (:require [clojure.test :refer :all]
            [cmr.search.services.query-walkers.collection-query-resolver :as c]
            [cmr.search.test.models.helpers :refer :all]
            [clojure.string :as str]
            [cmr.search.models.query :as q]
            [clojure.set :as set]))
(defn query
  [condition]
  (q/query {:concept-type :granule
            :condition condition}))

(defrecord MockCollectionQueryCondition
  [
   condition
   collection-ids-to-find
   ]

  c/ResolveCollectionQuery

  (resolve-collection-query
    [{:keys [condition collection-ids-to-find]} context]
    (let [{:keys [collection-ids]} context
          collection-ids (if (seq collection-ids)
                           (set/intersection (set collection-ids) collection-ids-to-find)
                           collection-ids-to-find)]
      (if (empty? collection-ids)
        [#{} q/match-none]
        [collection-ids (generic (str/join "," collection-ids))])))

  (is-collection-query-cond? [_] true))


(defn mock-coll-query-cond
  [named collection-ids-to-find]
  (->MockCollectionQueryCondition (generic named) (set collection-ids-to-find)))


(deftest resolve-collection-query
  (testing "and group"
    (testing "existing collection ids in the context should be respected"
      (let [test-and (and-conds (mock-coll-query-cond "coll-id" [:a :b :c])
                                (mock-coll-query-cond "coll-id" [:b :c :a]))]
        (is (= [#{:b} (and-conds (generic ":b")
                                 (generic ":b"))]
               (c/resolve-collection-query test-and {:collection-ids #{:b :d}})))))

    (testing "collection concept id condition with collection query condition"
      (let [test-and (and-conds (generic "normal")
                                (q/string-condition :collection-concept-id :a)
                                (mock-coll-query-cond "coll-foo" [:a :b :c]))]
        (is (= [#{:a} (and-conds (q/string-condition :collection-concept-id :a)
                                 (generic ":a")
                                 (generic "normal"))]
               (c/resolve-collection-query test-and {})))))

    (testing "collection concept id strings condition with collection query condition"
      (let [test-and (and-conds (generic "normal")
                                (q/string-conditions :collection-concept-id [:a :b :d])
                                (mock-coll-query-cond "coll-foo" [:a :b :c]))]
        (is (= [#{:a :b} (and-conds (q/string-conditions :collection-concept-id [:a :b :d])
                                 (generic ":b,:a")
                                 (generic "normal"))]
               (c/resolve-collection-query test-and {})))))

    (testing "multiple coll queries with different found collections"
      (let [test-and (and-conds (mock-coll-query-cond "coll-id" [:a])
                                (mock-coll-query-cond "coll-id" [:b]))]
        (is (= [#{} (and-conds (generic ":a")
                               q/match-none)]
               (c/resolve-collection-query test-and {})))))
    (testing "multiple coll queries with matching collections"
      (let [test-and (and-conds (mock-coll-query-cond "coll-id" [:a :b])
                                (mock-coll-query-cond "coll-id" [:b :c]))]
        (is (= [#{:b} (and-conds (generic ":b,:a")
                                 (generic ":b"))]
               (c/resolve-collection-query test-and {})))))
    (testing "collection queries and normal conditions"
      (let [test-and (and-conds (generic "normal")
                                (mock-coll-query-cond "coll-id" [:a :b]))]
        (is (= [#{:a :b} (and-conds (generic ":b,:a") (generic "normal"))]
               (c/resolve-collection-query test-and {}))))))
  (testing "or group"
    (testing "normal or"
      (let [test-or (or-conds (mock-coll-query-cond "coll-id" [:a :b :c])
                              (mock-coll-query-cond "coll-id" [:b :c :a]))]
        (is (= [#{:a :b :c} (or-conds (generic ":c,:b,:a")
                                      (generic ":c,:b,:a"))]
               (c/resolve-collection-query test-or {})))))
    (testing "existing collection ids in the context should be respected"
      (let [test-or (or-conds (mock-coll-query-cond "coll-id" [:a :b :c])
                              (mock-coll-query-cond "coll-id" [:b :c :a]))]
        (is (= [#{:b} (or-conds (generic ":b") (generic ":b"))]
               (c/resolve-collection-query test-or {:collection-ids #{:b :d}})))))
    (testing "multiple coll queries with different found collections"
      (let [test-or (or-conds (mock-coll-query-cond "coll-id" [:a])
                              (mock-coll-query-cond "coll-id" [:b]))]
        (is (= [#{:a :b} (or-conds (generic ":a") (generic ":b"))]
               (c/resolve-collection-query test-or {})))))
    (testing "multiple coll queries with matching collections"
      (let [test-or (or-conds (mock-coll-query-cond "coll-id" [:a :b])
                              (mock-coll-query-cond "coll-id" [:b :c]))]
        (is (= [#{:a :b :c} (or-conds (generic ":b,:a") (generic ":c,:b"))]
               (c/resolve-collection-query test-or {})))))
    (testing "collection queries and normal conditions"
      (let [test-or (or-conds (generic "normal")
                              (mock-coll-query-cond "coll-id" [:a :b]))]
        (is (= [#{:a :b} (or-conds (generic "normal") (generic ":b,:a"))]
               (c/resolve-collection-query test-or {}))))
      (let [test-or (or-conds (mock-coll-query-cond "coll-id" [:a :b])
                              (generic "normal"))]
        (is (= [#{:a :b} (or-conds (generic ":b,:a") (generic "normal"))]
               (c/resolve-collection-query test-or {}))))))
  (testing "query with collection identifying collection query"
    (let [test-query (query (and-conds (or-conds (generic "gran-inst")
                                                 (mock-coll-query-cond "coll-inst" [:d :f :g]))
                                       (or-conds (generic "gran-plat")
                                                 (mock-coll-query-cond "coll-plat" [:a :b :c :d :e]))
                                       (mock-coll-query-cond "coll-id" [:d])))

          expected (query (and-conds (generic ":d")
                                     (or-conds (generic "gran-inst")
                                               (generic ":d"))
                                     (or-conds (generic "gran-plat")
                                               (generic ":d"))))]
      (is (= [:all expected]
             (c/resolve-collection-query test-query {})))))
  (testing "query with collection concept id condition"
    (let [test-query (query (and-conds (or-conds (generic "gran-inst")
                                                 (mock-coll-query-cond "coll-inst" [:d :f :g]))
                                       (or-conds (generic "gran-plat")
                                                 (mock-coll-query-cond "coll-plat" [:a :b :c :d :e]))
                                       (q/string-condition :collection-concept-id :d)))

          expected (query (and-conds (q/string-condition :collection-concept-id :d)
                                     (or-conds (generic "gran-inst")
                                               (generic ":d"))
                                     (or-conds (generic "gran-plat")
                                               (generic ":d"))))]
      (is (= [:all expected]
             (c/resolve-collection-query test-query {}))))))

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

