(ns cmr.search.test.services.query-walkers.condition-extractor
  (:require [clojure.test :refer :all]
            [cmr.search.test.models.helpers :refer :all]
            [cmr.search.services.query-walkers.condition-extractor :as c]
            [cmr.common-app.services.search.query-model :as q]))


(defn string-cond
  [field n]
  (q/string-condition field (str "value" n)))

(defn field-is-matcher
  [field]
  (fn [condition-path condition]
    (= (:field condition) field)))

(defn within-ands
  [condition-path condition]
  (->> condition-path
       (filter (fn [c]
                 (= cmr.common_app.services.search.query_model.ConditionGroup (type c))))
       (filter (fn [c]
                 (= :or (:operation c))))
       empty?))

(defn compose-matchers
  [& matchers]
  (fn [condition-path condition]
    (reduce (fn [matches? matcher]
              (and matches? (matcher condition-path condition)))
            true
            matchers)))

(deftest extract-conditions
  (let [big-query (q/query
                    {:condition
                     (and-conds
                       (or-conds (string-cond :provider 1)
                                 (string-cond :provider 2))
                       (or-conds (string-cond :id 1)
                                 (string-cond :id 2)
                                 (string-cond :id 3))
                       (string-cond :id 4)
                       (coll-query-cond (string-cond :id 5))
                       (negated (string-cond :id 6)))})]
    (testing "extract all id conditions"
      (is (= (map (partial string-cond :id) (range 1 7))
             (c/extract-conditions big-query (field-is-matcher :id)))))

    (testing "extract id conditions that are AND'd"
      (is (= (map (partial string-cond :id) (range 4 7))
             (c/extract-conditions big-query
                                  (compose-matchers (field-is-matcher :id)
                                                    within-ands)))))))