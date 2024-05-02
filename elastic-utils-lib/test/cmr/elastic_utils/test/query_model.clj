(ns cmr.elastic-utils.test.query-model
  (:require [clojure.test :refer :all]
            [cmr.elastic-utils.test.helpers :refer :all]
            [cmr.common.services.search.query-model :as q]
            [cmr.elastic-utils.search.es-group-query-conditions :as gc]))

(deftest conds-test
  (testing "AND simplification"
    (testing "no conditions"
      (is (thrown? Exception (gc/and-conds []))))

    (testing "one condition"
      (is (= (q/string-condition :a 1)
             (gc/and-conds [(q/string-condition :a 1)]))))

    (testing "multiple conditions"
      (let [conds [(q/string-condition :a 1)
                   (q/string-condition :b 2)]]
        (is (= (q/->ConditionGroup :and conds)
               (gc/and-conds conds)))))

    (testing "flattening nested"
      (is (= (and-conds (other 1) (other 2) (other 3))
             (and-conds (other 1) (and-conds (other 2) (other 3))))))

    (testing "Filter out match alls"
      (is (= (and-conds (other 1) (other 2))
             (and-conds (other 1) (other 2) q/match-all)))

      (testing "except by themselves"
        (is (= q/match-all (and-conds q/match-all)))))

    (testing "MatchNone overrules other conditions"
      (is (= q/match-none
             (and-conds (other 1) (other 2) q/match-none)))))

  (testing "OR simplification"
    (testing "no conditions"
      (is (thrown? Exception (gc/or-conds []))))

    (testing "one condition"
      (is (= (q/string-condition :a 1)
             (gc/or-conds [(q/string-condition :a 1)]))))

    (testing "multiple conditions"
      (let [conds [(q/string-condition :a 1)
                   (q/string-condition :b 2)]]
        (is (= (q/->ConditionGroup :or conds)
               (gc/or-conds conds)))))

    (testing "flattening nested"
      (is (= (or-conds (other 1) (other 2) (other 3))
             (or-conds (other 1) (or-conds (other 2) (other 3))))))

    (testing "Filter out match nones"
      (is (= (or-conds (other 1) (other 2))
             (or-conds (other 1) (other 2) q/match-none)))
      (testing "except by themselves"
        (is (= q/match-none (or-conds q/match-none)))))

    (testing "MatchAll overrules other conditions"
      (is (= q/match-all
             (or-conds (other 1) (other 2) q/match-all))))))


