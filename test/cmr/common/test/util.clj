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

(deftest build-validator-test
  (let [error-type :not-found
        errors ["error 1" "error 2"]
        validation (fn [a b]
                     (cond
                       (> a b) errors
                       (< a b) []
                       :else nil))
        validator (util/build-validator error-type validation)]

    (is (nil? (validator 1 1)) "No error should be thrown for valid returning nil")
    (is (nil? (validator 0 1)) "No error should be thrown for valid returning empty vector")

    (try
      (validator 1 0)
      (is false "An exception should have been thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (= {:type error-type
                :errors errors}
               (ex-data e)))))))