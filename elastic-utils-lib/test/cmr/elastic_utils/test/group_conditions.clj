(ns cmr.elastic-utils.test.group_conditions
  "Tests for the cmr.elastic-utils.search.es-group-query-conditions namespace"
  (:require 
   [clojure.test :refer [deftest is testing]]
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.search.es-group-query-conditions :as gc]))

(def multi-match-none
  [qm/match-none qm/match-none qm/match-none])

(def multi-match-all
  [qm/match-all qm/match-all qm/match-all])

(def multi-match-all-none
  [qm/match-all qm/match-all qm/match-none qm/match-none])

(deftest test-group-conds-short-filter
  (testing "multi-match-all and"
    (let [processed-conds (gc/and-conds multi-match-all)]
      (is (= qm/match-all processed-conds))))
  (testing "multi-match-all or"
    (let [processed-conds (gc/or-conds multi-match-all)]
      (is (= qm/match-all processed-conds))))
  (testing "multi-match-none and"
    (let [processed-conds (gc/and-conds multi-match-none)]
      (is (= qm/match-none processed-conds))))
  (testing "multi-match-none or"
    (let [processed-conds (gc/or-conds multi-match-none)]
      (is (= qm/match-none processed-conds))))
  (testing "multi-match-all-none and"
    (let [processed-conds (gc/and-conds multi-match-all-none)]
      (is (= qm/match-none processed-conds))))
  (testing "multi-match-all-none or"
    (let [processed-conds (gc/or-conds multi-match-all-none)]
      (is (= qm/match-all processed-conds)))))
