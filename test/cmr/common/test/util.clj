(ns cmr.common.test.util
  (:require [clojure.test :refer :all]
            [cmr.common.util :as util]))

(deftest test-sequence->fn
  (testing "vector of values"
    (let [f (util/sequence->fn [1 2 3])]
      (is (= 1 (f)))
      (is (= 2 (f)))
      (is (= 3 (f)))
      (is (= nil (f)))
      (is (= nil (f)))))
  (testing "list of values"
    (let [f (util/sequence->fn '(1 2 3))]
      (is (= 1 (f)))
      (is (= 2 (f)))
      (is (= 3 (f)))
      (is (= nil (f)))))
  (testing "empty vector"
    (let [f (util/sequence->fn [])]
      (is (= nil (f)))
      (is (= nil (f)))))
  (testing "empty list"
    (let [f (util/sequence->fn '())]
      (is (= nil (f)))
      (is (= nil (f)))))
  (testing "infinite sequence of values"
    (let [f (util/sequence->fn (iterate inc 1))]
      (is (= 1 (f)))
      (is (= 2 (f)))
      (is (= 3 (f)))
      (is (= 4 (f)))
      (is (= 5 (f))))))

(deftest this-should-fail
  (is (= 5 4)))