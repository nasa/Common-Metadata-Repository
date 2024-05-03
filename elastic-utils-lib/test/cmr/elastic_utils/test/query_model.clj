(ns cmr.elastic-utils.test.query-model
  "Tests for the query-model."
  (:require [clojure.test :refer [deftest is testing]]
            [cmr.elastic-utils.test.helpers :refer [and-conds or-conds other]]
            [cmr.common.services.search.query-model :as q-mod]
            [cmr.elastic-utils.search.es-group-query-conditions :as g-con]))

(deftest conds-test
  (testing "AND simplification"
    (testing "no conditions"
      (is (thrown? Exception (g-con/and-conds []))))

    (testing "one condition"
      (is (= (q-mod/string-condition :a 1)
             (g-con/and-conds [(q-mod/string-condition :a 1)]))))

    (testing "multiple conditions"
      (let [conds [(q-mod/string-condition :a 1)
                   (q-mod/string-condition :b 2)]]
        (is (= (q-mod/->ConditionGroup :and conds)
               (g-con/and-conds conds)))))

    (testing "flattening nested"
      (is (= (and-conds (other 1) (other 2) (other 3))
             (and-conds (other 1) (and-conds (other 2) (other 3))))))

    (testing "Filter out match alls"
      (is (= (and-conds (other 1) (other 2))
             (and-conds (other 1) (other 2) q-mod/match-all)))

      (testing "except by themselves"
        (is (= q-mod/match-all (and-conds q-mod/match-all)))))

    (testing "MatchNone overrules other conditions"
      (is (= q-mod/match-none
             (and-conds (other 1) (other 2) q-mod/match-none)))))

  (testing "OR simplification"
    (testing "no conditions"
      (is (thrown? Exception (g-con/or-conds []))))

    (testing "one condition"
      (is (= (q-mod/string-condition :a 1)
             (g-con/or-conds [(q-mod/string-condition :a 1)]))))

    (testing "multiple conditions"
      (let [conds [(q-mod/string-condition :a 1)
                   (q-mod/string-condition :b 2)]]
        (is (= (q-mod/->ConditionGroup :or conds)
               (g-con/or-conds conds)))))

    (testing "flattening nested"
      (is (= (or-conds (other 1) (other 2) (other 3))
             (or-conds (other 1) (or-conds (other 2) (other 3))))))

    (testing "Filter out match nones"
      (is (= (or-conds (other 1) (other 2))
             (or-conds (other 1) (other 2) q-mod/match-none)))
      (testing "except by themselves"
        (is (= q-mod/match-none (or-conds q-mod/match-none)))))

    (testing "MatchAll overrules other conditions"
      (is (= q-mod/match-all
             (or-conds (other 1) (other 2) q-mod/match-all))))))
