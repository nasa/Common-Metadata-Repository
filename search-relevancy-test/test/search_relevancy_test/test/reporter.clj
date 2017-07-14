(ns search-relevancy-test.test.reporter
  "Tests for the reporter namespace"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [search-relevancy-test.reporter :as reporter]))

(deftest discounted-cumulative-gain
  (are3 [positions expected-dcg]
    (is (= (format "%.3f" expected-dcg)
           (format "%.3f" (double (#'reporter/result-discounted-cumulative-gain positions)))))

    "Ideal ranking"
    [1 2 3 4] 7.323

    "Worst ranking"
    [4 3 2 1] 5.485

    "0's in list"
    [1 0 2 0] 5.5

    "Empty list"
    [] 0.0))

(deftest discounted-cumulative-gain-percent
  (are3 [positions num-results expected-dcg-percent]
    (is (= (format "%.3f" expected-dcg-percent)
           (format "%.3f"
                   (double (#'reporter/test-discounted-cumulative-gain positions num-results)))))

    "Ideal ranking"
    [1 2 3 4] 4 1.0

    "Worst ranking"
    [4 3 2 1] 4 0.749

    "0's in list"
    [1 0 2 0] 4 0.751

    "Not all results returned"
    [1 2 3] 4 0.65

    "Empty list"
    [] 0 0.0))

(deftest reciprocal-rank
  (are3 [positions reciprocal-rank]
    (is (= (format "%.3f" reciprocal-rank)
           (format "%.3f" (double (#'reporter/reciprocal-rank positions)))))

    "Ideal ranking"
    [1 2 3 4] 1.0

    "2nd place"
    [2 1 3 4] 0.5

    "4th place"
    [4 3 2 1] 0.25

    "Empty list"
    [] 0.0

    "1 not found"
    [2 3 4] 0.0))
